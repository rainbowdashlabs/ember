/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * An own date format the editor asks about while it is typed.
 *
 * @param pattern  the format as written
 * @param language the language of the template, which writes the names of months and weekdays
 * @param clock    whether the date it is for has a time of day
 */
public record DateFormatCheckRequest(String pattern, DocumentLanguage language, boolean clock) {

    /** @return the example day in the format, or why it cannot be printed */
    public DateFormatCheck check() {
        return DateFormat.check(pattern, language, clock);
    }
}
