/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionSelection;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A batch of one-time appointments and everything they share.
 *
 * @param registrationDeadline            a deadline every appointment of the batch shares; it wins over
 *                                        {@code registrationDeadlineOffsetHours}
 * @param registrationLimit               the places of each appointment, or none for no limit
 * @param minRegistrations                the sign-ups an appointment needs to take place, or none
 * @param thresholdDays                   how many days before its start the minimum has to be reached
 * @param queueOrder                      how the waiting list of each appointment is ordered, or none for
 *                                        the order of sign-up
 * @param registrationDeadlineOffsetHours a deadline as hours before each appointment's own start, read
 *                                        where the batch names no deadline of its own
 * @param reminderDays                    the days before each appointment its reminders go out
 * @param eventTemplateId                 the appointment template the batch is made from; each appointment
 *                                        takes its questions, documents to bring, files and, on the same
 *                                        attendance sheet, its field starting values
 */
public record BatchRequest(
        @Nullable String name,
        @Nullable String description,
        @Nullable Integer templateId,
        @Nullable Integer categoryId,
        @Nullable List<BatchFieldEntry> inlineFields,
        List<BatchRow> rows,
        @Nullable Boolean requiresRegistration,
        @Nullable Boolean requiresConfirmation,
        @Nullable Instant registrationDeadline,
        RestrictionSelection restriction,
        RestrictionSelection viewRestriction,
        @Nullable Integer registrationLimit,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays,
        @Nullable QueueOrder queueOrder,
        @Nullable Integer registrationDeadlineOffsetHours,
        List<Integer> reminderDays,
        @Nullable Integer eventTemplateId) {

    /** A batch that takes nothing from a template and sets no places, minimum, waiting list order or reminders. */
    public BatchRequest(
            @Nullable String name,
            @Nullable String description,
            @Nullable Integer templateId,
            @Nullable Integer categoryId,
            @Nullable List<BatchFieldEntry> inlineFields,
            List<BatchRow> rows,
            @Nullable Boolean requiresRegistration,
            @Nullable Boolean requiresConfirmation,
            @Nullable Instant registrationDeadline,
            RestrictionSelection restriction,
            RestrictionSelection viewRestriction) {
        this(
                name,
                description,
                templateId,
                categoryId,
                inlineFields,
                rows,
                requiresRegistration,
                requiresConfirmation,
                registrationDeadline,
                restriction,
                viewRestriction,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null);
    }

    /**
     * The deadline of the appointment starting at the given moment: the batch's own, or the given hours
     * before that start, or none where the batch takes no sign-ups or sets neither.
     *
     * @param startTime when the appointment starts
     * @return its deadline, or null for none
     */
    public @Nullable Instant deadlineFor(Instant startTime) {
        if (!Boolean.TRUE.equals(requiresRegistration)) return registrationDeadline;
        if (registrationDeadline != null) return registrationDeadline;
        if (registrationDeadlineOffsetHours == null) return null;
        return startTime.minus(Duration.ofHours(registrationDeadlineOffsetHours));
    }
}
