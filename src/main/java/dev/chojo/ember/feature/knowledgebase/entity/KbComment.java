/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a threaded comment on a knowledge base file.
 *
 * @param id        unique identifier of the comment
 * @param fileId    the KB file this comment belongs to
 * @param parentId  parent comment ID for threaded replies, or {@code null} for top-level comments
 * @param authorId  member ID of the comment author
 * @param content   text content of the comment
 * @param deleted   whether the comment has been soft-deleted
 * @param createdAt timestamp when the comment was created
 * @param updatedAt timestamp when the comment was last updated, or {@code null}
 */
public record KbComment(
        int id,
        int fileId,
        Integer parentId,
        int authorId,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<KbComment> map() {
        return row -> new KbComment(
                row.getInt("id"),
                row.getInt("file_id"),
                row.getObject("parent_id", Integer.class),
                row.getInt("author_id"),
                row.getString("content"),
                row.getBoolean("deleted"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
