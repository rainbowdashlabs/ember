/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementStarted;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Tells whoever's turn it is that a movement has started, which is usually the station but is
 * whichever party the flow's second step belongs to.
 */
@Singleton
public class MovementStartedHandler implements DomainEventHandler<MovementStarted> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public MovementStartedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<MovementStarted> eventType() {
        return MovementStarted.class;
    }

    @Override
    public void handle(MovementStarted event) {
        var data = NotificationData.of(
                new NotificationParams.ExchangeNewRequest(event.memberName(), event.inventoryName(), event.reason()),
                new NotificationData.NotificationLink("inventory-movement-detail", Map.of("id", event.movementId())));
        var recipients = MovementNotificationRouting.recipients(
                stationMemberRepository, event.stationId(), event.memberId(), event.nextActor());
        notificationService.notifyMembersIfAbsent(
                recipients, NotificationType.EXCHANGE_NEW_REQUEST, data, event.actorMemberId());
    }
}
