/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record BoardTicketTransition(
        int id, int ticketId, Integer fromLaneId, Integer toLaneId, int movedBy, Instant movedAt) {

    public static RowMapping<BoardTicketTransition> map() {
        return row -> new BoardTicketTransition(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getObject("from_lane_id", Integer.class),
                row.getObject("to_lane_id", Integer.class),
                row.getInt("moved_by"),
                row.get("moved_at", INSTANT_TIMESTAMP));
    }
}
