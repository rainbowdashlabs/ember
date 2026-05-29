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

public record BoardTicketHistory(
        int id,
        int ticketId,
        String action,
        String detail,
        Integer actorMemberId,
        Integer federatedPartnerId,
        UUID federatedMemberId,
        Instant createdAt) {

    public static RowMapping<BoardTicketHistory> map() {
        return row -> new BoardTicketHistory(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getString("action"),
                row.getString("detail"),
                row.getObject("actor_member_id", Integer.class),
                row.getObject("federated_partner_id", Integer.class),
                row.get("federated_member_id", StandardValueConverter.UUID_STRING),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
