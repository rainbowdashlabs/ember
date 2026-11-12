/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One message in a conversation, or a system line.
 *
 * @param id             the message, which also orders the conversation
 * @param conversationId the conversation it was written in
 * @param author         who wrote it, or did what a system line says; null once that account is deleted
 * @param content        the text, empty once deleted and for most system lines
 * @param replyToId      the message it answers, or null
 * @param systemKind     what a system line says, or null for a written message
 * @param subject        whom a system line is about, or null
 * @param createdAt      when it was written
 * @param editedAt       when its author last changed it, or null
 * @param deletedAt      when its author deleted it, or null
 */
public record Message(
        int id,
        int conversationId,
        @Nullable Person author,
        String content,
        @Nullable Integer replyToId,
        @Nullable SystemKind systemKind,
        @Nullable Person subject,
        Instant createdAt,
        @Nullable Instant editedAt,
        @Nullable Instant deletedAt) {

    /**
     * The columns {@link #map()} reads, for a message table aliased {@code msg} joined to its author
     * as {@code au} and its subject as {@code su}, both left joins.
     */
    public static final String COLUMNS =
            """
            msg.id, msg.conversation_id, msg.content, msg.reply_to_id, msg.system_kind, msg.created_at, \
            msg.edited_at, msg.deleted_at, %s, %s""".formatted(Person.columns("au", "author_"), Person.columns("su", "subject_"));

    /** The joins {@link #COLUMNS} needs after {@code FROM message msg}. */
    public static final String JOINS = """
            LEFT JOIN account au ON au.id = msg.author_account_id
            LEFT JOIN account su ON su.id = msg.system_subject_account_id""";

    /**
     * Reads a message row.
     *
     * @return the mapping
     */
    public static RowMapping<Message> map() {
        return row -> new Message(
                row.getInt("id"),
                row.getInt("conversation_id"),
                Person.read(row, "author_"),
                row.getString("content"),
                row.getObject("reply_to_id", Integer.class),
                row.getString("system_kind") == null ? null : row.getEnum("system_kind", SystemKind.class),
                Person.read(row, "subject_"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("edited_at", INSTANT_TIMESTAMP),
                row.get("deleted_at", INSTANT_TIMESTAMP));
    }

    /** Whether somebody wrote this, rather than it being a system line. */
    public boolean written() {
        return systemKind == null;
    }

    /** Whether its author deleted it. */
    public boolean deleted() {
        return deletedAt != null;
    }

    /**
     * Whether the given account wrote it.
     *
     * @param accountId the account
     * @return true for a written message of theirs
     */
    public boolean writtenBy(int accountId) {
        Person by = author;
        return written() && by != null && by.accountId() == accountId;
    }
}
