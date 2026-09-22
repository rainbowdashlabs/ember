/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.form.service.FormRespondents.Respondent;
import dev.chojo.ember.feature.form.service.FormResultQuery.FieldCondition;
import dev.chojo.ember.feature.form.service.FormResultQuery.Filter;
import dev.chojo.ember.feature.form.service.FormResultQuery.Match;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which respondents a filter lets through.
 */
class FormResultQueryTest {
    private static final Respondent YOUTH_AND_ACTIVE = new Respondent(
            1, StationUserType.MEMBER, Set.of(10, 11), Set.of(20), Map.of(30, "L", 31, "true", 32, "12"), 16);
    private static final Respondent YOUTH_ONLY =
            new Respondent(2, StationUserType.TRIAL, Set.of(10), Set.of(), Map.of(), null);

    @Test
    void anEmptyFilterLetsEverybodyThrough() {
        var filter = new Filter(null, List.of(), null, null, null, List.of(), null, null);

        assertTrue(filter.matches(YOUTH_AND_ACTIVE));
        assertTrue(filter.matches(YOUTH_ONLY));
    }

    @Test
    void groupsMatchAnyOfThemOrAllOfThem() {
        var anyOf = new Filter(null, List.of(11, 12), Match.ANY, null, null, null, null, null);
        var allOf = new Filter(null, List.of(10, 11), Match.ALL, null, null, null, null, null);

        assertTrue(anyOf.matches(YOUTH_AND_ACTIVE));
        assertFalse(anyOf.matches(YOUTH_ONLY));
        assertTrue(allOf.matches(YOUTH_AND_ACTIVE), "a member of both groups");
        assertFalse(allOf.matches(YOUTH_ONLY), "a member of only one of them");
    }

    @Test
    void tagsMatchLikeGroups() {
        var allOf = new Filter(null, null, null, List.of(20, 21), Match.ALL, null, null, null);
        var anyOf = new Filter(null, null, null, List.of(20, 21), Match.ANY, null, null, null);

        assertFalse(allOf.matches(YOUTH_AND_ACTIVE));
        assertTrue(anyOf.matches(YOUTH_AND_ACTIVE));
    }

    @Test
    void conditionsOnDifferentAttributesMustAllHold() {
        var filter = new Filter(List.of(StationUserType.TRIAL), List.of(10), Match.ANY, null, null, null, null, null);

        assertFalse(filter.matches(YOUTH_AND_ACTIVE), "in the group but not of the type");
        assertTrue(filter.matches(YOUTH_ONLY));
    }

    @Test
    void anUnknownAgePassesNoAgeCondition() {
        var teens = new Filter(null, null, null, null, null, null, 14, 17);

        assertTrue(teens.matches(YOUTH_AND_ACTIVE));
        assertFalse(teens.matches(YOUTH_ONLY), "nothing says they are old enough");
    }

    @Test
    void profileFieldsMatchByAnswerOrRange() {
        var sizes = new Filter(
                null,
                null,
                null,
                null,
                null,
                List.of(new FieldCondition(30, List.of("M", "L"), null, null)),
                null,
                null);
        var firstAid = new Filter(
                null,
                null,
                null,
                null,
                null,
                List.of(new FieldCondition(31, List.of("false"), null, null)),
                null,
                null);
        var years =
                new Filter(null, null, null, null, null, List.of(new FieldCondition(32, null, 10.0, 15.0)), null, null);

        assertTrue(sizes.matches(YOUTH_AND_ACTIVE));
        assertFalse(sizes.matches(YOUTH_ONLY), "no answer is no match");
        assertFalse(firstAid.matches(YOUTH_AND_ACTIVE));
        assertTrue(years.matches(YOUTH_AND_ACTIVE));
    }

    @Test
    void aRangeOnlyMatchesAnswersThatAreNumbers() {
        var sizeAsNumber = new FieldCondition(30, null, 1.0, null);
        var onlyUpper = new FieldCondition(32, null, null, 11.0);

        assertFalse(sizeAsNumber.matches("L"), "a choice is not a number");
        assertFalse(onlyUpper.matches("12"));
        assertTrue(onlyUpper.matches("11"));
    }

    @Test
    void anAgeBoundOnOneSideLeavesTheOtherOpen() {
        assertTrue(new Filter(null, null, null, null, null, null, 16, null).matches(YOUTH_AND_ACTIVE));
        assertFalse(new Filter(null, null, null, null, null, null, null, 15).matches(YOUTH_AND_ACTIVE));
    }
}
