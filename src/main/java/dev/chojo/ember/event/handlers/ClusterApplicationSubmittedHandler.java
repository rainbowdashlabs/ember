/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterApplicationSubmitted;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells the people at the cluster who decide about stations that one is waiting.
 */
@Singleton
public class ClusterApplicationSubmittedHandler implements DomainEventHandler<ClusterApplicationSubmitted> {
    private final NotificationService notificationService;
    private final ClusterService clusterService;

    @Inject
    public ClusterApplicationSubmittedHandler(NotificationService notificationService, ClusterService clusterService) {
        this.notificationService = notificationService;
        this.clusterService = clusterService;
    }

    @Override
    public Class<ClusterApplicationSubmitted> eventType() {
        return ClusterApplicationSubmitted.class;
    }

    @Override
    public void handle(ClusterApplicationSubmitted event) {
        var data = NotificationData.of(
                new NotificationParams.ClusterApplicationSubmitted(event.stationName()),
                new NotificationData.NotificationLink("cluster-applications"));
        notificationService.notifyClusterMembersIfAbsent(
                clusterService.findMemberIdsWith(event.clusterId(), ClusterPermission.CLUSTER_STATIONS),
                NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                data,
                null);
    }
}
