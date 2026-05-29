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

public record BoardTicketTransition(
        int id,
        int ticketId,
        Integer fromLaneId,
        Integer toLaneId,
        Integer movedBy,
        Integer federatedPartnerId,
        UUID federatedMemberId,
        Instant movedAt) {

    public static RowMapping<BoardTicketTransition> map() {
        return row -> new BoardTicketTransition(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getObject("from_lane_id", Integer.class),
                row.getObject("to_lane_id", Integer.class),
                row.getObject("moved_by", Integer.class),
                row.getObject("federated_partner_id", Integer.class),
                row.get("federated_member_id", StandardValueConverter.UUID_STRING),
                row.get("moved_at", INSTANT_TIMESTAMP));
    }
}
