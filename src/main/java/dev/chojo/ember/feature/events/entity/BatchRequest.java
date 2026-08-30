/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionSelection;

import java.time.Instant;
import java.util.List;

public record BatchRequest(
        String name,
        String description,
        Integer templateId,
        Integer categoryId,
        List<BatchFieldEntry> inlineFields,
        List<BatchRow> rows,
        Boolean requiresRegistration,
        Boolean requiresConfirmation,
        Instant registrationDeadline,
        RestrictionSelection restriction,
        RestrictionSelection viewRestriction) {}
