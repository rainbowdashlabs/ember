/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterStationReleased;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells a station's owner that the cluster has let them go.
 */
@Singleton
public class ClusterStationReleasedHandler implements DomainEventHandler<ClusterStationReleased> {
    private final NotificationService notificationService;
    private final StationRepository stationRepository;

    @Inject
    public ClusterStationReleasedHandler(NotificationService notificationService, StationRepository stationRepository) {
        this.notificationService = notificationService;
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
        notificationService.notifyIfAbsent(owner, NotificationType.CLUSTER_STATION_RELEASED, data);
    }
}
