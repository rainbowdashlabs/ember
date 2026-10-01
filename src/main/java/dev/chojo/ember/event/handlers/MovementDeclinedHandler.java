/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementDeclined;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
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
    private final Notifier notifier;

    @Inject
    public MovementDeclinedHandler(Notifier notifier) {
        this.notifier = notifier;
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
        Integer memberId = event.memberId();
        var everybody = MovementNotificationRouting.stationTeam(event.stationId())
                .and(StationAudience.members(memberId != null ? List.of(memberId) : List.of()));
        notifier.notify(
                everybody.except(event.actorMemberId()),
                NotificationType.MOVEMENT_DECLINED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
