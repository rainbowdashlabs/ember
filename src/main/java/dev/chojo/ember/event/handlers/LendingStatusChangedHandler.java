/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.LendingStatusChanged;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class LendingStatusChangedHandler implements DomainEventHandler<LendingStatusChanged> {
    private final NotificationService notificationService;

    @Inject
    public LendingStatusChangedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<LendingStatusChanged> eventType() {
        return LendingStatusChanged.class;
    }

    @Override
    public void handle(LendingStatusChanged event) {
        var data = NotificationData.of(
                new NotificationParams.LendingStatusChange(event.stationName(), event.status()),
                new NotificationData.NotificationLink("inventory-lending-detail", Map.of("id", event.requestId())));
        notificationService.notifyMembersWithRole(event.targetStationId(), "INVENTORY_MANAGER", event.type(), data);
    }
}
