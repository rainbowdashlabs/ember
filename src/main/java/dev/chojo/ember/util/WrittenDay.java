/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a day the way a spreadsheet writes it: {@code 11.04.2024}, {@code 11.4.24}, {@code 11-04-24},
 * {@code 2024-04-11} or {@code 04/11/2024}.
 *
 * <p>Dots and dashes put the day first, unless the year leads with four digits as in ISO. Slashes put
 * the month first, as an English spreadsheet does. A two-digit year is read as the latest year it can
 * stand for that is not after the current one, so {@code 24} is 2024 and {@code 78} a birth year of
 * 1978.
 */
public final class WrittenDay {
    private static final Pattern YEAR_FIRST = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern DAY_FIRST = Pattern.compile("(\\d{1,2})([.-])(\\d{1,2})\\2(\\d{2}|\\d{4})");
    private static final Pattern MONTH_FIRST = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})");

    private WrittenDay() {}

    /**
     * @param written the text, or null
     * @return the day it names, or empty where it is blank or names no day of the calendar
     */
    public static Optional<LocalDate> of(@Nullable String written) {
        return of(written, Year.now().getValue());
    }

    /**
     * @param written     the text, or null
     * @param currentYear the year a two-digit year may reach up to
     * @return the day it names, or empty where it is blank or names no day of the calendar
     */
    static Optional<LocalDate> of(@Nullable String written, int currentYear) {
        if (written == null || written.isBlank()) return Optional.empty();
        String value = written.strip();
        Matcher match = YEAR_FIRST.matcher(value);
        if (match.matches()) return day(Integer.parseInt(match.group(1)), match.group(2), match.group(3));
        match = DAY_FIRST.matcher(value);
        if (match.matches()) return day(fullYear(match.group(4), currentYear), match.group(3), match.group(1));
        match = MONTH_FIRST.matcher(value);
        if (match.matches()) return day(fullYear(match.group(3), currentYear), match.group(1), match.group(2));
        return Optional.empty();
    }

    private static int fullYear(String year, int currentYear) {
        int value = Integer.parseInt(year);
        if (year.length() == 4) return value;
        int century = currentYear - currentYear % 100;
        return century + value > currentYear ? century - 100 + value : century + value;
    }

    private static Optional<LocalDate> day(int year, String month, String day) {
        try {
            return Optional.of(LocalDate.of(year, Integer.parseInt(month), Integer.parseInt(day)));
        } catch (DateTimeException notADay) {
            return Optional.empty();
        }
    }
}
