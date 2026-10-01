/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterQuotaChanged;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.util.SizeParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Tells a station's managers that its cluster has changed how much room it has.
 */
@Singleton
public class ClusterQuotaChangedHandler implements DomainEventHandler<ClusterQuotaChanged> {
    private final Notifier notifier;

    @Inject
    public ClusterQuotaChangedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<ClusterQuotaChanged> eventType() {
        return ClusterQuotaChanged.class;
    }

    @Override
    public void handle(ClusterQuotaChanged event) {
        var data = NotificationData.of(
                new NotificationParams.ClusterQuotaChanged(event.clusterName(), formatQuota(event.quotaBytes())),
                new NotificationData.NotificationLink("station-storage"));
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.STATION_MANAGER),
                NotificationType.CLUSTER_QUOTA_CHANGED,
                data,
                Delivery.EVERY_TIME);
    }

    /**
     * A quota handed back to the instance default has no number to show, so it says so in words.
     */
    private static String formatQuota(@Nullable Long quotaBytes) {
        return quotaBytes == null ? "-" : SizeParser.formatBytes(quotaBytes);
    }
}
