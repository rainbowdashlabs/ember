/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An event group as lists show it.
 *
 * @param id                   the event group
 * @param name                 its name
 * @param description          what it is about, as Markdown
 * @param registrationLimit    how many people it takes, or null
 * @param registrationDeadline when sign-ups close, or null
 * @param requiresConfirmation whether a manager confirms each sign-up
 * @param cancelled            whether it was called off
 * @param cancelReason         why, where it was
 * @param partCount            how many parts it has
 * @param holding              how many sign-ups hold or wait for a place
 * @param color                the colour that marks its parts (#RRGGBB), or null for one picked by its id
 * @param minRegistrations     how many sign-ups must be accepted for it to take place, or null
 * @param thresholdDays        how many days before its first part the minimum must be reached, or null
 */
public record EventGroupSummary(
        int id,
        String name,
        @Nullable String description,
        @Nullable Integer registrationLimit,
        @Nullable Instant registrationDeadline,
        boolean requiresConfirmation,
        boolean cancelled,
        @Nullable String cancelReason,
        int partCount,
        int holding,
        @Nullable String color,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays) {
    public static EventGroupSummary of(EventGroup eventGroup, EventGroupTally tally) {
        return new EventGroupSummary(
                eventGroup.id(),
                eventGroup.name(),
                eventGroup.description(),
                eventGroup.registrationLimit(),
                eventGroup.registrationDeadline(),
                eventGroup.requiresConfirmation(),
                eventGroup.cancelled(),
                eventGroup.cancelReason(),
                tally.partCount(),
                tally.holding(),
                eventGroup.color(),
                eventGroup.minRegistrations(),
                eventGroup.thresholdDays());
    }
}
