/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterApplicationResolved;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Answers the station owner who asked to join a cluster.
 *
 * <p>Only the owner hears. Applying was theirs to do, and a station full of members has no reason to be told
 * about a request most of them never knew was open.
 */
@Singleton
public class ClusterApplicationResolvedHandler implements DomainEventHandler<ClusterApplicationResolved> {
    private final NotificationService notificationService;
    private final StationRepository stationRepository;

    @Inject
    public ClusterApplicationResolvedHandler(
            NotificationService notificationService, StationRepository stationRepository) {
        this.notificationService = notificationService;
        this.stationRepository = stationRepository;
    }

    @Override
    public Class<ClusterApplicationResolved> eventType() {
        return ClusterApplicationResolved.class;
    }

    @Override
    public void handle(ClusterApplicationResolved event) {
        Integer owner = stationRepository
                .findById(event.stationId())
                .map(station -> station.ownerMemberId())
                .orElse(null);
        if (owner == null) return;

        var data = event.approved()
                ? NotificationData.of(
                        new NotificationParams.ClusterApplicationApproved(event.clusterName()),
                        new NotificationData.NotificationLink("station-manage-cluster"))
                : NotificationData.of(
                        new NotificationParams.ClusterApplicationDenied(event.clusterName(), event.reason()),
                        new NotificationData.NotificationLink("station-manage-cluster"));
        var type = event.approved()
                ? NotificationType.CLUSTER_APPLICATION_APPROVED
                : NotificationType.CLUSTER_APPLICATION_DENIED;
        notificationService.notifyIfAbsent(owner, type, data);
    }
}
