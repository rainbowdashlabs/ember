/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which preparation and follow-up time a sheet's rules credit to one person.
 */
class ExtraTimeTest {
    private static final int LEADERS = 7;
    private static final int TRAINERS = 8;
    private static final int TAG = 3;

    private static final RestrictionMember LEADER =
            new RestrictionMember(1, StationUserType.MEMBER, List.of(LEADERS), List.of());
    private static final RestrictionMember TRAINER_WITH_TAG =
            new RestrictionMember(2, StationUserType.MEMBER, List.of(TRAINERS), List.of(TAG));
    private static final RestrictionMember NOBODY_IN_PARTICULAR =
            new RestrictionMember(3, StationUserType.MEMBER, List.of(), List.of());

    private static TimeRule rule(int preparation, int followUp, RestrictionAudience audience) {
        return new TimeRule(0, 0, null, preparation, followUp, audience, true);
    }

    private static RestrictionAudience groups(RestrictionMode mode, List<Integer> groupIds, List<Integer> tagIds) {
        return new RestrictionAudience(List.of(), groupIds, tagIds, List.of(), mode);
    }

    @Test
    void aMatchingRuleCreditsItsMinutes() {
        var time =
                ExtraTime.of(List.of(rule(30, 15, groups(RestrictionMode.AND, List.of(LEADERS), List.of()))), LEADER);

        assertEquals(new ExtraTime(30, 15), time);
        assertEquals(45, time.totalMinutes());
    }

    @Test
    void somebodyNoRuleNamesGetsNothing() {
        var rules = List.of(rule(30, 15, groups(RestrictionMode.AND, List.of(LEADERS), List.of())));

        assertSame(ExtraTime.NONE, ExtraTime.of(rules, NOBODY_IN_PARTICULAR));
        assertSame(ExtraTime.NONE, ExtraTime.of(List.of(), LEADER));
    }

    @Test
    void theLargestOfEachPartWinsInsteadOfTheSum() {
        var rules = List.of(
                rule(30, 0, groups(RestrictionMode.AND, List.of(LEADERS), List.of())),
                rule(
                        10,
                        20,
                        new RestrictionAudience(
                                List.of(StationUserType.MEMBER),
                                List.of(),
                                List.of(),
                                List.of(),
                                RestrictionMode.AND)));

        assertEquals(new ExtraTime(30, 20), ExtraTime.of(rules, LEADER));
    }

    @Test
    void andNeedsEveryKindNamedWhileOrNeedsOne() {
        var both = groups(RestrictionMode.AND, List.of(LEADERS), List.of(TAG));
        var either = groups(RestrictionMode.OR, List.of(LEADERS), List.of(TAG));

        assertSame(ExtraTime.NONE, ExtraTime.of(List.of(rule(30, 0, both)), TRAINER_WITH_TAG));
        assertEquals(new ExtraTime(30, 0), ExtraTime.of(List.of(rule(30, 0, either)), TRAINER_WITH_TAG));
    }

    @Test
    void aRuleNamingNobodyCreditsNobody() {
        assertSame(ExtraTime.NONE, ExtraTime.of(List.of(rule(30, 30, RestrictionAudience.empty())), LEADER));
    }

    @Test
    void aMemberNamedOneByOneIsCredited() {
        var named = new RestrictionAudience(List.of(), List.of(), List.of(), List.of(3), RestrictionMode.AND);

        assertEquals(new ExtraTime(5, 5), ExtraTime.of(List.of(rule(5, 5, named)), NOBODY_IN_PARTICULAR));
    }
}
