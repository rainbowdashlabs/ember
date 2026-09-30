/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about a ticket: its key, its board and what changed, and from the ticket
 * itself its title, who has it and how urgent it is.
 */
@Singleton
public class BoardFeedDetails implements FeedDetailsContributor {
    private final BoardTicketService boardTicketService;

    @Inject
    public BoardFeedDetails(BoardTicketService boardTicketService) {
        this.boardTicketService = boardTicketService;
    }

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        if (params
                instanceof
                NotificationParams.BoardTicketUpdate(String boardName, String ticketKey, String changeDescription)) {
            details.putIfPresent(details.label("ticketKey"), ticketKey);
            details.putIfPresent(details.label("board"), boardName);
            details.putSnippetIfPresent(details.label("change", "Change"), changeDescription);
            addTicket(details);
        }
    }

    /**
     * Adds the linked ticket's title, assignee and priority. The ticket is named by the link's
     * {@code ticketId}. A missing id, a deleted ticket or a failed lookup adds nothing.
     */
    private void addTicket(FeedDetails details) {
        Integer ticketId = details.linkParam("ticketId");
        if (ticketId == null) return;
        try {
            var ticket = boardTicketService.findById(ticketId).orElse(null);
            if (ticket == null) return;
            details.putIfPresent(details.label("title", "Title"), ticket.title());
            if (ticket.assignee() != null && ticket.assignee().name() != null) {
                details.put(
                        details.label("assignee", "Assignee"), ticket.assignee().name());
            }
            if (ticket.priority() != null) {
                details.put(
                        details.label("priority", "Priority"),
                        details.localized("ticketPriority", ticket.priority().name(), null));
            }
        } catch (Exception ignored) {
        }
    }
}
