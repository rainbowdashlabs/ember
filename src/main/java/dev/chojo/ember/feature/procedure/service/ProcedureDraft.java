/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What one or more procedures are started with, before the member each is about is known.
 *
 * @param stationId   the station
 * @param templateId  the template whose steps the procedures start with, or {@code null} for none
 * @param name        the name the procedures start with
 * @param description what the procedures are about, or {@code null} for nothing
 * @param isPublic    whether the party and the member they are about see them
 * @param assignedBy  who starts them
 * @param dueAt       when they are due, or {@code null} for no date
 * @param party       the managing party, or {@code null} for the template's, else none
 * @param eventId     the appointment they were prepared for, or {@code null} for procedures that stand on
 *                    their own
 * @param eventDate   the one occurrence of that appointment, {@code null} exactly when the appointment is
 */
public record ProcedureDraft(
        int stationId,
        @Nullable Integer templateId,
        String name,
        @Nullable String description,
        boolean isPublic,
        int assignedBy,
        @Nullable Instant dueAt,
        @Nullable RestrictionAudience party,
        @Nullable Integer eventId,
        @Nullable LocalDate eventDate) {}
