/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterItemLost;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Tells the cluster that a station cannot find a piece of its gear.
 *
 * <p>The station is named, because a cluster's gear is spread over all of them and "a helmet is
 * missing" is not something anybody can act on.
 */
@Singleton
public class ClusterItemLostHandler implements DomainEventHandler<ClusterItemLost> {
    private final NotificationService notificationService;
    private final StationRepository stationRepository;
    private final Provider<ClusterService> clusterService;

    @Inject
    public ClusterItemLostHandler(
            NotificationService notificationService,
            StationRepository stationRepository,
            Provider<ClusterService> clusterService) {
        this.notificationService = notificationService;
        this.stationRepository = stationRepository;
        this.clusterService = clusterService;
    }

    @Override
    public Class<ClusterItemLost> eventType() {
        return ClusterItemLost.class;
    }

    @Override
    public void handle(ClusterItemLost event) {
        String stationName =
                stationRepository.findById(event.stationId()).map(Station::name).orElse("");
        var data = NotificationData.of(
                new NotificationParams.ClusterItemLost(event.itemName(), stationName),
                new NotificationData.NotificationLink("cluster-inventory"));
        notificationService.notifyClusterMembersIfAbsent(
                clusterService.get().findMemberIdsWith(event.clusterId(), ClusterPermission.CLUSTER_INVENTORY_MANAGER),
                NotificationType.CLUSTER_ITEM_LOST,
                data,
                null);
    }
}
