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
 * Reads a day the way a spreadsheet writes it, in one of the {@link Format formats} a CSV import offers.
 *
 * <p>The frontend reads appointment files with the same formats and rules, so a file reads alike
 * wherever it is imported. Every format takes a two-digit or a four-digit year, and {@link TwoDigitYears}
 * says which century a two-digit one falls in.
 */
public final class WrittenDay {
    private static final Pattern DOTTED = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2}|\\d{4})");
    private static final Pattern DASHED = Pattern.compile("(\\d{1,2})-(\\d{1,2})-(\\d{2}|\\d{4})");
    private static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern SLASHED = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})");

    private WrittenDay() {}

    /**
     * The ways a day can be written in a file, each by the code the frontend picker sends.
     *
     * <p>{@link #AUTO} leaves the format to {@link #detect(Collection)} over the whole column. A single
     * value read with it tries the dotted, the dashed, the ISO and the month-first slashed form in turn.
     */
    public enum Format {
        AUTO("auto"),
        DAY_MONTH_YEAR_DOTTED("DD.MM.YYYY"),
        DAY_MONTH_YEAR_DASHED("DD-MM-YYYY"),
        YEAR_MONTH_DAY("YYYY-MM-DD"),
        MONTH_DAY_YEAR_SLASHED("MM/DD/YYYY"),
        DAY_MONTH_YEAR_SLASHED("DD/MM/YYYY");

        private final String code;

        Format(String code) {
            this.code = code;
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
     * The format a column of days is written in, worked out from all of its values.
     *
     * <p>Each format is tried on every value, and the first to read all of them is taken. A day never passes 31 and a month never passes 12, so one value such as
     * {@code 13/01/2024} is enough to tell day-first from month-first slashes. Where several formats
     * read everything, the usual one wins: dots and dashes day first, then ISO, then slashes month
     * first. Where none reads everything, the one reading the most is taken.
     *
     * @param values the values of the column, blank ones included
     * @return the format they are written in, {@link Format#AUTO} where none of them reads as a day
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
     * <p>A picked format reads every cell. An automatic one reads with the format detected over the
     * column, and a cell that format cannot read is tried in every format on its own, so a column
     * mixing {@code 04.12.26} and {@code 2026-12-05} still reads both.
     *
     * @param format    the format picked or detected
     * @param automatic whether a cell the format cannot read is tried in every format
     * @param years     which century a two-digit year falls in
     */
    public record Column(Format format, boolean automatic, TwoDigitYears years) {
        /**
         * @param values the values of the column, blank ones included
         * @param picked the format picked for the file
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
     * @param format  how the day is written
     * @param years   which century a two-digit year falls in
     * @return the day it names, or empty where it is blank or names no day of the calendar
     */
    public static Optional<LocalDate> of(@Nullable String written, Format format, TwoDigitYears years) {
        return of(written, format, years, Year.now().getValue());
    }

    /**
     * @param written     the text, or null
     * @param format      how the day is written
     * @param years       which century a two-digit year falls in
     * @param currentYear the year the centuries are counted from
     * @return the day it names, or empty where it is blank or names no day of the calendar
     */
    static Optional<LocalDate> of(@Nullable String written, Format format, TwoDigitYears years, int currentYear) {
        if (written == null || written.isBlank()) return Optional.empty();
        String value = written.strip();
        var reader = new Reader(years, currentYear);
        return switch (format) {
            case DAY_MONTH_YEAR_DOTTED -> reader.dayFirst(DOTTED, value);
            case DAY_MONTH_YEAR_DASHED -> reader.dayFirst(DASHED, value);
            case YEAR_MONTH_DAY -> reader.yearFirst(value);
            case MONTH_DAY_YEAR_SLASHED -> reader.monthFirst(value);
            case DAY_MONTH_YEAR_SLASHED -> reader.dayFirst(SLASHED, value);
            case AUTO ->
                reader.dayFirst(DOTTED, value)
                        .or(() -> reader.dayFirst(DASHED, value))
                        .or(() -> reader.yearFirst(value))
                        .or(() -> reader.monthFirst(value));
        };
    }

    private record Reader(TwoDigitYears years, int currentYear) {
        Optional<LocalDate> dayFirst(Pattern pattern, String value) {
            Matcher match = pattern.matcher(value);
            return match.matches() ? day(fullYear(match.group(3)), match.group(2), match.group(1)) : Optional.empty();
        }

        Optional<LocalDate> monthFirst(String value) {
            Matcher match = SLASHED.matcher(value);
            return match.matches() ? day(fullYear(match.group(3)), match.group(1), match.group(2)) : Optional.empty();
        }

        Optional<LocalDate> yearFirst(String value) {
            Matcher match = ISO.matcher(value);
            return match.matches()
                    ? day(Integer.parseInt(match.group(1)), match.group(2), match.group(3))
                    : Optional.empty();
        }

        private int fullYear(String year) {
            int value = Integer.parseInt(year);
            if (year.length() == 4) return value;
            int century = currentYear - currentYear % 100;
            if (years == TwoDigitYears.CENTURY) return century + value;
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
}
