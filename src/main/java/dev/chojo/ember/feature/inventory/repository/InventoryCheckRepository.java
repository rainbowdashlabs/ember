/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.CheckDetail;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.InventoryCheck;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckItem;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckLock;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for managing inventory checks, check items, member locks, and check navigation.
 */
@Singleton
public class InventoryCheckRepository {

    // -- Checks --

    /**
     * Creates a new inventory check record for a member.
     *
     * @param stationId the station ID
     * @param memberId  the member being checked
     * @param checkedBy the member performing the check
     * @return the created check
     */
    public InventoryCheck createCheck(int stationId, int memberId, int checkedBy) {
        return query("""
                INSERT INTO inventory_check(station_id, member_id, checked_by, scope)
                VALUES (:station_id, :member_id, :checked_by, 'MEMBER')
                RETURNING id, station_id, member_id, checked_by, checked_at, scope, container_id, deep;""")
                .single(call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("checked_by", checkedBy))
                .map(InventoryCheck.map())
                .first()
                .orElseThrow();
    }

    /**
     * Creates a new container-scoped check.
     *
     * @param stationId   the station ID
     * @param containerId the target container
     * @param checkedBy   the member performing the check
     * @param deep        whether descendant containers are included in the walk
     * @return the created check
     */
    public InventoryCheck createContainerCheck(int stationId, int containerId, int checkedBy, boolean deep) {
        return query("""
                INSERT INTO inventory_check(station_id, container_id, checked_by, scope, deep)
                VALUES (:station_id, :container_id, :checked_by, 'CONTAINER', :deep)
                RETURNING id, station_id, member_id, checked_by, checked_at, scope, container_id, deep;""")
                .single(call().bind("station_id", stationId)
                        .bind("container_id", containerId)
                        .bind("checked_by", checkedBy)
                        .bind("deep", deep))
                .map(InventoryCheck.map())
                .first()
                .orElseThrow();
    }

    /**
     * Returns the most recent container-scope check for the given container, if any.
     */
    public Optional<InventoryCheck> latestCheckForContainer(int containerId) {
        return query("""
                SELECT id, station_id, member_id, checked_by, checked_at, scope, container_id, deep
                FROM inventory_check
                WHERE container_id = :container_id AND scope = 'CONTAINER'
                ORDER BY checked_at DESC LIMIT 1;""")
                .single(call().bind("container_id", containerId))
                .map(InventoryCheck.map())
                .first();
    }

    /**
     * Finds the most recent inventory check for a member.
     *
     * @param memberId the member ID
     * @return the latest check, or empty if no checks exist
     */
    public Optional<InventoryCheck> latestCheckForMember(int memberId) {
        return query(
                        "SELECT id, station_id, member_id, checked_by, checked_at, scope, container_id, deep FROM inventory_check WHERE member_id = :member_id ORDER BY checked_at DESC LIMIT 1;")
                .single(call().bind("member_id", memberId))
                .map(InventoryCheck.map())
                .first();
    }

