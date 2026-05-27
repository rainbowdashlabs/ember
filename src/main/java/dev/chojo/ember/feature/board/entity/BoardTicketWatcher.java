/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardTicketWatcher(int ticketId, int memberId) {

    public static RowMapping<BoardTicketWatcher> map() {
        return row -> new BoardTicketWatcher(row.getInt("ticket_id"), row.getInt("member_id"));
    }
}
