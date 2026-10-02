/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.LendingRequested;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class LendingRequestedHandler implements DomainEventHandler<LendingRequested> {
    private final Notifier notifier;

    @Inject
    public LendingRequestedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<LendingRequested> eventType() {
        return LendingRequested.class;
    }

    @Override
    public void handle(LendingRequested event) {
        notifier.notify(
                StationAudience.holders(event.owningStationId(), StationPermission.INVENTORY_MANAGER),
                NotificationType.LENDING_NEW_REQUEST,
                NotificationData.of(
                        new NotificationParams.LendingNewRequest(event.requestingStationName(), event.itemSummary()),
                        NotificationLinks.lendingRequest(event.requestId())),
                Delivery.EVERY_TIME);
    }
}
