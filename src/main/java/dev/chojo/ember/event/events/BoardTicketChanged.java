/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

import java.util.List;

/**
 * Published when a board ticket changes in a way that watchers should be notified about.
 *
 * @param stationId         the station this board belongs to
 * @param boardId           the board ID
 * @param ticketId          the ticket ID (numeric primary key, used by the feed renderer
 *                          for context enrichment)
 * @param boardKey          the board short key (e.g. "DEV", used in the frontend route)
 * @param ticketNumber      the per-board ticket number (used in the frontend route)
 * @param boardName         board display name
 * @param ticketKey         ticket key (e.g. "DEV-42") — display form combining boardKey + number
 * @param changeDescription human-readable change description (e.g. "moved to Erledigt", "comment added")
 * @param actorMemberId     the member who made the change
 * @param watcherMemberIds  the member IDs watching this ticket
 */
public record BoardTicketChanged(
        int stationId,
        int boardId,
        int ticketId,
        String boardKey,
        int ticketNumber,
        String boardName,
        String ticketKey,
        String changeDescription,
        Integer actorMemberId,
        List<Integer> watcherMemberIds)
        implements DomainEvent {}
