/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class BoardTicketChangedHandler implements DomainEventHandler<BoardTicketChanged> {
    private final Notifier notifier;

    @Inject
    public BoardTicketChangedHandler(Notifier notifier) {
        this.notifier = notifier;
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
                NotificationLinks.ticket(
                        new BoardTicketAddress(event.boardKey(), event.ticketNumber()), event.ticketId()));

        notifier.notify(
                StationAudience.members(event.watcherMemberIds()).except(event.actorMemberId()),
                NotificationType.BOARD_TICKET_UPDATE,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
