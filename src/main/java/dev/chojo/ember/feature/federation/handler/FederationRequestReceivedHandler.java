/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FederationRequestReceived;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Tells whoever manages a station's federation that another station asked to become its partner. */
@Singleton
public class FederationRequestReceivedHandler implements DomainEventHandler<FederationRequestReceived> {
    private final Notifier notifier;

    @Inject
    public FederationRequestReceivedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<FederationRequestReceived> eventType() {
        return FederationRequestReceived.class;
    }

    @Override
    public void handle(FederationRequestReceived event) {
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.STATION_FEDERATION),
                NotificationType.FEDERATION_REQUEST_RECEIVED,
                NotificationData.of(
                        new NotificationParams.FederationRequestReceived(event.requestingStationName()),
                        NotificationLinks.federation()),
                Delivery.EVERY_TIME);
    }
}
