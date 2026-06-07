/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardChecklistItem(int id, int ticketId, String title, boolean checked, int position) {

    public static RowMapping<BoardChecklistItem> map() {
        return row -> new BoardChecklistItem(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getString("title"),
                row.getBoolean("checked"),
                row.getInt("position"));
    }
}
