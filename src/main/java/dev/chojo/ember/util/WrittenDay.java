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
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a day the way a spreadsheet writes it: three numbers joined by dots, dashes or slashes, in one
 * of the {@link Format orders} a CSV import offers.
 *
 * <p>The frontend reads appointment files with the same orders and rules, so a file reads alike
 * wherever it is imported. The separator does not matter, as long as both of a day are the same. Day
 * and month take one or two digits, the year two or four, and {@link TwoDigitYears} decides which century
 * a two-digit year falls in. A year in front takes two digits only above 31, since {@code 04.12.26}
 * would otherwise read as a day in 2004 as readily as the 4th of December.
 */
public final class WrittenDay {
    private static final Pattern THREE_PARTS = Pattern.compile("(\\d{1,4})([./-])(\\d{1,4})\\2(\\d{1,4})");

    private static final int LAST_DAY = 31;

    private WrittenDay() {}

    /**
     * The orders a day can be written in, each by the code the frontend picker sends.
     *
     * <p>{@link #AUTO} leaves the order to {@link #detect(Collection)} over the whole column. A single
     * value read with it tries day first, month first and year first in turn.
     */
    public enum Format {
        AUTO("auto", -1, -1, -1),
        DAY_MONTH_YEAR("day-month-year", 0, 1, 2),
        MONTH_DAY_YEAR("month-day-year", 1, 0, 2),
        YEAR_MONTH_DAY("year-month-day", 2, 1, 0);

        private final String code;
        private final int dayAt;
        private final int monthAt;
        private final int yearAt;

        Format(String code, int dayAt, int monthAt, int yearAt) {
            this.code = code;
            this.dayAt = dayAt;
            this.monthAt = monthAt;
            this.yearAt = yearAt;
        }

        /**
         * @param code the code of a format, or null
         * @return the format with that code, {@link #AUTO} where none is given or none has it
         */
        public static Format of(@Nullable String code) {
            return Arrays.stream(values())
                    .filter(format -> format.code.equals(code))
                    .findFirst()
                    .orElse(AUTO);
        }
    }

    /** Which century a two-digit year falls in. */
    public enum TwoDigitYears {
        /** The current century, so {@code 30} is 2030: for appointments, which mostly lie ahead. */
        CENTURY,
        /** The latest year not after the current one, so {@code 78} is 1978: for birth and join dates. */
        PAST
    }

    /**
     * The order a column of days is written in, worked out from all of its values.
     *
     * <p>Each order is tried on every value, and the first to read all of them is taken. A day never
     * passes 31 and a month never passes 12, so one value such as {@code 03-16-60} is enough to distinguish
     * month first from day first. Where several orders read everything, day first wins, then month
     * first, then year first. Where none reads everything, the one reading the most is taken.
     *
     * @param values the values of the column, blank ones included
     * @return the order they are written in, {@link Format#AUTO} where none of them reads as a day
     */
    public static Format detect(Collection<String> values) {
        Format best = Format.AUTO;
        long mostRead = 0;
        for (var format : Format.values()) {
            if (format == Format.AUTO) continue;
            long read = values.stream()
                    .filter(value -> of(value, format, TwoDigitYears.CENTURY).isPresent())
                    .count();
            if (read > mostRead) {
                best = format;
                mostRead = read;
            }
        }
        return best;
    }

    /**
     * How the days of one column are read.
     *
     * <p>A picked order reads every cell. An automatic one reads with the order detected over the
     * column, and a cell that order cannot read is tried in every order on its own, so a column
     * mixing {@code 04.12.26} and {@code 2026-12-05} still reads both.
     *
     * @param format    the order picked or detected
     * @param automatic whether a cell the order cannot read is tried in every order
     * @param years     which century a two-digit year falls in
     */
    public record Column(Format format, boolean automatic, TwoDigitYears years) {
        /**
         * @param values the values of the column, blank ones included
         * @param picked the order picked for the file
         * @param years  which century a two-digit year falls in
         * @return how the column is read
         */
        public static Column of(Collection<String> values, Format picked, TwoDigitYears years) {
            return picked == Format.AUTO ? new Column(detect(values), true, years) : new Column(picked, false, years);
        }

        /**
         * @param written the text, or null
         * @return the day it names, or empty where it names none
         */
        public Optional<LocalDate> read(@Nullable String written) {
            var day = WrittenDay.of(written, format, years);
            return automatic && day.isEmpty() ? WrittenDay.of(written, Format.AUTO, years) : day;
        }
    }

    /**
     * @param written the text, or null
     * @param format  the order the day is written in
     * @param years   which century a two-digit year falls in
     * @return the day it names, or empty where it is blank or names no day of the calendar
     */
    public static Optional<LocalDate> of(@Nullable String written, Format format, TwoDigitYears years) {
        return of(written, format, years, Year.now().getValue());
    }

    /**
     * @param written     the text, or null
     * @param format      the order the day is written in
     * @param years       which century a two-digit year falls in
     * @param currentYear the year the centuries are counted from
     * @return the day it names, or empty where it is blank or names no day of the calendar
     */
    static Optional<LocalDate> of(@Nullable String written, Format format, TwoDigitYears years, int currentYear) {
        if (written == null) return Optional.empty();
        Matcher match = THREE_PARTS.matcher(written.strip());
        if (!match.matches()) return Optional.empty();
        var parts = new String[] {match.group(1), match.group(3), match.group(4)};
        if (format != Format.AUTO) return inOrder(parts, format, years, currentYear);
        return inOrder(parts, Format.DAY_MONTH_YEAR, years, currentYear)
                .or(() -> inOrder(parts, Format.MONTH_DAY_YEAR, years, currentYear))
                .or(() -> inOrder(parts, Format.YEAR_MONTH_DAY, years, currentYear));
    }

    private static Optional<LocalDate> inOrder(String[] parts, Format format, TwoDigitYears years, int currentYear) {
        String day = parts[format.dayAt];
        String month = parts[format.monthAt];
        String year = parts[format.yearAt];
        if (day.length() > 2 || month.length() > 2 || (year.length() != 2 && year.length() != 4)) {
            return Optional.empty();
        }
        if (yearCouldBeADay(year, format)) return Optional.empty();
        try {
            return Optional.of(
                    LocalDate.of(fullYear(year, years, currentYear), Integer.parseInt(month), Integer.parseInt(day)));
        } catch (DateTimeException notADay) {
            return Optional.empty();
        }
    }

    /** Whether a year in front has two digits that could as well be a day, which is too ambiguous to read. */
    private static boolean yearCouldBeADay(String year, Format format) {
        return format == Format.YEAR_MONTH_DAY && year.length() == 2 && Integer.parseInt(year) <= LAST_DAY;
    }

    private static int fullYear(String year, TwoDigitYears years, int currentYear) {
        int value = Integer.parseInt(year);
        if (year.length() == 4) return value;
        int century = currentYear - currentYear % 100;
        if (years == TwoDigitYears.CENTURY) return century + value;
        return century + value > currentYear ? century - 100 + value : century + value;
    }
}
