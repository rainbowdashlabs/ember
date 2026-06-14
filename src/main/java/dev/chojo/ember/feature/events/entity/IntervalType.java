/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * Defines how dates are generated for a batch of recurring events.
 */
public enum IntervalType {
    /** A specific weekday between two dates. */
    RECURRING,
    /** The first occurrence of a weekday in each month. */
    MONTHLY_FIRST,
    /** The first occurrence of a weekday in each quarter. */
    QUARTERLY,
    /** A yearly recurrence starting from a fixed date. */
    YEARLY
}
