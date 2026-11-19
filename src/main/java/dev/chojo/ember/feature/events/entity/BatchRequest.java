/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionSelection;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A batch of one-time appointments and everything they share.
 *
 * @param registrationDeadline            the sign-up deadline every appointment of the batch takes: a
 *                                        fixed one is shared, a relative one is counted from each start
 * @param registrationLimit               the places of each appointment, or none for no limit
 * @param minRegistrations                the sign-ups an appointment needs to take place, or none
 * @param thresholdDays                   how many days before its start the minimum has to be reached
 * @param queueOrder                      how the waiting list of each appointment is ordered, or none for
 *                                        the order of sign-up
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
        @Nullable RegistrationDeadline registrationDeadline,
        RestrictionSelection restriction,
        RestrictionSelection viewRestriction,
        @Nullable Integer registrationLimit,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays,
        @Nullable QueueOrder queueOrder,
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
            @Nullable RegistrationDeadline registrationDeadline,
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
                List.of(),
                null);
    }

    /**
     * The deadline each appointment of the batch takes: the batch's own rule, or none where the batch
     * takes no sign-ups.
     *
     * @return the rule
     */
    public RegistrationDeadline deadline() {
        if (!Boolean.TRUE.equals(requiresRegistration) || registrationDeadline == null) {
            return RegistrationDeadline.none();
        }
        return registrationDeadline;
    }
}
