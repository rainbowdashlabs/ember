/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterModuleDenied;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells the people who run a station that its cluster has switched a module off.
 *
 * <p>Addressed to whoever manages the station's modules rather than to everybody: the page is gone for all of
 * them, but only those people were ever in a position to turn it on, and only they will wonder why they now
 * cannot.
 */
@Singleton
public class ClusterGovernanceHandler implements DomainEventHandler<ClusterModuleDenied> {
    private final NotificationService notificationService;

    @Inject
    public ClusterGovernanceHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<ClusterModuleDenied> eventType() {
        return ClusterModuleDenied.class;
    }

    @Override
    public void handle(ClusterModuleDenied event) {
        var data = NotificationData.of(
                new NotificationParams.ClusterModuleDenied(
                        event.clusterName(), event.module().name()),
                new NotificationData.NotificationLink("station-modules"));
        notificationService.notifyMembersWithRole(
                event.stationId(),
                StationPermission.STATION_MODULES.name(),
                NotificationType.CLUSTER_MODULE_DENIED,
                data);
    }
}
