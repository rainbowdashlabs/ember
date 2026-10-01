/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A comment on an appointment, a news entry, a knowledge base file or a board ticket, supporting
 * threaded replies.
 *
 * @param id        unique identifier of the comment, unique across every kind of target
 * @param type      what kind of thing the comment was written on
 * @param targetId  the appointment, entry, file or ticket it was written on
 * @param stationId the station owning that target, {@code null} only under a news entry the
 *                  instance published to every station
 * @param eventDate for comments on a specific occurrence of a recurring appointment, the date of
 *                  that occurrence; {@code null} for whole-appointment comments and every other kind
 * @param parentId  parent comment ID for threaded replies, or {@code null} for top-level comments
 * @param author    identity of the comment author (station + member UUIDs), or {@code null}
 * @param content   text content of the comment
 * @param deleted   whether the comment has been soft-deleted
 * @param createdAt timestamp when the comment was created
 * @param updatedAt timestamp when the comment was last updated, or {@code null}
 */
public record Comment(
        int id,
        CommentEntityType type,
        int targetId,
        @Nullable Integer stationId,
        @Nullable LocalDate eventDate,
        @Nullable Integer parentId,
        @Nullable MemberIdentity author,
        String content,
        boolean deleted,
        Instant createdAt,
        @Nullable Instant updatedAt) {
    /**
     * Creates a row mapping for database result set conversion. The target column that is set
     * decides the type.
     */
    public static RowMapping<Comment> map() {
        return row -> {
            UUID stationUid = row.get("author_station_uid", StandardValueConverter.UUID_STRING);
            UUID memberUid = row.get("author_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity author =
                    (stationUid != null && memberUid != null) ? new MemberIdentity(stationUid, memberUid) : null;
            CommentEntityType type = null;
            int targetId = 0;
            for (var candidate : CommentEntityType.values()) {
                Integer target = row.getObject(candidate.column(), Integer.class);
                if (target != null) {
                    type = candidate;
                    targetId = target;
                }
            }
            return new Comment(
                    row.getInt("id"),
                    Objects.requireNonNull(type, "a comment row names no target"),
                    targetId,
                    row.getObject("station_id", Integer.class),
                    row.getObject("event_date", LocalDate.class),
                    row.getObject("parent_id", Integer.class),
                    author,
                    row.getString("content"),
                    row.getBoolean("deleted"),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("updated_at", INSTANT_TIMESTAMP));
        };
    }
}
