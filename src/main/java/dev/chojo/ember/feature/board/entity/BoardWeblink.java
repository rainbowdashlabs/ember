/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardWeblink(int id, int ticketId, String url, String title, int position) {

    public static RowMapping<BoardWeblink> map() {
        return row -> new BoardWeblink(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getString("url"),
                row.getString("title"),
                row.getInt("position"));
    }
}
