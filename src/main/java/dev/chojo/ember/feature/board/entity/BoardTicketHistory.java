/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record BoardTicketHistory(
        int id, int ticketId, String action, String detail, int actorMemberId, Instant createdAt) {

    public static RowMapping<BoardTicketHistory> map() {
        return row -> new BoardTicketHistory(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getString("action"),
                row.getString("detail"),
                row.getInt("actor_member_id"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
