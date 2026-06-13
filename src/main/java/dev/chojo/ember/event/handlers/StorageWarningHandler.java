/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.StorageWarningEvent;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.util.SizeParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class StorageWarningHandler implements DomainEventHandler<StorageWarningEvent> {
    private final NotificationService notificationService;

    @Inject
    public StorageWarningHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<StorageWarningEvent> eventType() {
        return StorageWarningEvent.class;
    }

    @Override
    public void handle(StorageWarningEvent event) {
        notificationService.notifyMembersWithRole(
                event.stationId(),
                "STATION_MANAGER",
                NotificationType.STORAGE_WARNING,
                NotificationData.of(
                        new NotificationParams.StorageWarning(
                                event.usedPercent(),
                                SizeParser.formatBytes(event.usedBytes()),
                                SizeParser.formatBytes(event.quotaBytes())),
                        // Carry stationId so the feed renderer can load the per-category
                        // breakdown for the body. The "station-settings" route is parameterless;
                        // adding extra routeParams is a no-op for the deep link.
                        new NotificationData.NotificationLink(
                                "station-settings", Map.of("stationId", event.stationId()))));
    }
}
