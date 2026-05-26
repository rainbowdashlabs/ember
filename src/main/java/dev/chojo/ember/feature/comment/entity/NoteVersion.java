/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a version entry in the note history, stored as a unified diff patch.
 *
 * @param id        unique identifier of the version
 * @param noteId    the note this version belongs to
 * @param diffPatch the unified diff patch from the previous version
 * @param authorId  the member ID who created this version
 * @param createdAt timestamp when this version was created
 */
public record NoteVersion(int id, int noteId, String diffPatch, int authorId, Instant createdAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<NoteVersion> map() {
        return row -> new NoteVersion(
                row.getInt("id"),
                row.getInt("note_id"),
                row.getString("diff_patch"),
                row.getInt("author_id"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
