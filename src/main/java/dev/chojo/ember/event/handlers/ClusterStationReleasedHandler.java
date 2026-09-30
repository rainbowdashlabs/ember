/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterStationReleased;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells a station's owner that the cluster has let them go.
 */
@Singleton
public class ClusterStationReleasedHandler implements DomainEventHandler<ClusterStationReleased> {
    private final Notifier notifier;
    private final StationRepository stationRepository;

    @Inject
    public ClusterStationReleasedHandler(Notifier notifier, StationRepository stationRepository) {
        this.notifier = notifier;
        this.stationRepository = stationRepository;
    }

    @Override
    public Class<ClusterStationReleased> eventType() {
        return ClusterStationReleased.class;
    }

    @Override
    public void handle(ClusterStationReleased event) {
        Integer owner = stationRepository
                .findById(event.stationId())
                .map(station -> station.ownerMemberId())
                .orElse(null);
        if (owner == null) return;

        var data = NotificationData.of(
                new NotificationParams.ClusterStationReleased(event.clusterName()),
                new NotificationData.NotificationLink("station-manage-cluster"));
        notifier.notify(
                StationAudience.member(owner),
                NotificationType.CLUSTER_STATION_RELEASED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
