/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcurementFulfilled;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProcurementFulfilledHandler implements DomainEventHandler<ProcurementFulfilled> {
    private final NotificationService notificationService;

    @Inject
    public ProcurementFulfilledHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<ProcurementFulfilled> eventType() {
        return ProcurementFulfilled.class;
    }

    @Override
    public void handle(ProcurementFulfilled event) {
        notificationService.notify(
                event.memberId(),
                NotificationType.PROCUREMENT_FULFILLED,
                NotificationData.of(
                        new NotificationParams.ProcurementFulfilled(event.inventoryName()),
                        new NotificationData.NotificationLink("dashboard-overview")));
    }
}
