/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.Instant;
import java.util.List;

public record BatchRequest(
        String name,
        String description,
        Integer templateId,
        Integer categoryId,
        Integer layoutId,
        List<EventLayoutField> inlineFields,
        List<BatchRow> rows,
        Boolean requiresRegistration,
        Boolean requiresConfirmation,
        Instant registrationDeadline,
        List<Integer> restrictedRoleIds,
        List<Integer> restrictedGroupIds,
        List<Integer> restrictedTagIds) {}
