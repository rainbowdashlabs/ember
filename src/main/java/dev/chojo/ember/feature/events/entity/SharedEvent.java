/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * An appointment as one station shows it to another.
 *
 * <p>One shape for all three ways it travels: served to a partner, fetched from a partner over
 * HTTP, and handed over directly between two stations on the same instance. The three used to be
 * three identical records, so anything added to what a shared appointment says had to be added
 * three times, and forgetting one of them left the same appointment reading differently depending
 * on where the partner lives.
 *
 * @param dayOfWeek   the ISO day of week, or 0 where the appointment does not repeat on one
 * @param repeatUntil the last day the repetition reaches, or null where it has no end
 * @param repeatCount how many times it takes place in total, or null where it has no end
 */
public record SharedEvent(
        int id,
        String name,
        String description,
        StationEvent.EventType eventType,
        int dayOfWeek,
        String startTime,
        String endTime,
        boolean requiresRegistration,
        boolean requiresConfirmation,
        @Nullable LocalDate repeatUntil,
        @Nullable Integer repeatCount) {

    /** What of an appointment a partner is shown. */
    public static SharedEvent of(StationEvent event) {
        return new SharedEvent(
                event.id(),
                event.name(),
                event.description() != null ? event.description() : "",
                event.eventType(),
                event.dayOfWeek() != null ? event.dayOfWeek() : 0,
                event.startTime() != null ? event.startTime().toString() : "",
                event.endTime() != null ? event.endTime().toString() : "",
                event.requiresRegistration(),
                true,
                event.repeatUntil(),
                event.repeatCount());
    }
}
