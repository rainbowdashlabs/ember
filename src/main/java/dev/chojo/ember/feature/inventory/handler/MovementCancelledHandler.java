/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementCancelled;
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
 * Tells everybody who was part of a movement that it was called off, except whoever called it off.
 *
 * <p>The one who pressed it knows. The others do not, and for them it is the end of something they
 * were waiting on: a member who was promised a replacement, a station that had planned around the
 * piece coming back. The message names the piece and says where it stayed, because a movement called
 * off during the post does not bring anything home.
 */
@Singleton
public class MovementCancelledHandler implements DomainEventHandler<MovementCancelled> {
    private final Notifier notifier;

    @Inject
    public MovementCancelledHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<MovementCancelled> eventType() {
        return MovementCancelled.class;
    }

    @Override
    public void handle(MovementCancelled event) {
        var data = NotificationData.of(
                new NotificationParams.MovementCancelled(
                        event.inventoryName(), event.itemName(), event.reason(), event.itemStayedAway()),
                new NotificationData.NotificationLink("inventory-movement-detail", Map.of("id", event.movementId())));
        var everybody = MovementNotificationRouting.stationTeam(event.stationId())
                .and(StationAudience.members(event.memberId() != null ? List.of(event.memberId()) : List.of()));
        notifier.notify(
                everybody.except(event.actorMemberId()),
                NotificationType.MOVEMENT_CANCELLED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
