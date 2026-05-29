/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.util.UUID;

public record BoardTicketFederatedAssignee(int ticketId, int partnerId, UUID remoteMemberId) {

    public static RowMapping<BoardTicketFederatedAssignee> map() {
        return row -> new BoardTicketFederatedAssignee(
                row.getInt("ticket_id"),
                row.getInt("partner_id"),
                row.get("remote_member_id", StandardValueConverter.UUID_STRING));
    }
}
