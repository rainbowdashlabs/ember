/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardTicketFederatedAssignee(int ticketId, int partnerId, String remoteMemberId) {

    public static RowMapping<BoardTicketFederatedAssignee> map() {
        return row -> new BoardTicketFederatedAssignee(
                row.getInt("ticket_id"), row.getInt("partner_id"), row.getString("remote_member_id"));
    }
}
