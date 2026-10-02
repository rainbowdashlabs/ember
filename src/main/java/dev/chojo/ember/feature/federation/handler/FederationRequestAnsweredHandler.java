/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.FederationRequestAnswered;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Tells whoever manages a station's federation how the station it asked answered. */
@Singleton
public class FederationRequestAnsweredHandler implements DomainEventHandler<FederationRequestAnswered> {
    private final Notifier notifier;

    @Inject
    public FederationRequestAnsweredHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<FederationRequestAnswered> eventType() {
        return FederationRequestAnswered.class;
    }

    @Override
    public void handle(FederationRequestAnswered event) {
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.STATION_FEDERATION),
                event.accepted()
                        ? NotificationType.FEDERATION_REQUEST_ACCEPTED
                        : NotificationType.FEDERATION_REQUEST_DECLINED,
                NotificationData.of(
                        new NotificationParams.FederationRequestAnswered(event.answeringStationName()),
                        NotificationLinks.federation()),
                Delivery.EVERY_TIME);
    }
}
