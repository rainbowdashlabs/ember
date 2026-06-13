/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventsBatchCreated;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Aggregates a batch event creation into a single station-wide NEW_EVENTS_BATCH notification
 * instead of emitting one NEW_EVENT per row. The preview lists up to three event names.
 */
@Singleton
public class EventsBatchCreatedHandler implements DomainEventHandler<EventsBatchCreated> {
    private static final int PREVIEW_LIMIT = 3;

    private final NotificationService notificationService;

    @Inject
    public EventsBatchCreatedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<EventsBatchCreated> eventType() {
        return EventsBatchCreated.class;
    }

    @Override
    public void handle(EventsBatchCreated event) {
        var events = event.events();
        if (events.isEmpty()) return;

        var preview = new StringBuilder();
        int previewN = Math.min(PREVIEW_LIMIT, events.size());
        for (int i = 0; i < previewN; i++) {
            if (i > 0) preview.append(", ");
            preview.append(events.get(i).name());
        }
        if (events.size() > previewN) {
            preview.append(", …");
        }

        // Earliest start time across the batch — surfaces "starting 15 Sep" in the title so
        // members see when the first occurrence falls without expanding.
        java.time.LocalDate firstEventDate = events.stream()
                .map(dev.chojo.ember.feature.events.entity.StationEvent::startTime)
                .filter(java.util.Objects::nonNull)
                .min(java.time.Instant::compareTo)
                .map(instant -> instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                .orElse(null);

        notificationService.notifyStation(
                event.stationId(),
                NotificationType.NEW_EVENTS_BATCH,
                NotificationData.of(
                        new NotificationParams.NewEventsBatch(events.size(), preview.toString(), firstEventDate),
                        new NotificationData.NotificationLink("events-upcoming", Map.of())));
    }
}
