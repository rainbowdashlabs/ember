/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionSelection;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

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
        RestrictionSelection viewRestriction) {}
