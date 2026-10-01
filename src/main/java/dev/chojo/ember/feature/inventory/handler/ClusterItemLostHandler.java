/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.handler;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterItemLost;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells the cluster that a station has reported a piece of its gear missing.
 *
 * <p>Not when the station marks it lost, which is the station's own business: when the station raises a
 * report and asks for a replacement. That is the point at which there is something for the cluster to do.
 *
 * <p>The station is named, because a cluster's gear is spread over all of them and "a helmet is
 * missing" is not something anybody can act on.
 */
@Singleton
public class ClusterItemLostHandler implements DomainEventHandler<ClusterItemLost> {
    private final Notifier notifier;
    private final StationRepository stationRepository;

    @Inject
    public ClusterItemLostHandler(Notifier notifier, StationRepository stationRepository) {
        this.notifier = notifier;
        this.stationRepository = stationRepository;
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
        notifier.notify(
                ClusterAudience.holders(event.clusterId(), ClusterPermission.CLUSTER_INVENTORY_MANAGER),
                NotificationType.CLUSTER_ITEM_LOST,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
