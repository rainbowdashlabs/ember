/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.entity.CheckResult;
import dev.chojo.ember.entity.InventoryCheck;
import dev.chojo.ember.entity.InventoryCheckItem;
import dev.chojo.ember.entity.InventoryCheckLock;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class InventoryCheckRepository {

    // -- Checks --

    public InventoryCheck createCheck(int stationId, int memberId, int checkedBy) {
        return Query.query("""
                            INSERT INTO inventory_check(station_id, member_id, checked_by)
                            VALUES (:station_id, :member_id, :checked_by)
                            RETURNING id, station_id, member_id, checked_by, checked_at;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("checked_by", checkedBy))
                .map(InventoryCheck.map())
                .first()
                .orElseThrow();
    }

    public Optional<InventoryCheck> latestCheckForMember(int memberId) {
        return Query.query(
                        "SELECT id, station_id, member_id, checked_by, checked_at FROM inventory_check WHERE member_id = :member_id ORDER BY checked_at DESC LIMIT 1;")
                .single(Call.of().bind("member_id", memberId))
                .map(InventoryCheck.map())
                .first();
    }

    public List<MemberCheckSummary> checkOverview(int stationId) {
        return Query.query("""
                            SELECT sm.id AS member_id, a.first_name, a.last_name,
                                   lc.checked_at AS last_checked_at, lc.checked_by,
                                   ca.first_name AS checker_first_name, ca.last_name AS checker_last_name,
                                   (l.id IS NOT NULL) AS locked,
                                   l.locked_by,
                                   la.first_name AS locker_first_name, la.last_name AS locker_last_name
                            FROM station_member sm
                                JOIN account a ON a.id = sm.account_id
                                LEFT JOIN LATERAL (
                                    SELECT checked_at, checked_by FROM inventory_check
                                    WHERE member_id = sm.id ORDER BY checked_at DESC LIMIT 1
                                ) lc ON TRUE
                                LEFT JOIN station_member csm ON csm.id = lc.checked_by
                                LEFT JOIN account ca ON ca.id = csm.account_id
                                LEFT JOIN inventory_check_lock l ON l.member_id = sm.id
                                LEFT JOIN station_member lsm ON lsm.id = l.locked_by
                                LEFT JOIN account la ON la.id = lsm.account_id
                            WHERE sm.station_id = :station_id
                            ORDER BY lc.checked_at ASC NULLS FIRST;""")
                .single(Call.of().bind("station_id", stationId))
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
                        List.of()))
                .all();
    }

    public Optional<CheckDetail> latestCheckDetail(int memberId) {
        var check = latestCheckForMember(memberId);
        if (check.isEmpty()) return Optional.empty();
        var items = findCheckItems(check.get().id());
        // Get checker name
        var names = Query.query("""
                    SELECT a.first_name, a.last_name FROM station_member sm
                        JOIN account a ON a.id = sm.account_id
                    WHERE sm.id = :id;""")
                .single(Call.of().bind("id", check.get().checkedBy()))
                .map(row -> new String[] {row.getString("first_name"), row.getString("last_name")})
                .first()
                .orElse(new String[] {"", ""});
        return Optional.of(new CheckDetail(check.get(), names[0], names[1], items));
    }

    // -- Check Items --

    public InventoryCheckItem createCheckItem(
            int checkId, Integer itemId, Integer inventoryId, CheckResult result, String note) {
        return Query.query("""
                            INSERT INTO inventory_check_item(check_id, item_id, inventory_id, result, note)
                            VALUES (:check_id, :item_id, :inventory_id, :result, :note)
                            RETURNING id, check_id, item_id, inventory_id, result, note;""")
                .single(Call.of()
                        .bind("check_id", checkId)
                        .bind("item_id", itemId)
                        .bind("inventory_id", inventoryId)
                        .bind("result", result)
                        .bind("note", note != null ? note : ""))
                .map(InventoryCheckItem.map())
                .first()
                .orElseThrow();
    }

    public List<InventoryCheckItem> findCheckItems(int checkId) {
        return Query.query(
                        "SELECT id, check_id, item_id, inventory_id, result, note FROM inventory_check_item WHERE check_id = :check_id;")
                .single(Call.of().bind("check_id", checkId))
                .map(InventoryCheckItem.map())
                .all();
    }

    // -- Locks --

    public Optional<InventoryCheckLock> acquireLock(int stationId, int memberId, int lockedBy) {
        return Query.query("""
                            INSERT INTO inventory_check_lock(station_id, member_id, locked_by)
                            VALUES (:station_id, :member_id, :locked_by)
                            ON CONFLICT (member_id) DO NOTHING
                            RETURNING id, station_id, member_id, locked_by, locked_at;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("locked_by", lockedBy))
                .map(InventoryCheckLock.map())
                .first();
    }

    public boolean releaseLock(int memberId) {
        return Query.query("DELETE FROM inventory_check_lock WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .delete()
                .changed();
    }

    public boolean releaseLockByLocker(int lockedBy) {
        return Query.query("DELETE FROM inventory_check_lock WHERE locked_by = :locked_by;")
                .single(Call.of().bind("locked_by", lockedBy))
                .delete()
                .changed();
    }

    public Optional<InventoryCheckLock> findLock(int memberId) {
        return Query.query(
                        "SELECT id, station_id, member_id, locked_by, locked_at FROM inventory_check_lock WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(InventoryCheckLock.map())
                .first();
    }

    public void releaseExpiredLocks(int maxMinutes) {
        Query.query("DELETE FROM inventory_check_lock WHERE locked_at < NOW() - INTERVAL '1 minute' * :minutes;")
                .single(Call.of().bind("minutes", maxMinutes))
                .delete();
    }

    // -- Navigation --

    public Optional<Integer> nextUncheckedMember(int stationId, int excludeMemberId) {
        return Query.query("""
                            SELECT sm.id FROM station_member sm
                                LEFT JOIN inventory_check_lock l ON l.member_id = sm.id
                                LEFT JOIN LATERAL (
                                    SELECT checked_at FROM inventory_check WHERE member_id = sm.id ORDER BY checked_at DESC LIMIT 1
                                ) lc ON TRUE
                            WHERE sm.station_id = :station_id
                              AND l.id IS NULL
                              AND sm.id != :exclude
                            ORDER BY lc.checked_at ASC NULLS FIRST
                            LIMIT 1;""")
                .single(Call.of().bind("station_id", stationId).bind("exclude", excludeMemberId))
                .map(row -> row.getInt("id"))
                .first();
    }

    // -- Summary record --

    public record MemberCheckSummary(
            int memberId,
            String firstName,
            String lastName,
            java.time.Instant lastCheckedAt,
            String checkerFirstName,
            String checkerLastName,
            boolean locked,
            Integer lockedBy,
            String lockerFirstName,
            String lockerLastName,
            List<Roles> roles) {}

    public record CheckDetail(
            InventoryCheck check, String checkerFirstName, String checkerLastName, List<InventoryCheckItem> items) {}
}
