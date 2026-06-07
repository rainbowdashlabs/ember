/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardTicketKbLink(int id, int ticketId, int kbFileId, String title, String folderPath) {

    public static RowMapping<BoardTicketKbLink> map() {
        return row -> new BoardTicketKbLink(
                row.getInt("id"),
                row.getInt("ticket_id"),
                row.getInt("kb_file_id"),
                row.getString("title"),
                row.getString("folder_path"));
    }
}
