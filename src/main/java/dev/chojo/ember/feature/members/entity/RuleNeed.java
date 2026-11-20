/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/** What a check's value has to carry for its operator to have something to compare with. */
public enum RuleNeed {
    /** Nothing: the operator stands on its own, or names its group or tag in a column of its own. */
    NOTHING,
    /** At least one answer of the field. */
    ANSWERS,
    /** At least one user type. */
    USER_TYPES,
    /** At least one expiry state. */
    STATES,
    /** One number. */
    NUMBER,
    /** Two numbers, the lower first. */
    NUMBER_RANGE,
    /** A text that is not blank. */
    TEXT,
    /** A date. */
    DATE,
    /** A duration: an amount of at least zero and a unit. */
    DURATION,
    /** A number of whole years of at least zero. */
    YEARS,
    /** Two numbers of whole years, the lower first. */
    YEARS_RANGE
}
