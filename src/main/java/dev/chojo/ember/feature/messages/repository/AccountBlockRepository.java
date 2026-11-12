/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import dev.chojo.ember.feature.messages.entity.Person;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Who blocked whom for messages. A block is one-sided and the blocked account is never told.
 */
@Singleton
public class AccountBlockRepository {

    /**
     * Blocks an account. Blocking again changes nothing.
     *
     * @param blockerId the account blocking
     * @param blockedId the account blocked
     */
    public void block(int blockerId, int blockedId) {
        query("""
                INSERT INTO account_block(blocker_account_id, blocked_account_id)
                VALUES (:blocker_id, :blocked_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("blocker_id", blockerId).bind("blocked_id", blockedId))
                .insert();
    }

    /**
     * Takes a block back. Taking back what was never set changes nothing.
     *
     * @param blockerId the account that blocked
     * @param blockedId the account blocked
     */
    public void unblock(int blockerId, int blockedId) {
        query("""
                DELETE FROM account_block
                WHERE blocker_account_id = :blocker_id AND blocked_account_id = :blocked_id;""")
                .single(call().bind("blocker_id", blockerId).bind("blocked_id", blockedId))
                .delete();
    }

    /**
     * Whether one account blocked another.
     *
     * @param blockerId the account that may have blocked
     * @param blockedId the account that may be blocked
     * @return whether the block is set
     */
    public boolean blocks(int blockerId, int blockedId) {
        return query("""
                SELECT EXISTS(
                    SELECT 1 FROM account_block
                    WHERE blocker_account_id = :blocker_id AND blocked_account_id = :blocked_id
                ) AS blocks;""")
                .single(call().bind("blocker_id", blockerId).bind("blocked_id", blockedId))
                .map(row -> row.getBoolean("blocks"))
                .first()
                .orElse(false);
    }

    /**
     * The accounts somebody blocked, by name.
     *
     * @param blockerId the account that blocked
     * @return the blocked people
     */
    public List<Person> blockedBy(int blockerId) {
        return query("""
                SELECT a.id AS account_id, a.uid, a.first_name, a.last_name
                FROM account_block b
                JOIN account a ON a.id = b.blocked_account_id
                WHERE b.blocker_account_id = :blocker_id
                ORDER BY lower(a.first_name), lower(a.last_name), a.id;""")
                .single(call().bind("blocker_id", blockerId))
                .map(row -> Person.require(row, ""))
                .all();
    }
}
