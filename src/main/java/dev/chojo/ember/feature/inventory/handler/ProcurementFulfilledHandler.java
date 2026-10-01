/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcurementFulfilled;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class ProcurementFulfilledHandler implements DomainEventHandler<ProcurementFulfilled> {
    private final Notifier notifier;

    @Inject
    public ProcurementFulfilledHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<ProcurementFulfilled> eventType() {
        return ProcurementFulfilled.class;
    }

    @Override
    public void handle(ProcurementFulfilled event) {
        notifier.notify(
                StationAudience.member(event.memberId()),
                NotificationType.PROCUREMENT_FULFILLED,
                NotificationData.of(
                        new NotificationParams.ProcurementFulfilled(event.inventoryName()),
                        new NotificationData.NotificationLink(
                                "inventory-procurement", Map.of("id", event.inventoryId()))),
                Delivery.EVERY_TIME);
    }
}
