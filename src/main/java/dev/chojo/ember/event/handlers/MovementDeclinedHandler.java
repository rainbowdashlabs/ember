/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementDeclined;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Map;

/**
 * Tells both ends that a movement was refused.
 *
 * <p>Everybody who was part of it hears, rather than only whoever was next: a refusal ends the chain
 * and puts the item back where it came from, and the member who asked needs to see the reason where
 * they asked.
 */
@Singleton
public class MovementDeclinedHandler implements DomainEventHandler<MovementDeclined> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public MovementDeclinedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<MovementDeclined> eventType() {
        return MovementDeclined.class;
    }

    @Override
    public void handle(MovementDeclined event) {
        var data = NotificationData.of(
                new NotificationParams.MovementDeclined(event.inventoryName(), event.reason()),
                new NotificationData.NotificationLink("inventory-movement-detail", Map.of("id", event.movementId())));
        var recipients =
                new ArrayList<>(MovementNotificationRouting.stationTeam(stationMemberRepository, event.stationId()));
        if (event.memberId() != null) recipients.add(event.memberId());
        notificationService.notifyMembersIfAbsent(
                recipients, NotificationType.MOVEMENT_DECLINED, data, event.actorMemberId());
    }
}
