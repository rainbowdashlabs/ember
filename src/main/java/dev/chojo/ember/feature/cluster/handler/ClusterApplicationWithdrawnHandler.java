/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.handler;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterApplicationWithdrawn;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells the cluster that a request it was about to answer is gone.
 */
@Singleton
public class ClusterApplicationWithdrawnHandler implements DomainEventHandler<ClusterApplicationWithdrawn> {
    private final Notifier notifier;

    @Inject
    public ClusterApplicationWithdrawnHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<ClusterApplicationWithdrawn> eventType() {
        return ClusterApplicationWithdrawn.class;
    }

    @Override
    public void handle(ClusterApplicationWithdrawn event) {
        var data = NotificationData.of(
                new NotificationParams.ClusterApplicationWithdrawn(event.stationName()),
                new NotificationData.NotificationLink("cluster-applications"));
        notifier.notify(
                ClusterAudience.holders(event.clusterId(), ClusterPermission.CLUSTER_STATIONS),
                NotificationType.CLUSTER_APPLICATION_WITHDRAWN,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
