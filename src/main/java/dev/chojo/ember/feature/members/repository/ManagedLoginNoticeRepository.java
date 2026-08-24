/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The access changes a guardian made that the members in their care have not been told about yet.
 *
 * <p>A member has at most one of them: only the newest change is worth announcing, so scheduling
 * another one for the same member replaces what was waiting.
 */
@Singleton
public class ManagedLoginNoticeRepository {

    private static final RowMapping<PendingNotice> PENDING_NOTICE = row ->
            new PendingNotice(row.getInt("member_id"), row.getBoolean("granted"), row.get("due_at", INSTANT_TIMESTAMP));

    /**
     * A change waiting to be announced.
     *
     * @param granted whether signing in was switched on, rather than taken away
     * @param dueAt   when the mail may leave
     */
    public record PendingNotice(int memberId, boolean granted, Instant dueAt) {}

    /**
     * Records a change as waiting, replacing whatever was waiting for that member.
     */
    public void schedule(int memberId, boolean granted, Instant dueAt) {
        query("""
                INSERT INTO managed_login_notice(member_id, granted, due_at)
                VALUES (:member_id, :granted, :due_at)
                ON CONFLICT (member_id) DO UPDATE SET granted = :granted, due_at = :due_at;""")
                .single(call().bind("member_id", memberId)
                        .bind("granted", granted)
                        .bind("due_at", dueAt, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * The change waiting for this member, if any.
     */
    public Optional<PendingNotice> find(int memberId) {
        return query("SELECT member_id, granted, due_at FROM managed_login_notice WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(PENDING_NOTICE)
                .first();
    }

    /**
     * Drops what was waiting for this member, whether because it was announced or because the switch
     * came back to where it started.
     *
     * @return whether anything was waiting
     */
    public boolean cancel(int memberId) {
        return query("DELETE FROM managed_login_notice WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .delete()
                .changed();
    }

    /**
     * Everything whose waiting time has passed, oldest first.
     */
    public List<PendingNotice> findDue(Instant now) {
        return query("""
                SELECT member_id, granted, due_at
                FROM managed_login_notice
                WHERE due_at <= :now
                ORDER BY due_at;""")
                .single(call().bind("now", now, INSTANT_TIMESTAMP))
                .map(PENDING_NOTICE)
                .all();
    }
}
