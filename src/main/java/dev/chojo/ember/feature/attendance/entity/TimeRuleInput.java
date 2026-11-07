/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;

/**
 * One preparation and follow-up rule as it is written, before it has an identity of its own. A list
 * of these replaces a template's or a sheet's rules as a whole, in the order given.
 *
 * @param label              what the rule is for, null or blank where nobody named it
 * @param preparationMinutes minutes credited for preparing the appointment, 0 to 1440
 * @param followUpMinutes    minutes credited for what comes after it, 0 to 1440
 * @param audience           whom the rule names, which has to name somebody
 */
public record TimeRuleInput(
        @Nullable String label, int preparationMinutes, int followUpMinutes, RestrictionAudience audience) {}
