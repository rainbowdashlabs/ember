/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.Instant;

/**
 * Sparse representation of a station event for list views and federated browse responses.
 * Contains only the fields needed to render an event list row.
 */
public record EventSummary(
        int id,
        int stationId,
        String name,
        String description,
        StationEvent.EventType eventType,
        Integer dayOfWeek,
        Instant startTime,
        Instant endTime,
        boolean requiresRegistration,
        Instant registrationDeadline,
        Integer categoryId,
        Integer templateId,
        boolean restricted) {

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
                event.restricted());
    }
}
