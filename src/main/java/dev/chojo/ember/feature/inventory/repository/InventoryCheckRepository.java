/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.CheckDetail;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.InventoryCheck;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckItem;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckLock;
import dev.chojo.ember.feature.inventory.entity.ItemCheckHistoryEntry;
import dev.chojo.ember.feature.inventory.entity.ItemLastCheck;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
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
    private static final String INVENTORY_CHECK_COLUMNS =
            "id, station_id, member_id, checked_by, checked_at, scope, container_id, deep, reported_by";
    private static final String INVENTORY_CHECK_ITEM_COLUMNS = "id, check_id, item_id, inventory_id, result, note";
    private static final String INVENTORY_CHECK_LOCK_COLUMNS = "id, station_id, member_id, locked_by, locked_at";

    /**
     * Creates a new inventory check record for a member.
     *
     * @param stationId the station ID
     * @param memberId  the member being checked
     * @param checkedBy the member performing the check
     * @return the created check
     */
    public InventoryCheck createCheck(int stationId, int memberId, int checkedBy) {
        return createCheck(stationId, memberId, checkedBy, null);
    }

    /**
     * Creates a check that carries two people: whoever said what was there, and whoever signed it
     * off.
     *
     * @param stationId  the station ID
     * @param memberId   the member being checked
     * @param checkedBy  the member who signed the check off
     * @param reportedBy who reported it, where that is somebody else, or {@code null}
     * @return the created check
     */
    public InventoryCheck createCheck(int stationId, int memberId, int checkedBy, Integer reportedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_check(station_id, member_id, checked_by, scope, reported_by)
                VALUES (:station_id, :member_id, :checked_by, 'MEMBER', :reported_by)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("checked_by", checkedBy)
                        .bind("reported_by", reportedBy),
                InventoryCheck.map(),
                INVENTORY_CHECK_COLUMNS);
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
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_check(station_id, container_id, checked_by, scope, deep)
                VALUES (:station_id, :container_id, :checked_by, 'CONTAINER', :deep)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("container_id", containerId)
                        .bind("checked_by", checkedBy)
                        .bind("deep", deep),
                InventoryCheck.map(),
                INVENTORY_CHECK_COLUMNS);
    }

    /**
     * Returns the most recent container-scope check for the given container, if any.
     */
    public Optional<InventoryCheck> latestCheckForContainer(int containerId) {
        return query("""
                SELECT %s
                FROM inventory_check
                WHERE container_id = :container_id AND scope = 'CONTAINER'
                ORDER BY checked_at DESC LIMIT 1;""", INVENTORY_CHECK_COLUMNS)
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
        return query("""
                SELECT %s FROM inventory_check WHERE member_id = :member_id ORDER BY checked_at DESC LIMIT 1;""", INVENTORY_CHECK_COLUMNS)
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
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_check_item(check_id, item_id, inventory_id, result, note)
                VALUES (:check_id, :item_id, :inventory_id, :result, :note)
                RETURNING %s;""",
                call().bind("check_id", checkId)
                        .bind("item_id", itemId)
                        .bind("inventory_id", inventoryId)
                        .bind("result", result)
                        .bind("note", note != null ? note : ""),
                InventoryCheckItem.map(),
                INVENTORY_CHECK_ITEM_COLUMNS);
    }

    /**
     * Returns the most recent check result for each supplied item. Items that have never been
     * included in a check are absent from the result list, so the caller can treat their entry
     * as "never checked".
     *
     * @param itemIds the items to look up
     * @return one entry per item that has at least one prior check, ordered by most-recent-first
     */
    public List<ItemLastCheck> latestCheckPerItem(Collection<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return Collections.emptyList();
        return query("""
                SELECT DISTINCT ON (ci.item_id)
                    ci.item_id,
                    ci.result,
                    c.checked_at,
                    a.first_name,
                    a.last_name
                FROM inventory_check_item ci
                    JOIN inventory_check c ON c.id = ci.check_id
                    LEFT JOIN station_member sm ON sm.id = c.checked_by
                    LEFT JOIN account a ON a.id = sm.account_id
                WHERE ci.item_id = ANY(:item_ids)
                ORDER BY ci.item_id, c.checked_at DESC;""")
                .single(call().bind("item_ids", List.copyOf(itemIds), PostgreSqlTypes.INTEGER))
                .map(row -> {
                    String first = row.getString("first_name");
                    String last = row.getString("last_name");
                    String checkerName = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
                    return new ItemLastCheck(
                            row.getInt("item_id"),
                            row.getEnum("result", CheckResult.class),
                            row.get("checked_at", INSTANT_TIMESTAMP),
                            checkerName);
                })
                .all();
    }

    /**
     * Returns every recorded check for a single item, newest-first, with the checker's name and
     * the container's name (when the check was container-scoped) joined in for display.
     */
    public List<ItemCheckHistoryEntry> findCheckHistoryForItem(int itemId) {
        return query("""
                SELECT
                    c.id            AS check_id,
                    ci.result,
                    c.checked_at,
                    c.scope,
                    ci.note,
                    a.first_name,
                    a.last_name,
                    co.name         AS container_name
                FROM inventory_check_item ci
                    JOIN inventory_check c ON c.id = ci.check_id
                    LEFT JOIN station_member sm ON sm.id = c.checked_by
                    LEFT JOIN account a ON a.id = sm.account_id
                    LEFT JOIN inventory_container co ON co.id = c.container_id
                WHERE ci.item_id = :item_id
                ORDER BY c.checked_at DESC;""")
                .single(call().bind("item_id", itemId))
                .map(row -> {
                    String first = row.getString("first_name");
                    String last = row.getString("last_name");
                    String checkerName = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
                    String note = row.getString("note");
                    return new ItemCheckHistoryEntry(
                            row.getInt("check_id"),
                            row.getEnum("result", CheckResult.class),
                            row.get("checked_at", INSTANT_TIMESTAMP),
                            checkerName,
                            row.getString("container_name"),
                            row.getString("scope"),
                            note == null ? "" : note);
                })
                .all();
    }

    /**
     * Finds all check items for a given check.
     *
     * @param checkId the check ID
     * @return list of check items
     */
    public List<InventoryCheckItem> findCheckItems(int checkId) {
        return query("""
                SELECT %s FROM inventory_check_item WHERE check_id = :check_id;""", INVENTORY_CHECK_ITEM_COLUMNS)
                .single(call().bind("check_id", checkId))
                .map(InventoryCheckItem.map())
                .all();
    }

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
                RETURNING %s;""", INVENTORY_CHECK_LOCK_COLUMNS)
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
        return query("""
                SELECT %s FROM inventory_check_lock WHERE member_id = :member_id;""", INVENTORY_CHECK_LOCK_COLUMNS)
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
