/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.entity.UnreadMailDue;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
     */
    public void edit(int id, String content) {
        query("""
                UPDATE message SET content = :content, edited_at = now() WHERE id = :id;""").single(call().bind("id", id).bind("content", content)).update();
    }

    /**
     * Deletes a message, keeping its place: the text goes, the row stays.
     *
     * @param id the message
     */
    public void delete(int id) {
        query("""
                UPDATE message SET content = '', deleted_at = now() WHERE id = :id AND deleted_at IS NULL;""").single(call().bind("id", id)).update();
    }

    /**
     * The conversations whose readers have waited long enough on an unread message of somebody
     * else to be mailed about it: once per unread stretch, not for muted conversations, and not for
     * readers who turned the mail off.
     *
     * @param writtenBefore only messages written before this count
     * @return one entry per reader and conversation
     */
    public List<UnreadMailDue> unreadMailDue(Instant writtenBefore) {
        return query("""
                SELECT %s, m.account_id AS reader_account_id, m.last_read_message_id
                FROM conversation_member m
                JOIN conversation c ON c.id = m.conversation_id
                JOIN account a ON a.id = m.account_id
                WHERE m.left_at IS NULL AND m.muted = FALSE AND m.mailed_at IS NULL AND a.messages_mail
                AND EXISTS (
                    SELECT 1 FROM message msg
                    WHERE msg.conversation_id = m.conversation_id
                    AND msg.id > coalesce(m.last_read_message_id, 0)
                    AND msg.system_kind IS NULL AND msg.deleted_at IS NULL
                    AND msg.author_account_id IS DISTINCT FROM m.account_id
                    AND msg.created_at < :before
                    AND NOT EXISTS (
                        SELECT 1 FROM account_block b
                        WHERE b.blocker_account_id = m.account_id AND b.blocked_account_id = msg.author_account_id))
                ORDER BY m.account_id, c.id;""", Conversation.COLUMNS)
                .single(call().bind("before", writtenBefore, INSTANT_TIMESTAMP))
                .map(UnreadMailDue.map())
                .all();
    }

    /**
     * The first messages a reader has not read in a conversation, the way a mail quotes them.
     *
     * @param due   the reader and conversation
     * @param limit how many at most
     * @return the unread messages of others the reader has not blocked, oldest first
     */
    public List<Message> unreadFor(UnreadMailDue due, int limit) {
        return query("""
                SELECT %s FROM message msg
                %s
                WHERE msg.conversation_id = :conversation_id AND msg.id > :after_id
                AND msg.system_kind IS NULL AND msg.deleted_at IS NULL
                AND msg.author_account_id IS DISTINCT FROM :reader_id
                AND NOT EXISTS (
                    SELECT 1 FROM account_block b
                    WHERE b.blocker_account_id = :reader_id AND b.blocked_account_id = msg.author_account_id)
                ORDER BY msg.id
                LIMIT :limit;""", Message.COLUMNS, Message.JOINS)
                .single(call().bind("conversation_id", due.conversation().id())
                        .bind("after_id", Objects.requireNonNullElse(due.lastReadMessageId(), 0))
                        .bind("reader_id", due.accountId())
                        .bind("limit", limit))
                .map(Message.map())
                .all();
    }

    /**
     * Notes that a reader was mailed about a conversation, so the next mail waits until they read.
     *
     * @param conversationId the conversation
     * @param accountId      the reader
     */
    public void markMailed(int conversationId, int accountId) {
        query("""
                UPDATE conversation_member SET mailed_at = now()
                WHERE conversation_id = :conversation_id AND account_id = :account_id;""")
                .single(call().bind("conversation_id", conversationId).bind("account_id", accountId))
                .update();
    }
}
