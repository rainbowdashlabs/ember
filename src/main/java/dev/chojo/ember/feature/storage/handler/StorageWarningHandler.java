/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.StorageWarningEvent;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.util.SizeParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class StorageWarningHandler implements DomainEventHandler<StorageWarningEvent> {
    private final Notifier notifier;

    @Inject
    public StorageWarningHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<StorageWarningEvent> eventType() {
        return StorageWarningEvent.class;
    }

    @Override
    public void handle(StorageWarningEvent event) {
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.STATION_MANAGER),
                NotificationType.STORAGE_WARNING,
                NotificationData.of(
                        new NotificationParams.StorageWarning(
                                event.usedPercent(),
                                SizeParser.formatBytes(event.usedBytes()),
                                SizeParser.formatBytes(event.quotaBytes())),
                        new NotificationData.NotificationLink(
                                "station-settings", Map.of("stationId", event.stationId()))),
                Delivery.EVERY_TIME);
    }
}
