/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BoardTicketChanged;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks for board tickets with due dates that are today or overdue.
 * Sends a daily reminder notification to the assignee if the ticket is not in the last lane.
 */
@Singleton
public class DueDateReminderChecker {
    private static final Logger log = LoggerFactory.getLogger(DueDateReminderChecker.class);

    private final DomainEventBus eventBus;

    @Inject
    public DueDateReminderChecker(DomainEventBus eventBus) {
        this.eventBus = eventBus;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "board-due-date-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::check, 1, 60, TimeUnit.MINUTES);
    }

    private void check() {
        try {
            var overdue = findOverdueTickets();
            for (var ticket : overdue) {
                eventBus.publish(new BoardTicketChanged(
                        ticket.stationId,
                        ticket.boardId,
                        ticket.ticketId,
                        ticket.boardName,
                        ticket.ticketKey,
                        "Fällig: " + ticket.dueDate,
                        ticket.assignedMemberId,
                        List.of(ticket.assignedMemberId)));
            }
            if (!overdue.isEmpty()) {
                log.info("Sent {} due date reminders", overdue.size());
            }
        } catch (Exception e) {
            log.error("Error checking board ticket due dates", e);
        }
    }

    private record OverdueTicket(
            int stationId,
            int boardId,
            int ticketId,
            String boardName,
            String ticketKey,
            String dueDate,
            int assignedMemberId) {}

    private List<OverdueTicket> findOverdueTickets() {
        return Query.query("""
                        SELECT b.station_id, b.id AS board_id, t.id AS ticket_id,
                               b.name AS board_name, b.short_key || '-' || t.ticket_number AS ticket_key,
                               t.due_date::text AS due_date, t.assigned_member_id
                        FROM board_ticket t
                        JOIN board b ON b.id = t.board_id
                        WHERE t.due_date IS NOT NULL
                          AND t.due_date <= CURRENT_DATE
                          AND t.assigned_member_id IS NOT NULL
                          AND t.lane_id != (
                              SELECT bl.id FROM board_lane bl
                              WHERE bl.board_id = t.board_id
                              ORDER BY bl.position DESC LIMIT 1
                          );""")
                .single(Call.of())
                .map(row -> new OverdueTicket(
                        row.getInt("station_id"),
                        row.getInt("board_id"),
                        row.getInt("ticket_id"),
                        row.getString("board_name"),
                        row.getString("ticket_key"),
                        row.getString("due_date"),
                        row.getInt("assigned_member_id")))
                .all();
    }
}
