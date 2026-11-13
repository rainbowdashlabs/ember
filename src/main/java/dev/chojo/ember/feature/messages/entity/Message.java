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
 * @param deletedAt        when its author or the station deleted it, or null
 * @param removedByStation whether the station removed it rather than its author deleting it
 * @param deletedBy        who deleted or removed it, or null while it stands and once that account is gone
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
        @Nullable Instant deletedAt,
        boolean removedByStation,
        @Nullable Person deletedBy) {

    /**
     * The columns {@link #map()} reads, for a message table aliased {@code msg} joined to its author
     * as {@code au}, its subject as {@code su} and who deleted it as {@code db}, all left joins.
     */
    public static final String COLUMNS = """
            msg.id, msg.conversation_id, msg.content, msg.reply_to_id, msg.system_kind, msg.created_at, \
            msg.edited_at, msg.deleted_at, msg.removed_by_station, %s, %s, %s""".formatted(
            Person.columns("au", "author_"), Person.columns("su", "subject_"), Person.columns("db", "deleter_"));

    /** The joins {@link #COLUMNS} needs after {@code FROM message msg}. */
    public static final String JOINS = """
            LEFT JOIN account au ON au.id = msg.author_account_id
            LEFT JOIN account su ON su.id = msg.system_subject_account_id
            LEFT JOIN account db ON db.id = msg.deleted_by_account_id""";

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
                row.get("deleted_at", INSTANT_TIMESTAMP),
                row.getBoolean("removed_by_station"),
                Person.read(row, "deleter_"));
    }

    /**
     * The same message without who removed it, which only the people handling the station's chats
     * see.
     *
     * @return the message, its remover left out
     */
    public Message withoutRemover() {
        if (!removedByStation || deletedBy == null) return this;
        return new Message(
                id,
                conversationId,
                author,
                content,
                replyToId,
                systemKind,
                subject,
                createdAt,
                editedAt,
                deletedAt,
                true,
                null);
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
