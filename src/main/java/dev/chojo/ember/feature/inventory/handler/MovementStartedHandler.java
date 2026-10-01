/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementStarted;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells whoever's turn it is that a movement has started, which is usually the station but is
 * whichever party the flow's second step belongs to.
 */
@Singleton
public class MovementStartedHandler implements DomainEventHandler<MovementStarted> {
    private final Notifier notifier;

    @Inject
    public MovementStartedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<MovementStarted> eventType() {
        return MovementStarted.class;
    }

    @Override
    public void handle(MovementStarted event) {
        MovementNotificationRouting.tell(
                notifier,
                MovementNotificationRouting.nextParty(
                        event.stationId(), event.memberId(), event.nextActor(), event.ownerClusterId()),
                event.actorMemberId(),
                NotificationType.MOVEMENT_RAISED,
                new NotificationParams.MovementRaised(event.memberName(), event.inventoryName(), event.reason()),
                event.movementId());
    }
}
