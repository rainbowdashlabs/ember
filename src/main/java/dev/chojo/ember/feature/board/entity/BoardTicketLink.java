/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardTicketLink(int ticketId, int linkedTicketId, LinkType linkType) {

    public static RowMapping<BoardTicketLink> map() {
        return row -> new BoardTicketLink(
                row.getInt("ticket_id"), row.getInt("linked_ticket_id"), row.getEnum("link_type", LinkType.class));
    }
}
