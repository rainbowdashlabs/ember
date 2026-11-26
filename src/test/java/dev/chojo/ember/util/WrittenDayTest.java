/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.util.WrittenDay.Format;
import dev.chojo.ember.util.WrittenDay.TwoDigitYears;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The days a spreadsheet writes, in the ways it writes them.
 *
 * <p>The cases match those of the frontend reader in {@code csvDate.test.ts}, so both read a file alike.
 */
class WrittenDayTest {
    private static final int THIS_YEAR = 2026;

    private static Optional<LocalDate> read(String written, Format format) {
        return WrittenDay.of(written, format, TwoDigitYears.CENTURY, THIS_YEAR);
    }

    @ParameterizedTest
    @CsvSource({
        "11.04.2024, 2024-04-11",
        "1.4.2024, 2024-04-01",
        "11.04.24, 2024-04-11",
        "11-04-24, 2024-04-11",
        "11/04/2024, 2024-04-11",
        "2024-04-11, 2024-04-11",
        "2024-4-1, 2024-04-01",
        "2024/04/11, 2024-04-11",
        "04/13/2024, 2024-04-13",
        "03-16-60, 2060-03-16",
        "' 11.04.2024 ', 2024-04-11"
    })
    void automaticallyReadsDayFirstThenMonthFirstThenYearFirst(String written, LocalDate day) {
        assertEquals(Optional.of(day), read(written, Format.AUTO));
    }

    @ParameterizedTest
    @CsvSource({
        "11.04.24, day-month-year",
        "11-04-2024, day-month-year",
        "11/04/24, day-month-year",
        "04.11.24, month-day-year",
        "04-11-2024, month-day-year",
        "04/11/24, month-day-year",
        "2024.04.11, year-month-day",
        "2024-04-11, year-month-day",
        "2024/4/11, year-month-day"
    })
    void aPickedOrderReadsEverySeparator(String written, String format) {
        assertEquals(Optional.of(LocalDate.of(2024, 4, 11)), read(written, Format.of(format)));
    }

    @ParameterizedTest
    @CsvSource({
        "03-16-60, day-month-year",
        "2024-04-11, day-month-year",
        "11.04.2024, year-month-day",
        "24/04/11, year-month-day"
    })
    void aPickedOrderReadsNoOtherOrder(String written, String format) {
        assertEquals(Optional.empty(), read(written, Format.of(format)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "31.02.2024", "11.04-24", "morgen", "11.04.124", "111.04.2024", "40/40/2024"})
    void readsNoDayWhereNoneIsWritten(String written) {
        assertEquals(Optional.empty(), read(written, Format.AUTO));
    }

    @ParameterizedTest
    @CsvSource({"01.01.30, 2030-01-01", "03.05.78, 2078-05-03"})
    void anAppointmentYearStaysInThisCentury(String written, LocalDate day) {
        assertEquals(Optional.of(day), read(written, Format.AUTO));
    }

    @ParameterizedTest
    @CsvSource({
        "03.05.78, 1978-05-03",
        "01.01.26, 2026-01-01",
        "01.01.27, 1927-01-01",
        "01.01.00, 2000-01-01",
        "03-16-60, 1960-03-16"
    })
    void aBirthYearIsNeverInTheFuture(String written, LocalDate day) {
        assertEquals(Optional.of(day), WrittenDay.of(written, Format.AUTO, TwoDigitYears.PAST, THIS_YEAR));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "11.04.24;1.12.2024|day-month-year",
                "11-04-24;01/12/24|day-month-year",
                "04/11/24;12/01/24|day-month-year",
                "05-04-60;03-16-60|month-day-year",
                "04/11/24;01/31/24|month-day-year",
                "2024-04-11;2024-12-01|year-month-day",
                "60-03-16;71.12.01|year-month-day",
                "11.04.24;morgen;|day-month-year",
                "morgen;|auto"
            })
    void detectsTheOrderOfAColumn(String column, String format) {
        assertEquals(Format.of(format), WrittenDay.detect(List.of(column.split(";", -1))));
    }

    @Test
    void anAutomaticColumnReadsACellItsOrderCannotReadOnItsOwn() {
        var column = WrittenDay.Column.of(List.of("04.12.26", "2026-12-05"), Format.AUTO, TwoDigitYears.CENTURY);
        assertEquals(Optional.of(LocalDate.of(2026, 12, 5)), column.read("2026-12-05"));
        assertEquals(Format.DAY_MONTH_YEAR, column.format());
    }

    @Test
    void aPickedColumnReadsOnlyItsOrder() {
        var column = WrittenDay.Column.of(List.of("04.12.26"), Format.DAY_MONTH_YEAR, TwoDigitYears.CENTURY);
        assertEquals(Optional.empty(), column.read("2026-12-05"));
    }

    @Test
    void anUnknownFormatIsReadAutomatically() {
        assertEquals(Format.AUTO, Format.of("DD.MM.YYYY"));
        assertEquals(Format.AUTO, Format.of(null));
    }
}
