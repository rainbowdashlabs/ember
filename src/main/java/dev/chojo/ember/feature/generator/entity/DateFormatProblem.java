/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Why an own date format cannot be printed, as the editor names it while the format is typed.
 */
public enum DateFormatProblem {
    /** Nothing is written yet. */
    EMPTY,
    /** It has more characters than {@link DateFormat#MAX_LENGTH}. */
    TOO_LONG,
    /** A run of letters is no {@link DateToken}. */
    UNKNOWN,
    /** It holds separators only. */
    NO_TOKEN,
    /** It prints a time of day for a date that has none. */
    CLOCK
}
