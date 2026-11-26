/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The days a spreadsheet writes, in the ways it writes them.
 *
 * <p>Dots and dashes read day first unless the year leads, slashes month first, and a two-digit year
 * never lies in the future.
 */
class WrittenDayTest {
    private static final int THIS_YEAR = 2026;

    @ParameterizedTest
    @CsvSource({
        "11.04.2024, 2024-04-11",
        "1.4.2024, 2024-04-01",
        "11.04.24, 2024-04-11",
        "11-04-24, 2024-04-11",
        "11-04-2024, 2024-04-11",
        "2024-04-11, 2024-04-11",
        "2024-4-1, 2024-04-01",
        "04/11/2024, 2024-04-11",
        "4/11/24, 2024-04-11",
        "' 11.04.2024 ', 2024-04-11"
    })
    void readsEveryWayOfWritingADay(String written, LocalDate day) {
        assertEquals(Optional.of(day), WrittenDay.of(written, THIS_YEAR));
    }

    @ParameterizedTest
    @CsvSource({"03.05.78, 1978-05-03", "01.01.26, 2026-01-01", "01.01.27, 1927-01-01", "01.01.00, 2000-01-01"})
    void aTwoDigitYearIsNeverInTheFuture(String written, LocalDate day) {
        assertEquals(Optional.of(day), WrittenDay.of(written, THIS_YEAR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "31.02.2024", "11.04-24", "morgen", "2024/04/11", "11.04.124", "13/01/2024"})
    void readsNoDayWhereNoneIsWritten(String written) {
        assertEquals(Optional.empty(), WrittenDay.of(written, THIS_YEAR));
    }
}
