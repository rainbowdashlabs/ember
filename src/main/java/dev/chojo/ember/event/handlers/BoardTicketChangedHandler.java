/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class BoardTicketChangedHandler implements DomainEventHandler<BoardTicketChanged> {
    private final NotificationService notificationService;

    @Inject
    public BoardTicketChangedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<BoardTicketChanged> eventType() {
        return BoardTicketChanged.class;
    }

    @Override
    public void handle(BoardTicketChanged event) {
        var data = NotificationData.of(
                new NotificationParams.BoardTicketUpdate(
                        event.boardName(), event.ticketKey(), event.changeDescription()),
                new NotificationData.NotificationLink(
                        "ticket-detail", Map.of("boardId", event.boardId(), "ticketId", event.ticketId())));

        notificationService.notifyMembersIfAbsent(
                event.watcherMemberIds(),
                NotificationType.BOARD_TICKET_UPDATE,
                data,
                event.actorMemberId() != null ? event.actorMemberId() : -1);
    }
}
