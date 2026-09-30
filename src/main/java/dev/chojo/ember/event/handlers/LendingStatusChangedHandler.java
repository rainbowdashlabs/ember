/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.LendingStatusChanged;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class LendingStatusChangedHandler implements DomainEventHandler<LendingStatusChanged> {
    private final Notifier notifier;

    @Inject
    public LendingStatusChangedHandler(Notifier notifier) {
        this.notifier = notifier;
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
        notifier.notify(
                StationAudience.holders(event.targetStationId(), StationPermission.INVENTORY_MANAGER),
                event.type(),
                data,
                Delivery.EVERY_TIME);
    }
}
