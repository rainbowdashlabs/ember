/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * What a manager sets on an event group besides its sets, parts and audiences.
 *
 * @param name                 the event group name
 * @param description          what it is about, as Markdown
 * @param registrationLimit    how many people it takes, or null for no limit
 * @param registrationDeadline when sign-ups close: fixed, or relative to the start of the first part;
 *                             null reads as no deadline
 * @param requiresConfirmation whether a manager confirms each sign-up
 * @param color                the colour that marks its parts in lists (#RRGGBB), or null for one
 *                             picked by its id
 * @param minRegistrations     how many sign-ups must be accepted for it to take place, or null for no
 *                             minimum
 * @param thresholdDays        how many days before its first part the minimum must be reached, given
 *                             together with the minimum
 * @param partRegistrationLimit how many places each part has where it has no limit of its own, or
 *                             null for no limit
 * @param queueOrder           how its waiting list is ordered; null reads as by time
 */
public record EventGroupDetails(
        String name,
        @Nullable String description,
        @Nullable Integer registrationLimit,
        @Nullable RegistrationDeadline registrationDeadline,
        boolean requiresConfirmation,
        @Nullable String color,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays,
        @Nullable Integer partRegistrationLimit,
        @Nullable QueueOrder queueOrder) {

    /** Reads a missing deadline as none. */
    public EventGroupDetails {
        if (registrationDeadline == null) registrationDeadline = RegistrationDeadline.none();
    }

    /**
     * Details without places per part, kept by time.
     *
     * @param name                 the event group name
     * @param description          what it is about, as Markdown
     * @param registrationLimit    how many people it takes, or null for no limit
     * @param registrationDeadline when sign-ups close: fixed, or relative to the start of the first part;
     *                             null reads as no deadline
     * @param requiresConfirmation whether a manager confirms each sign-up
     * @param color                the colour that marks its parts, or null
     * @param minRegistrations     how many sign-ups must be accepted, or null
     * @param thresholdDays        how many days before its first part the minimum must be reached
     */
    public EventGroupDetails(
            String name,
            @Nullable String description,
            @Nullable Integer registrationLimit,
            @Nullable RegistrationDeadline registrationDeadline,
            boolean requiresConfirmation,
            @Nullable String color,
            @Nullable Integer minRegistrations,
            @Nullable Integer thresholdDays) {
        this(
                name,
                description,
                registrationLimit,
                registrationDeadline,
                requiresConfirmation,
                color,
                minRegistrations,
                thresholdDays,
                null,
                null);
    }

    /**
     * The order of the waiting list, by time where none was given.
     *
     * @return the order
     */
    public QueueOrder order() {
        return queueOrder == null ? QueueOrder.TIME : queueOrder;
    }

    /**
     * Details without a minimum, which is what most event groups carry.
     *
     * @param name                 the event group name
     * @param description          what it is about, as Markdown
     * @param registrationLimit    how many people it takes, or null for no limit
     * @param registrationDeadline when sign-ups close: fixed, or relative to the start of the first part;
     *                             null reads as no deadline
     * @param requiresConfirmation whether a manager confirms each sign-up
     * @param color                the colour that marks its parts, or null
     */
    public EventGroupDetails(
            String name,
            @Nullable String description,
            @Nullable Integer registrationLimit,
            @Nullable RegistrationDeadline registrationDeadline,
            boolean requiresConfirmation,
            @Nullable String color) {
        this(name, description, registrationLimit, registrationDeadline, requiresConfirmation, color, null, null);
    }
}
