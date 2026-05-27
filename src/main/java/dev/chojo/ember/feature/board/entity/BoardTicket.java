/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record BoardTicket(
        int id,
        int boardId,
        int laneId,
        int ticketNumber,
        String title,
        String description,
        Integer assignedMemberId,
        TicketPriority priority,
        LocalDate dueDate,
        int position,
        int createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant laneEnteredAt,
        int checklistTotal,
        int checklistChecked,
        int attachmentCount) {

    public static RowMapping<BoardTicket> map() {
        return row -> new BoardTicket(
                row.getInt("id"),
                row.getInt("board_id"),
                row.getInt("lane_id"),
                row.getInt("ticket_number"),
                row.getString("title"),
                row.getString("description"),
                row.getObject("assigned_member_id", Integer.class),
                TicketPriority.valueOf(row.getString("priority")),
                row.getObject("due_date", LocalDate.class),
                row.getInt("position"),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.get("lane_entered_at", INSTANT_TIMESTAMP),
                row.getInt("checklist_total"),
                row.getInt("checklist_checked"),
                row.getInt("attachment_count"));
    }
}
