/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Sparse representation of a board ticket for kanban tile rendering and federated list responses.
 * Omits the potentially large {@code description} field and internal timestamps.
 */
public record TicketSummary(
        int id,
        int boardId,
        int laneId,
        int ticketNumber,
        String title,
        MemberIdentity assignee,
        TicketPriority priority,
        LocalDate dueDate,
        int position,
        Instant laneEnteredAt,
        int checklistTotal,
        int checklistChecked,
        int attachmentCount) {

    public TicketSummary withAssignee(MemberIdentity assignee) {
        return new TicketSummary(
                id,
                boardId,
                laneId,
                ticketNumber,
                title,
                assignee,
                priority,
                dueDate,
                position,
                laneEnteredAt,
                checklistTotal,
                checklistChecked,
                attachmentCount);
    }

    public static TicketSummary of(BoardTicket ticket) {
        return new TicketSummary(
                ticket.id(),
                ticket.boardId(),
                ticket.laneId(),
                ticket.ticketNumber(),
                ticket.title(),
                ticket.assignee(),
                ticket.priority(),
                ticket.dueDate(),
                ticket.position(),
                ticket.laneEnteredAt(),
                ticket.checklistTotal(),
                ticket.checklistChecked(),
                ticket.attachmentCount());
    }

    public static RowMapping<TicketSummary> map() {
        return row -> {
            UUID assigneeStationUid = row.get("assignee_station_uid", StandardValueConverter.UUID_STRING);
            UUID assigneeMemberUid = row.get("assignee_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity assignee = (assigneeStationUid != null && assigneeMemberUid != null)
                    ? new MemberIdentity(assigneeStationUid, assigneeMemberUid)
                    : null;

            return new TicketSummary(
                    row.getInt("id"),
                    row.getInt("board_id"),
                    row.getInt("lane_id"),
                    row.getInt("ticket_number"),
                    row.getString("title"),
                    assignee,
                    TicketPriority.valueOf(row.getString("priority")),
                    row.getObject("due_date", LocalDate.class),
                    row.getInt("position"),
                    row.get("lane_entered_at", INSTANT_TIMESTAMP),
                    row.getInt("checklist_total"),
                    row.getInt("checklist_checked"),
                    row.getInt("attachment_count"));
        };
    }
}
