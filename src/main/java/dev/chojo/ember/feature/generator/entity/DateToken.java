/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The letters an own date format is written with, German as the people writing templates read them:
 * T for Tag, M for Monat, J for Jahr, h for Stunde and m for Minute. Each stands for a pattern of
 * {@link java.time.format.DateTimeFormatter}; the words of a month or a weekday follow the language of
 * the template.
 *
 * <p>The editor's preview and the help centre's table of these letters read the same set from
 * {@code frontend/src/util/dateFormatPattern.ts}.
 */
public enum DateToken {
    DAY("T", "d", false),
    DAY_TWO_DIGITS("TT", "dd", false),
    WEEKDAY_SHORT("TTT", "EEE", false),
    WEEKDAY("TTTT", "EEEE", false),
    MONTH("M", "M", false),
    MONTH_TWO_DIGITS("MM", "MM", false),
    MONTH_SHORT("MMM", "MMM", false),
    MONTH_NAME("MMMM", "MMMM", false),
    YEAR_TWO_DIGITS("JJ", "yy", false),
    YEAR("JJJJ", "yyyy", false),
    HOUR("h", "H", true),
    HOUR_TWO_DIGITS("hh", "HH", true),
    MINUTE("mm", "mm", true);

    private final String written;
    private final String pattern;
    private final boolean clock;

    DateToken(String written, String pattern, boolean clock) {
        this.written = written;
        this.pattern = pattern;
        this.clock = clock;
    }

    /** @return the pattern of {@link java.time.format.DateTimeFormatter} it stands for */
    public String pattern() {
        return pattern;
    }

    /** @return whether it prints a time of day, which a plain day does not have */
    public boolean clock() {
        return clock;
    }

    /**
     * @param written a run of one letter in an own format
     * @return the token so written, or empty where the run is none
     */
    public static Optional<DateToken> of(String written) {
        return Arrays.stream(values())
                .filter(token -> token.written.equals(written))
                .findFirst();
    }
}
