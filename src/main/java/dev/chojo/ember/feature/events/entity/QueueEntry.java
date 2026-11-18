/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.wrapper.Row;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One sign-up waiting on a waiting list, of an appointment date or of an event group.
 *
 * @param id        the entry
 * @param localId   the waiting sign-up of a member of this station, or null for a partner's member
 * @param partnerId the waiting sign-up of a partner's member, or null for a member of this station
 * @param position  the order key; lower moves up first, gaps are allowed
 * @param rank      the place in the list as the member is shown it, 1 moves up next
 * @param score     the fairness score taken on joining
 * @param joinedAt  when the sign-up joined the list
 */
public record QueueEntry(
        int id,
        @Nullable Integer localId,
        @Nullable Integer partnerId,
        int position,
        int rank,
        @Nullable Double score,
        Instant joinedAt) {

    /**
     * Reads an entry from a row carrying the owner columns as {@code local_id} and {@code partner_id}
     * and the rank as {@code rank}.
     *
     * @param row the row
     * @return the entry
     * @throws SQLException when a column cannot be read
     */
    public static QueueEntry map(Row row) throws SQLException {
        return new QueueEntry(
                row.getInt("id"),
                row.getObject("local_id", Integer.class),
                row.getObject("partner_id", Integer.class),
                row.getInt("position"),
                row.getInt("rank"),
                row.getObject("score", Double.class),
                row.get("joined_at", INSTANT_TIMESTAMP));
    }
}
