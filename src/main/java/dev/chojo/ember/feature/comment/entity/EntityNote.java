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
 * Represents a shared note attached to an entity (event, news, etc.).
 *
 * @param id         unique identifier of the note
 * @param entityType the type of entity this note is attached to (e.g. "event", "news")
 * @param entityId   the ID of the entity this note is attached to
 * @param stationId  the station this note belongs to
 * @param content    the markdown content of the note
 * @param updatedBy  the member ID who last updated the note, or {@code null}
 * @param updatedAt  timestamp when the note was last updated
 */
public record EntityNote(
        int id, String entityType, int entityId, int stationId, String content, Integer updatedBy, Instant updatedAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<EntityNote> map() {
        return row -> new EntityNote(
                row.getInt("id"),
                row.getString("entity_type"),
                row.getInt("entity_id"),
                row.getInt("station_id"),
                row.getString("content"),
                row.getObject("updated_by", Integer.class),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