    /**
     * Retrieves a check overview for all members of a station, including last check info and lock status.
     * Results are ordered by last checked date ascending, with unchecked members first.
     *
     * @param stationId the station ID
     * @return list of member check summaries
     */
    public List<MemberCheckSummary> checkOverview(int stationId) {
        return query("""
                SELECT
                    sm.id                AS member_id,
                    a.first_name,
                    a.last_name,
                    sm.user_type,
                    lc.checked_at        AS last_checked_at,
                    lc.checked_by,
                    ca.first_name        AS checker_first_name,
                    ca.last_name         AS checker_last_name,
                    ( l.id IS NOT NULL ) AS locked,
                    l.locked_by,
                    la.first_name        AS locker_first_name,
                    la.last_name         AS locker_last_name
                FROM
                    station_member sm
                        JOIN account a
                        ON a.id = sm.account_id
                        LEFT JOIN LATERAL (
                        SELECT
                            checked_at,
                            checked_by
                        FROM
                            inventory_check
                        WHERE member_id = sm.id
                        ORDER BY checked_at DESC
                        LIMIT 1
                        ) lc
                        ON TRUE
                        LEFT JOIN station_member csm
                        ON csm.id = lc.checked_by
                        LEFT JOIN account ca
                        ON ca.id = csm.account_id
                        LEFT JOIN inventory_check_lock l
                        ON l.member_id = sm.id
                        LEFT JOIN station_member lsm
                        ON lsm.id = l.locked_by
                        LEFT JOIN account la
                        ON la.id = lsm.account_id
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                ORDER BY lc.checked_at ASC NULLS FIRST;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new MemberCheckSummary(
                        row.getInt("member_id"),
                        row.getString("first_name"),
                        row.getString("last_name"),
                        row.get("last_checked_at", INSTANT_TIMESTAMP),
                        row.getString("checker_first_name"),
                        row.getString("checker_last_name"),
                        row.getBoolean("locked"),
                        row.getObject("locked_by", Integer.class),
                        row.getString("locker_first_name"),
                        row.getString("locker_last_name"),
                        row.getEnum("user_type", StationUserType.class)))
                .all();
    }

    /**
     * Retrieves the latest check detail for a member, including all check items and the checker's name.
     *
     * @param memberId the member ID
     * @return the check detail, or empty if no checks exist
     */
    public Optional<CheckDetail> latestCheckDetail(int memberId) {
        var check = latestCheckForMember(memberId);
        if (check.isEmpty()) return Optional.empty();
        var items = findCheckItems(check.get().id());
        // Get checker name
        var names = query("""
                SELECT a.first_name, a.last_name FROM station_member sm
                    JOIN account a ON a.id = sm.account_id
                WHERE sm.id = :id;""")
                .single(call().bind("id", check.get().checkedBy()))
                .map(row -> new String[] {row.getString("first_name"), row.getString("last_name")})
                .first()
                .orElse(new String[] {"", ""});
        return Optional.of(new CheckDetail(check.get(), names[0], names[1], items));
    }

    // -- Check Items --

    /**
     * Creates a check item result within an inventory check.
     *
     * @param checkId     the parent check ID
     * @param itemId      the item being checked, or {@code null}
     * @param inventoryId the inventory, or {@code null}
     * @param result      the check result
     * @param note        an optional note
     * @return the created check item
     */
    public InventoryCheckItem createCheckItem(
            int checkId, Integer itemId, Integer inventoryId, CheckResult result, String note) {
        return query("""
                INSERT INTO inventory_check_item(check_id, item_id, inventory_id, result, note)
                VALUES (:check_id, :item_id, :inventory_id, :result, :note)
                RETURNING id, check_id, item_id, inventory_id, result, note;""")
                .single(call().bind("check_id", checkId)
                        .bind("item_id", itemId)
                        .bind("inventory_id", inventoryId)
                        .bind("result", result)
                        .bind("note", note != null ? note : ""))
                .map(InventoryCheckItem.map())
                .first()
                .orElseThrow();
    }

    /**
     * Finds all check items for a given check.
     *
     * @param checkId the check ID
     * @return list of check items
     */
    public List<InventoryCheckItem> findCheckItems(int checkId) {
        return query(
                        "SELECT id, check_id, item_id, inventory_id, result, note FROM inventory_check_item WHERE check_id = :check_id;")
                .single(call().bind("check_id", checkId))
                .map(InventoryCheckItem.map())
                .all();
    }

    // -- Locks --

    /**
     * Attempts to acquire a lock on a member for an inventory check.
     * Uses {@code ON CONFLICT DO NOTHING} to prevent duplicate locks.
     *
     * @param stationId the station ID
     * @param memberId  the member to lock
     * @param lockedBy  the member acquiring the lock
     * @return the acquired lock, or empty if the member is already locked
     */
    public Optional<InventoryCheckLock> acquireLock(int stationId, int memberId, int lockedBy) {
        return query("""
                INSERT INTO inventory_check_lock(station_id, member_id, locked_by)
                VALUES (:station_id, :member_id, :locked_by)
                ON CONFLICT (member_id) DO NOTHING
                RETURNING id, station_id, member_id, locked_by, locked_at;""")
                .single(call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("locked_by", lockedBy))
                .map(InventoryCheckLock.map())
                .first();
    }

    /**
     * Releases the lock on a member.
     *
     * @param memberId the member whose lock to release
     * @return {@code true} if a lock was released
     */
    public boolean releaseLock(int memberId) {
        return query("DELETE FROM inventory_check_lock WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .delete()
                .changed();
    }

    /**
     * Releases all locks held by a specific checker.
     *
     * @param lockedBy the member who holds the locks
     * @return {@code true} if any locks were released
     */
    public boolean releaseLockByLocker(int lockedBy) {
        return query("DELETE FROM inventory_check_lock WHERE locked_by = :locked_by;")
                .single(call().bind("locked_by", lockedBy))
                .delete()
                .changed();
    }

    /**
     * Finds the current lock on a member, if any.
     *
     * @param memberId the member ID
     * @return the lock, or empty if not locked
     */
    public Optional<InventoryCheckLock> findLock(int memberId) {
        return query(
                        "SELECT id, station_id, member_id, locked_by, locked_at FROM inventory_check_lock WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(InventoryCheckLock.map())
                .first();
    }

    /**
     * Releases all locks that have been held for longer than the specified duration.
     *
     * @param maxMinutes the maximum lock age in minutes before automatic release
     */
    public void releaseExpiredLocks(int maxMinutes) {
        query("DELETE FROM inventory_check_lock WHERE locked_at < now() - INTERVAL '1 minute' * :minutes;")
                .single(call().bind("minutes", maxMinutes))
                .delete();
    }

    // -- Navigation --

    /**
     * Finds the next unlocked member who was checked least recently (or never).
     *
     * @param stationId       the station ID
     * @param excludeMemberId the member ID to exclude (typically the current one)
     * @return the member ID, or empty if all members are locked or checked
     */
    public Optional<Integer> nextUncheckedMember(int stationId, int excludeMemberId, boolean teamOnly) {
        String roleFilter =
                teamOnly ? "AND sm.user_type IN ('TEAM','MANAGER','GUARDIAN')" : "AND sm.user_type = 'MEMBER'";
        return query("""
                SELECT
                    sm.id
                FROM
                    station_member sm
                        LEFT JOIN inventory_check_lock l
                        ON l.member_id = sm.id
                        LEFT JOIN LATERAL ( SELECT checked_at
                                            FROM inventory_check
                                            WHERE member_id = sm.id
                                            ORDER BY checked_at DESC
                                            LIMIT 1 ) lc
                        ON TRUE
                WHERE sm.station_id = :station_id
                  AND l.id IS NULL
                  AND sm.id != :exclude
                  AND sm.former = FALSE
                  AND ( lc.checked_at IS NULL OR lc.checked_at < current_date ) %s
                ORDER BY lc.checked_at ASC NULLS FIRST
                LIMIT 1;""", roleFilter)
                .single(call().bind("station_id", stationId).bind("exclude", excludeMemberId))
                .map(row -> row.getInt("id"))
                .first();
    }

    // -- Summary record --

    /**
     * Summary of a member's inventory check status, including lock information and roles.
     *
     * @param memberId         the member ID
     * @param firstName        the member's first name
     * @param lastName         the member's last name
     * @param lastCheckedAt    when the member was last checked, or {@code null} if never
     * @param checkerFirstName the first name of the person who last checked
     * @param checkerLastName  the last name of the person who last checked
     * @param locked           whether the member is currently locked for checking
     * @param lockedBy         the member who holds the lock, or {@code null}
     * @param lockerFirstName  the locker's first name
     * @param lockerLastName   the locker's last name
     * @param userType         the member's user type
     */
    public record MemberCheckSummary(
            int memberId,
            String firstName,
            String lastName,
            Instant lastCheckedAt,
            String checkerFirstName,
            String checkerLastName,
            boolean locked,
            Integer lockedBy,
            String lockerFirstName,
            String lockerLastName,
            StationUserType userType) {}
}
