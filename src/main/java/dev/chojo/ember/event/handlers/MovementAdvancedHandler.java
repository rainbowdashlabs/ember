/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementAdvanced;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Tells whoever's turn it is next that a step has been acknowledged.
 *
 * <p>The message goes to that party and to nobody else, so its arrival is itself the signal that
 * something is waiting. When the chain has ended there is no next party, and the member it concerned
 * is told instead, because the last thing they saw was their gear going away.
 */
@Singleton
public class MovementAdvancedHandler implements DomainEventHandler<MovementAdvanced> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public MovementAdvancedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<MovementAdvanced> eventType() {
        return MovementAdvanced.class;
    }

    @Override
    public void handle(MovementAdvanced event) {
        var data = NotificationData.of(
                new NotificationParams.ExchangeStatusChange(
                        event.stepLabel(), event.inventoryName(), event.nextActor()),
                new NotificationData.NotificationLink("inventory-movement-detail", Map.of("id", event.movementId())));
        var recipients = MovementNotificationRouting.recipients(
                stationMemberRepository, event.stationId(), event.memberId(), event.nextActor());
        notificationService.notifyMembersIfAbsent(
                recipients, NotificationType.EXCHANGE_STATUS_CHANGE, data, event.actorMemberId());
    }
}
