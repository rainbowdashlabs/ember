/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where an expiry date stands, on the examples the browser's copy of the rule is tested against.
 *
 * <p>The same rows stand in {@code frontend/src/util/expiry.test.ts}. The two copies of the rule
 * colour the list and word the reminders, and a date that reads as expiring on the screen while its
 * reminder calls it expired is exactly what keeping one set of examples prevents.
 */
class ExpiryStateTest {

    @ParameterizedTest(name = "{0} warned {1} days ahead is {2} on 2026-03-01")
    @CsvSource(
            nullValues = "none",
            value = {
                "2026-04-15, 30, VALID",
                "2026-03-31, 30, EXPIRING",
                "2026-03-02, 30, EXPIRING",
                "2026-03-01, 30, EXPIRING",
                "2026-02-28, 30, EXPIRED",
                "2025-12-31, 30, EXPIRED",
                "2026-03-02, 0, VALID",
                "2026-03-01, 0, EXPIRING",
                "none, 30, EMPTY",
            })
    void aDateStandsWhereTheSharedExamplesSay(LocalDate lastValidDay, int warnFromDays, ExpiryState expected) {
        assertEquals(expected, ExpiryState.of(lastValidDay, LocalDate.of(2026, 3, 1), warnFromDays));
    }

    @ParameterizedTest(name = "{0} has {1} days left on 2026-03-01")
    @CsvSource({"2026-03-13, 12", "2026-03-01, 0", "2026-02-26, -3"})
    void theDaysLeftCountFromTheLastValidDay(LocalDate lastValidDay, long expected) {
        assertEquals(expected, ExpiryState.daysLeft(lastValidDay, LocalDate.of(2026, 3, 1)));
    }
}
