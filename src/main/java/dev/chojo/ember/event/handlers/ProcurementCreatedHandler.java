/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcurementCreated;
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
public class ProcurementCreatedHandler implements DomainEventHandler<ProcurementCreated> {
    private final Notifier notifier;

    @Inject
    public ProcurementCreatedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<ProcurementCreated> eventType() {
        return ProcurementCreated.class;
    }

    @Override
    public void handle(ProcurementCreated event) {
        notifier.notify(
                StationAudience.member(event.memberId()),
                NotificationType.PROCUREMENT_REQUESTED,
                NotificationData.of(
                        new NotificationParams.ProcurementRequested(event.inventoryName()),
                        new NotificationData.NotificationLink(
                                "inventory-procurement", Map.of("id", event.inventoryId()))),
                Delivery.EVERY_TIME);
    }
}
