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

public record BoardTicket(
        int id,
        int boardId,
        int laneId,
        int ticketNumber,
        String title,
        String description,
        MemberIdentity assignee,
        TicketPriority priority,
        LocalDate dueDate,
        int position,
        MemberIdentity creator,
        Instant createdAt,
        Instant updatedAt,
        Instant laneEnteredAt,
        int checklistTotal,
        int checklistChecked,
        int attachmentCount) {

    public BoardTicket withIdentities(MemberIdentity assignee, MemberIdentity creator) {
        return new BoardTicket(
                id,
                boardId,
                laneId,
                ticketNumber,
                title,
                description,
                assignee,
                priority,
                dueDate,
                position,
                creator,
                createdAt,
                updatedAt,
                laneEnteredAt,
                checklistTotal,
                checklistChecked,
                attachmentCount);
    }

    public static RowMapping<BoardTicket> map() {
        return row -> {
            UUID assigneeStationUid = row.get("assignee_station_uid", StandardValueConverter.UUID_STRING);
            UUID assigneeMemberUid = row.get("assignee_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity assignee = (assigneeStationUid != null && assigneeMemberUid != null)
                    ? new MemberIdentity(assigneeStationUid, assigneeMemberUid)
                    : null;

            UUID creatorStationUid = row.get("creator_station_uid", StandardValueConverter.UUID_STRING);
            UUID creatorMemberUid = row.get("creator_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity creator = (creatorStationUid != null && creatorMemberUid != null)
                    ? new MemberIdentity(creatorStationUid, creatorMemberUid)
                    : null;

            return new BoardTicket(
                    row.getInt("id"),
                    row.getInt("board_id"),
                    row.getInt("lane_id"),
                    row.getInt("ticket_number"),
                    row.getString("title"),
                    row.getString("description"),
                    assignee,
                    TicketPriority.valueOf(row.getString("priority")),
                    row.getObject("due_date", LocalDate.class),
                    row.getInt("position"),
                    creator,
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("updated_at", INSTANT_TIMESTAMP),
                    row.get("lane_entered_at", INSTANT_TIMESTAMP),
                    row.getInt("checklist_total"),
                    row.getInt("checklist_checked"),
                    row.getInt("attachment_count"));
        };
    }
}
