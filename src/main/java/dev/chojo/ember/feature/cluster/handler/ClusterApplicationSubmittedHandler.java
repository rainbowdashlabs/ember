/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.handler;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterApplicationSubmitted;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells the people at the cluster who decide about stations that one is waiting.
 */
@Singleton
public class ClusterApplicationSubmittedHandler implements DomainEventHandler<ClusterApplicationSubmitted> {
    private final Notifier notifier;

    @Inject
    public ClusterApplicationSubmittedHandler(Notifier notifier) {
        this.notifier = notifier;
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
        notifier.notify(
                ClusterAudience.holders(event.clusterId(), ClusterPermission.CLUSTER_STATIONS),
                NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
