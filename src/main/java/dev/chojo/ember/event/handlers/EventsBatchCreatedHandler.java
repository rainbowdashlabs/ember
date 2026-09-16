/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventsBatchCreated;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates a batch event creation into a single station-wide NEW_EVENTS_BATCH notification
 * instead of emitting one NEW_EVENT per row. The preview lists up to three event names.
 */
@Singleton
public class EventsBatchCreatedHandler implements DomainEventHandler<EventsBatchCreated> {
    private static final int PREVIEW_LIMIT = 3;

    private final NotificationService notificationService;
    private final StationRepository stationRepository;

    @Inject
    public EventsBatchCreatedHandler(NotificationService notificationService, StationRepository stationRepository) {
        this.notificationService = notificationService;
        this.stationRepository = stationRepository;
    }

    @Override
    public Class<EventsBatchCreated> eventType() {
        return EventsBatchCreated.class;
    }

    /**
     * Announces a batch of new appointments as one entry naming when the first of them falls.
     *
     * <p>Which day that is belongs to the station's clock rather than the server's: an evening just
     * after midnight in Berlin is the previous day read in UTC, and the announcement would name a
     * day nobody is meeting on.
     */
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

        var zone = StationFormat.timezoneOf(
                stationRepository.findById(event.stationId()).orElse(null));
        LocalDate firstEventDate = events.stream()
                .map(StationEvent::startTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .map(instant -> instant.atZone(zone).toLocalDate())
                .orElse(null);

        notificationService.notifyStation(
                event.stationId(),
                NotificationType.NEW_EVENTS_BATCH,
                NotificationData.of(
                        new NotificationParams.NewEventsBatch(events.size(), preview.toString(), firstEventDate),
                        new NotificationData.NotificationLink("events-upcoming", Map.of())));
    }
}
