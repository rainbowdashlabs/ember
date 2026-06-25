/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record Board(
        int id,
        UUID uid,
        int stationId,
        String name,
        String description,
        String shortKey,
        int hideDoneAfterDays,
        int ticketCounter,
        Integer backlogLaneId,
        Instant createdAt) {

    public static RowMapping<Board> map() {
        return row -> new Board(
                row.getInt("id"),
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getString("short_key"),
                row.getInt("hide_done_after_days"),
                row.getInt("ticket_counter"),
                row.getObject("backlog_lane_id", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    public boolean hasBacklog() {
        return backlogLaneId != null;
    }
}
