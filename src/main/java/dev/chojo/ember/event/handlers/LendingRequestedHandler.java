/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.LendingRequested;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class LendingRequestedHandler implements DomainEventHandler<LendingRequested> {
    private final NotificationService notificationService;

    @Inject
    public LendingRequestedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<LendingRequested> eventType() {
        return LendingRequested.class;
    }

    @Override
    public void handle(LendingRequested event) {
        notificationService.notifyMembersWithRole(
                event.owningStationId(),
                "INVENTORY_MANAGER",
                NotificationType.LENDING_NEW_REQUEST,
                NotificationData.of(
                        new NotificationParams.LendingNewRequest(event.requestingStationName(), event.itemSummary()),
                        new NotificationData.NotificationLink(
                                "inventory-lending-detail", Map.of("id", event.requestId()))));
    }
}
