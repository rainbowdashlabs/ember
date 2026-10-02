/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Sparse representation of a station event for list views and federated browse responses.
 * Contains only the fields needed to render an event list row.
 *
 * <p>{@code seriesCancelled} says that every date is off for good. A single date that is off is told
 * beside the date it belongs to, since the appointment itself goes on.
 */
public record EventSummary(
        int id,
        int stationId,
        String name,
        @Nullable String description,
        StationEvent.EventType eventType,
        @Nullable Integer dayOfWeek,
        Instant startTime,
        Instant endTime,
        boolean requiresRegistration,
        @Nullable Instant registrationDeadline,
        @Nullable Integer categoryId,
        @Nullable Integer templateId,
        boolean restricted,
        boolean seriesCancelled,
        @Nullable Integer registrationLimit) {

    public static EventSummary of(StationEvent event) {
        return new EventSummary(
                event.id(),
                event.stationId(),
                event.name(),
                event.description(),
                event.eventType(),
                event.dayOfWeek(),
                event.startTime(),
                event.endTime(),
                event.requiresRegistration(),
                event.registrationDeadline(),
                event.categoryId(),
                event.templateId(),
                event.restricted(),
                event.cancelled(),
                event.registrationLimit());
    }
}
