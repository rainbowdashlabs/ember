/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.auth.StationUserType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a check of a member rule compares with, and how it is stored. */
class RuleValueTest {

    @Test
    void everyShapeComesBackAsItWasStored() {
        var values = List.of(
                RuleValue.ofAnswers(List.of("Bronze", "Silber")),
                RuleValue.ofUserTypes(List.of(StationUserType.MEMBER, StationUserType.TEAM)),
                RuleValue.ofStates(List.of(ExpiryState.EXPIRING, ExpiryState.EXPIRED)),
                RuleValue.ofNumber(new BigDecimal("2.5")),
                RuleValue.ofNumbers(BigDecimal.ONE, BigDecimal.TEN),
                RuleValue.ofText("Atemschutz"),
                RuleValue.ofDate(LocalDate.of(2027, 1, 1)),
                RuleValue.ofDuration(10, RuleUnit.MONTHS),
                RuleValue.ofYears(10),
                RuleValue.ofYears(10, 14));
        for (var value : values) {
            assertEquals(value, RuleValue.parse(value.toJson()));
        }
    }

    @Test
    void nothingStoredReadsAsEmpty() {
        assertEquals(RuleValue.empty(), RuleValue.parse(null));
        assertEquals(RuleValue.empty(), RuleValue.parse("{}"));
        assertEquals(RuleValue.empty(), RuleValue.parse("not json"));
    }

    @Test
    void eachNeedAsksForItsOwnPart() {
        assertTrue(RuleValue.empty().satisfies(RuleNeed.NOTHING));
        assertTrue(RuleValue.ofAnswers(List.of("Bronze")).satisfies(RuleNeed.ANSWERS));
        assertFalse(RuleValue.ofAnswers(List.of(" ")).satisfies(RuleNeed.ANSWERS));
        assertFalse(RuleValue.ofAnswers(Arrays.asList("", "")).satisfies(RuleNeed.ANSWERS));
        assertTrue(RuleValue.ofUserTypes(List.of(StationUserType.MEMBER)).satisfies(RuleNeed.USER_TYPES));
        assertFalse(RuleValue.ofUserTypes(List.of()).satisfies(RuleNeed.USER_TYPES));
        assertTrue(RuleValue.ofStates(List.of(ExpiryState.EXPIRED)).satisfies(RuleNeed.STATES));
        assertFalse(RuleValue.empty().satisfies(RuleNeed.STATES));
        assertTrue(RuleValue.ofNumber(BigDecimal.ZERO).satisfies(RuleNeed.NUMBER));
        assertFalse(RuleValue.empty().satisfies(RuleNeed.NUMBER));
        assertTrue(RuleValue.ofText("x").satisfies(RuleNeed.TEXT));
        assertFalse(RuleValue.ofText("  ").satisfies(RuleNeed.TEXT));
        assertTrue(RuleValue.ofDate(LocalDate.of(2027, 1, 1)).satisfies(RuleNeed.DATE));
        assertFalse(RuleValue.empty().satisfies(RuleNeed.DATE));
        assertTrue(RuleValue.ofYears(0).satisfies(RuleNeed.YEARS));
        assertFalse(RuleValue.ofYears(-1).satisfies(RuleNeed.YEARS));
    }

    @Test
    void aRangeRunsUpwards() {
        assertTrue(RuleValue.ofNumbers(BigDecimal.ONE, BigDecimal.ONE).satisfies(RuleNeed.NUMBER_RANGE));
        assertFalse(RuleValue.ofNumbers(BigDecimal.TEN, BigDecimal.ONE).satisfies(RuleNeed.NUMBER_RANGE));
        assertFalse(RuleValue.ofNumber(BigDecimal.ONE).satisfies(RuleNeed.NUMBER_RANGE));
        assertTrue(RuleValue.ofYears(10, 14).satisfies(RuleNeed.YEARS_RANGE));
        assertFalse(RuleValue.ofYears(14, 10).satisfies(RuleNeed.YEARS_RANGE));
        assertFalse(RuleValue.ofYears(-1, 10).satisfies(RuleNeed.YEARS_RANGE));
        assertFalse(RuleValue.ofYears(10).satisfies(RuleNeed.YEARS_RANGE));
    }

    @Test
    void aDurationNeedsAnAmountAndAUnit() {
        assertTrue(RuleValue.ofDuration(0, RuleUnit.DAYS).satisfies(RuleNeed.DURATION));
        assertFalse(RuleValue.ofDuration(-1, RuleUnit.DAYS).satisfies(RuleNeed.DURATION));
        assertFalse(
                new RuleValue(null, null, null, null, null, null, null, 3, null, null).satisfies(RuleNeed.DURATION));
    }
}
