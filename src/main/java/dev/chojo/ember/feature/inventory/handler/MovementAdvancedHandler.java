/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementAdvanced;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells whoever's turn it is next that a step has been acknowledged.
 *
 * <p>The message goes to that party and to nobody else, so its arrival is itself the signal that
 * something is waiting. When the chain has ended there is no next party, and the member it concerned
 * is told instead, because the last thing they saw was their gear going away.
 */
@Singleton
public class MovementAdvancedHandler implements DomainEventHandler<MovementAdvanced> {
    private final Notifier notifier;

    @Inject
    public MovementAdvancedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<MovementAdvanced> eventType() {
        return MovementAdvanced.class;
    }

    @Override
    public void handle(MovementAdvanced event) {
        MovementNotificationRouting.tell(
                notifier,
                MovementNotificationRouting.nextParty(
                        event.stationId(), event.memberId(), event.nextActor(), event.ownerClusterId()),
                event.actorMemberId(),
                NotificationType.MOVEMENT_ADVANCED,
                new NotificationParams.MovementMoved(event.stepLabel(), event.inventoryName(), event.nextActor()),
                event.movementId());
    }
}
