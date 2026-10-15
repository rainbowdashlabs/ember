/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.messages.entity.Reaction;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The emoji people reacted to messages with. Each person reacts with each emoji at most once per
 * message, which the table's key holds.
 */
@Singleton
public class ReactionRepository {

    /**
     * Adds a reaction. Adding one that is there already changes nothing.
     *
     * @param messageId the message
     * @param accountId who reacts
     * @param emoji     the emoji
     * @return whether it was new
     */
    public boolean add(int messageId, int accountId, String emoji) {
        return query("""
                INSERT INTO message_reaction(message_id, account_id, emoji)
                VALUES (:message_id, :account_id, :emoji)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("message_id", messageId)
                        .bind("account_id", accountId)
                        .bind("emoji", emoji))
                .insert()
                .changed();
    }

    /**
     * Takes a reaction back.
     *
     * @param messageId the message
     * @param accountId who reacted
     * @param emoji     the emoji
     * @return whether there was one to take back
     */
    public boolean remove(int messageId, int accountId, String emoji) {
        return query("""
                DELETE FROM message_reaction
                WHERE message_id = :message_id AND account_id = :account_id AND emoji = :emoji;""")
                .single(call().bind("message_id", messageId)
                        .bind("account_id", accountId)
                        .bind("emoji", emoji))
                .delete()
                .changed();
    }

    /**
     * The reactions to messages as a reader sees them: those of people the reader blocked are left out.
     *
     * @param messageIds the messages
     * @param readerId   the reader
     * @return the reactions, in the order they were given
     */
    public List<Reaction> forMessages(List<Integer> messageIds, int readerId) {
        if (messageIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM message_reaction r
                JOIN account a ON a.id = r.account_id
                WHERE r.message_id = ANY(:ids)
                AND NOT EXISTS (
                    SELECT 1 FROM account_block b
                    WHERE b.blocker_account_id = :reader_id AND b.blocked_account_id = r.account_id)
                ORDER BY r.message_id, r.created_at, r.account_id;""", Reaction.COLUMNS)
                .single(call().bind("ids", messageIds, PostgreSqlTypes.INTEGER).bind("reader_id", readerId))
                .map(Reaction.map())
                .all();
    }
}
