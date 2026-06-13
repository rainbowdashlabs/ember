/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcurementCreated;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class ProcurementCreatedHandler implements DomainEventHandler<ProcurementCreated> {
    private final NotificationService notificationService;

    @Inject
    public ProcurementCreatedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<ProcurementCreated> eventType() {
        return ProcurementCreated.class;
    }

    @Override
    public void handle(ProcurementCreated event) {
        notificationService.notify(
                event.memberId(),
                NotificationType.PROCUREMENT_REQUESTED,
                NotificationData.of(
                        new NotificationParams.ProcurementRequested(event.inventoryName()),
                        // Land on the procurement page with the inventory id so the feed renderer
                        // can enrich with the inventory's type / flow.
                        new NotificationData.NotificationLink(
                                "inventory-procurement", Map.of("id", event.inventoryId()))));
    }
}
