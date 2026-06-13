/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a comment on an event, supporting threaded replies.
 *
 * @param id        unique identifier of the comment
 * @param parentId  parent comment ID for threaded replies, or {@code null} for top-level comments
 * @param author    identity of the comment author (station + member UUIDs), or {@code null}
 * @param content   text content of the comment
 * @param deleted   whether the comment has been soft-deleted
 * @param createdAt timestamp when the comment was created
 * @param updatedAt timestamp when the comment was last updated, or {@code null}
 * @param eventDate for comments on a specific occurrence of a recurring event, the ISO date
 *                  of that occurrence; {@code null} for one-time events and for whole-event
 *                  comments
 */
public record Comment(
        int id,
        Integer parentId,
        MemberIdentity author,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        LocalDate eventDate) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Comment> map() {
        return row -> {
            UUID stationUid = row.get("author_station_uid", StandardValueConverter.UUID_STRING);
            UUID memberUid = row.get("author_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity author =
                    (stationUid != null && memberUid != null) ? new MemberIdentity(stationUid, memberUid) : null;
            return new Comment(
                    row.getInt("id"),
                    row.getObject("parent_id", Integer.class),
                    author,
                    row.getString("content"),
                    row.getBoolean("deleted"),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("updated_at", INSTANT_TIMESTAMP),
                    row.getObject("event_date", LocalDate.class));
        };
    }
}
