/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hours on attendance sheets and reports follow the station's language, never the locale the server
 * happens to run in.
 */
class AttendanceHoursFormatTest {
    private Locale serverLocale;

    @BeforeEach
    void runOnAGermanServer() {
        serverLocale = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
    }

    @AfterEach
    void restoreServerLocale() {
        Locale.setDefault(serverLocale);
    }

    /** An English station reads a decimal point even on a German server. */
    @Test
    void anEnglishStationReadsADecimalPoint() {
        assertEquals("7.5", AttendanceReportService.hoursIn(7.5, "en"));
    }

    /** A German station reads a decimal comma. */
    @Test
    void aGermanStationReadsADecimalComma() {
        assertEquals("7,5", AttendanceReportService.hoursIn(7.5, "de"));
    }

    /** Whole hours keep their one decimal, so a column lines up. */
    @Test
    void wholeHoursKeepOneDecimal() {
        assertEquals("3.0", AttendanceReportService.hoursIn(3, "en"));
    }
}
