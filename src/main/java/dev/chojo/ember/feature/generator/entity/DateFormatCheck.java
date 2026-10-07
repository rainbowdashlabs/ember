/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * An own date format as the editor checks it while it is typed: the example day printed in it, or why
 * it cannot be printed.
 *
 * @param example the example day in the format, or null where it cannot be printed
 * @param problem why it cannot be printed, or null where it can
 * @param detail  for {@link DateFormatProblem#UNKNOWN} the run that is no token, for
 *                {@link DateFormatProblem#TOO_LONG} the most characters a format may have, else null
 */
public record DateFormatCheck(
        @Nullable String example,
        @Nullable DateFormatProblem problem,
        @Nullable String detail) {

    /**
     * @param example the example day in the format
     * @return the check of a format that can be printed
     */
    static DateFormatCheck printed(String example) {
        return new DateFormatCheck(example, null, null);
    }

    /**
     * @param problem why it cannot be printed
     * @param detail  what the problem names, or null
     * @return the check of a format that cannot be printed
     */
    static DateFormatCheck refused(DateFormatProblem problem, @Nullable String detail) {
        return new DateFormatCheck(null, problem, detail);
    }
}
