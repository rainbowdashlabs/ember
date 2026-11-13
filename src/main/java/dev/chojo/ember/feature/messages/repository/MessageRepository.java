/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.messages.entity.DigestMessage;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The messages of conversations, and the system lines between them.
 *
 * <p>A deleted message keeps its row with its text emptied, so a conversation still shows where it
 * stood and an answer to it still has something to point at.
 */
@Singleton
public class MessageRepository {

    /**
     * Writes a message.
     *
     * @param conversationId the conversation
     * @param authorId       the account writing
     * @param content        the text
     * @param replyToId      the message it answers, or null
     * @return the message's id
     */
    public int write(int conversationId, int authorId, String content, @Nullable Integer replyToId) {
        return query("""
                INSERT INTO message(conversation_id, author_account_id, content, reply_to_id)
                VALUES (:conversation_id, :author_id, :content, :reply_to_id)
                RETURNING id;""")
                .single(call().bind("conversation_id", conversationId)
                        .bind("author_id", authorId)
                        .bind("content", content)
                        .bind("reply_to_id", replyToId))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * Writes a system line.
     *
     * @param conversationId the conversation
     * @param authorId       who did what it says, or null where nobody did
     * @param kind           what it says
     * @param subjectId      whom it is about, or null
     * @param content        the new name of a rename, empty otherwise
     * @return the line's id
     */
    public int writeSystem(
            int conversationId,
            @Nullable Integer authorId,
            SystemKind kind,
            @Nullable Integer subjectId,
            String content) {
        return query("""
                INSERT INTO message(conversation_id, author_account_id, content, system_kind, system_subject_account_id)
                VALUES (:conversation_id, :author_id, :content, :kind, :subject_id)
                RETURNING id;""")
                .single(call().bind("conversation_id", conversationId)
                        .bind("author_id", authorId)
                        .bind("content", content)
                        .bind("kind", kind)
                        .bind("subject_id", subjectId))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * A message by its id.
     *
     * @param id the message
     * @return the message, or empty where there is none
     */
    public Optional<Message> find(int id) {
        return query("""
                SELECT %s FROM message msg
                %s
                WHERE msg.id = :id;""", Message.COLUMNS, Message.JOINS)
                .single(call().bind("id", id))
                .map(Message.map())
                .first();
    }

    /**
     * Messages by their ids, in any order.
     *
     * @param ids the messages
     * @return the ones that exist
     */
    public List<Message> findAll(List<Integer> ids) {
        if (ids.isEmpty()) return List.of();
        return query("""
                SELECT %s FROM message msg
                %s
                WHERE msg.id = ANY(:ids);""", Message.COLUMNS, Message.JOINS)
                .single(call().bind("ids", ids, PostgreSqlTypes.INTEGER))
                .map(Message.map())
                .all();
    }

    /**
     * The newest messages of a conversation before a message, oldest first.
     *
     * @param conversationId the conversation
     * @param beforeId       the message to read back from, or null for the newest
     * @param limit          how many at most
     * @return the messages
     */
    public List<Message> before(int conversationId, @Nullable Integer beforeId, int limit) {
        return query("""
                SELECT * FROM (
                    SELECT %s FROM message msg
                    %s
                    WHERE msg.conversation_id = :conversation_id AND (:before_id::int IS NULL OR msg.id < :before_id)
                    ORDER BY msg.id DESC
                    LIMIT :limit
                ) page ORDER BY id;""", Message.COLUMNS, Message.JOINS)
                .single(call().bind("conversation_id", conversationId)
                        .bind("before_id", beforeId)
                        .bind("limit", limit))
                .map(Message.map())
                .all();
    }

    /**
     * The messages of a conversation after a message, oldest first.
     *
     * @param conversationId the conversation
     * @param afterId        the message to read on from
     * @param limit          how many at most
     * @return the messages
     */
    public List<Message> after(int conversationId, int afterId, int limit) {
        return query("""
                SELECT %s FROM message msg
                %s
                WHERE msg.conversation_id = :conversation_id AND msg.id > :after_id
                ORDER BY msg.id
                LIMIT :limit;""", Message.COLUMNS, Message.JOINS)
                .single(call().bind("conversation_id", conversationId)
                        .bind("after_id", afterId)
                        .bind("limit", limit))
                .map(Message.map())
                .all();
    }

    /**
     * The newest message of each conversation.
     *
     * @param conversationIds the conversations
     * @return one message for each conversation that has any
     */
    public List<Message> newestOf(List<Integer> conversationIds) {
        if (conversationIds.isEmpty()) return List.of();
        return query("""
                SELECT DISTINCT ON (msg.conversation_id) %s FROM message msg
                %s
                WHERE msg.conversation_id = ANY(:ids)
                ORDER BY msg.conversation_id, msg.id DESC;""", Message.COLUMNS, Message.JOINS)
                .single(call().bind("ids", conversationIds, PostgreSqlTypes.INTEGER))
                .map(Message.map())
                .all();
    }

    /**
     * Changes a message's text and marks it edited.
     *
     * @param id      the message
     * @param content the new text
     * @return whether it still stood and was changed
     */
    public boolean edit(int id, String content) {
        return query("""
                UPDATE message SET content = :content, edited_at = now() WHERE id = :id AND deleted_at IS NULL;""")
                .single(call().bind("id", id).bind("content", content))
                .update()
                .changed();
    }

    /**
     * Deletes a message, keeping its place: the text goes, the row stays.
     *
     * @param id          the message
     * @param byAccountId its author, who deletes it
     */
    public void delete(int id, int byAccountId) {
        query("""
                UPDATE message SET content = '', deleted_at = now(), deleted_by_account_id = :by
                WHERE id = :id AND deleted_at IS NULL;""").single(call().bind("id", id).bind("by", byAccountId)).update();
    }

    /**
     * Removes a message for the station: like a deletion, but it shows as removed by the station.
     *
     * @param id          the message
     * @param byAccountId the account removing it
     * @return whether it stood until now
     */
    public boolean removeForStation(int id, int byAccountId) {
        return query("""
                UPDATE message SET content = '', deleted_at = now(), deleted_by_account_id = :by, removed_by_station = TRUE
                WHERE id = :id AND deleted_at IS NULL;""")
                .single(call().bind("id", id).bind("by", byAccountId))
                .update()
                .changed();
    }

    /**
     * The condition a message aliased {@code msg} meets when it belongs into the daily mail of the
     * member aliased {@code m}, whose account is aliased {@code a} and conversation {@code c}: unread,
     * written after the account's last daily mail, in a conversation the member has not muted, and
     * from a station chat only where it mentions the account or the account takes everything.
     */
    private static final String IN_DIGEST = """
            m.left_at IS NULL AND m.muted = FALSE
            AND msg.created_at > coalesce(a.message_digest_sent_at, '-infinity'::timestamptz)
            AND (c.kind <> 'STATION' OR a.messages_station_digest = 'ALL'
                 OR strpos(msg.content, '@[account/' || a.uid::text || ':') > 0)""";

    /**
     * The accounts the daily mail goes to now: those that want it, can use messages and have
     * something for it.
     *
     * @return the account ids
     */
    public List<Integer> digestReaders() {
        return query("""
                SELECT a.id FROM account a
                WHERE a.messages_mail AND %s
                AND EXISTS (
                    SELECT 1
                    FROM conversation_member m
                    JOIN conversation c ON c.id = m.conversation_id
                    JOIN message msg ON %s
                    WHERE m.account_id = a.id AND %s)
                ORDER BY a.id;""", MessagePeopleRepository.USABLE, ConversationRepository.UNREAD, IN_DIGEST)
                .single()
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * What goes into an account's daily mail: the newest messages of each conversation as far as
     * the mail quotes them, each with how many wait there in all, by conversation and then oldest
     * first.
     *
     * @param accountId the account
     * @param quoted    how many of each conversation's newest messages to read
     * @return the messages
     */
    public List<DigestMessage> digestOf(int accountId, int quoted) {
        return query("""
                SELECT * FROM (
                    SELECT %s,
                        count(*) OVER (PARTITION BY msg.conversation_id) AS waiting,
                        row_number() OVER (PARTITION BY msg.conversation_id ORDER BY msg.id DESC) AS newest
                    FROM conversation_member m
                    JOIN conversation c ON c.id = m.conversation_id
                    JOIN account a ON a.id = m.account_id
                    JOIN message msg ON %s
                    %s
                    WHERE m.account_id = :account_id AND %s
                ) found
                WHERE newest <= :quoted
                ORDER BY conversation_id, id;""", Message.COLUMNS, ConversationRepository.UNREAD, Message.JOINS, IN_DIGEST)
                .single(call().bind("account_id", accountId).bind("quoted", quoted))
                .map(DigestMessage.map())
                .all();
    }

    /**
     * Moves the daily mail's mark of every account that wants it up to a moment, except the ones
     * named, so the next mail takes nothing written before it. Accounts that had nothing for this
     * mail then start the next one from here instead of from all they ever left unread.
     *
     * @param at     the moment the run started
     * @param except the accounts the run looked at itself
     */
    public void markDigestedUpTo(Instant at, List<Integer> except) {
        query("""
                UPDATE account SET message_digest_sent_at = :at
                WHERE messages_mail AND (message_digest_sent_at IS NULL OR message_digest_sent_at < :at)
                AND id <> ALL(:except);""")
                .single(call().bind("at", at, INSTANT_TIMESTAMP).bind("except", except, PostgreSqlTypes.INTEGER))
                .update();
    }

    /**
     * Notes that an account was sent its daily mail, so the next one only takes what is newer.
     *
     * @param accountId the account
     * @param at        when the newest message the mail took was written, so a message written while
     *                  the mail was put together goes into the next one
     */
    public void markDigestSent(int accountId, Instant at) {
        query("""
                UPDATE account SET message_digest_sent_at = :at WHERE id = :account_id;""")
                .single(call().bind("account_id", accountId).bind("at", at, INSTANT_TIMESTAMP))
                .update();
    }
}
