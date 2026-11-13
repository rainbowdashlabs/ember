/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;

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
 * @param eventGroup     the event group it is a part of, or null for an appointment of its own; a partner
 *                    that has never heard of event groups ignores it
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
        @Nullable Integer repeatCount,
        @Nullable EventGroupPartSummary eventGroup) {

    /** A shared appointment that is a part of no event group. */
    public SharedEvent(
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
        this(
                id,
                name,
                description,
                eventType,
                dayOfWeek,
                startTime,
                endTime,
                requiresRegistration,
                requiresConfirmation,
                repeatUntil,
                repeatCount,
                null);
    }

    /**
     * This appointment as a part of the given event group.
     *
     * @param part the event group and set it belongs to, or null for an appointment of its own
     * @return the appointment naming its event group
     */
    public SharedEvent withEventGroup(@Nullable EventGroupPartSummary part) {
        return new SharedEvent(
                id,
                name,
                description,
                eventType,
                dayOfWeek,
                startTime,
                endTime,
                requiresRegistration,
                requiresConfirmation,
                repeatUntil,
                repeatCount,
                part);
    }

    /** What of an appointment a partner is shown. */
    public static SharedEvent of(StationEvent event) {
        return new SharedEvent(
                event.id(),
                event.name(),
                Objects.requireNonNullElse(event.description(), ""),
                event.eventType(),
                Objects.requireNonNullElse(event.dayOfWeek(), 0),
                event.startTime() != null ? event.startTime().toString() : "",
                event.endTime() != null ? event.endTime().toString() : "",
                event.requiresRegistration(),
                true,
                event.repeatUntil(),
                event.repeatCount());
    }
}
