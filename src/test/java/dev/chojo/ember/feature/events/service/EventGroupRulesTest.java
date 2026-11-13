/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of an event group like a Jugendflamme weekend: three practice days of which two count, and a
 * test that is mandatory.
 */
class EventGroupRulesTest {
    private static final SetRule PRACTICE = new SetRule(1, "Übung", 2, List.of(11, 12, 13));
    private static final SetRule TEST = new SetRule(2, "Prüfung", 1, List.of(21));
    private static final List<SetRule> JUGENDFLAMME = List.of(PRACTICE, TEST);

    @Test
    void twoPracticeDaysAndTheTestMeetTheRules() {
        assertTrue(EventGroupRules.met(JUGENDFLAMME, Set.of(11, 13, 21), Set.of()));
        assertTrue(EventGroupRules.met(JUGENDFLAMME, Set.of(11, 12, 13, 21), Set.of()));
    }

    @Test
    void oneShortSetIsNamedWithWhatIsMissing() {
        List<SetResult> shortfalls = EventGroupRules.shortfalls(JUGENDFLAMME, Set.of(11, 21), Set.of());

        assertEquals(List.of(new SetResult(1, "Übung", 2, 1)), shortfalls);
        assertEquals(1, shortfalls.getFirst().missing());
    }

    @Test
    void everyShortSetIsNamedInTheOrderOfTheSets() {
        List<SetResult> shortfalls = EventGroupRules.shortfalls(JUGENDFLAMME, Set.of(12), Set.of());

        assertEquals(List.of(new SetResult(1, "Übung", 2, 1), new SetResult(2, "Prüfung", 1, 0)), shortfalls);
    }

    @Test
    void aMandatoryPartIsASetAskingForAllOfIt() {
        var allPractice = List.of(new SetRule(1, "Übung", 3, List.of(11, 12, 13)));

        assertFalse(EventGroupRules.met(allPractice, Set.of(11, 12), Set.of()));
        assertTrue(EventGroupRules.met(allPractice, Set.of(11, 12, 13), Set.of()));
    }

    @Test
    void aCancelledPartThatWasNotChosenCanNoLongerBeAskedFor() {
        assertTrue(EventGroupRules.met(JUGENDFLAMME, Set.of(11, 21), Set.of(12, 13)));
        assertEquals(
                List.of(new SetResult(1, "Übung", 1, 0)),
                EventGroupRules.shortfalls(JUGENDFLAMME, Set.of(21), Set.of(12, 13)));
    }

    @Test
    void aCancelledPartThatWasChosenStillCounts() {
        assertTrue(EventGroupRules.met(JUGENDFLAMME, Set.of(11, 12, 21), Set.of(12)));
        assertTrue(EventGroupRules.met(JUGENDFLAMME, Set.of(11, 13, 21), Set.of(21)));
    }

    @Test
    void aNewChoiceWithoutTheCancelledPartStillNeedsTheMinimum() {
        assertEquals(
                List.of(new SetResult(1, "Übung", 2, 1)),
                EventGroupRules.shortfalls(JUGENDFLAMME, Set.of(13, 21), Set.of(12)));
    }

    @Test
    void aMandatorySetWithACancelledPartAsksForTheRest() {
        var allPractice = List.of(new SetRule(1, "Übung", 3, List.of(11, 12, 13)));

        assertTrue(EventGroupRules.met(allPractice, Set.of(11, 13), Set.of(12)));
        assertFalse(EventGroupRules.met(allPractice, Set.of(11), Set.of(12)));
    }

    @Test
    void aSetWhosePartsWereAllCalledOffAsksForNothing() {
        assertTrue(EventGroupRules.met(JUGENDFLAMME, Set.of(11, 12), Set.of(21)));
    }

    @Test
    void aSetAskingForNothingIsAlwaysMet() {
        var optional = List.of(new SetRule(3, "Zusatz", 0, List.of(31, 32)));

        assertTrue(EventGroupRules.met(optional, Set.of(), Set.of()));
    }

    @Test
    void partsOutsideTheSetsDoNotHelpAnySet() {
        assertFalse(EventGroupRules.met(JUGENDFLAMME, Set.of(11, 21, 99), Set.of()));
    }

    @Test
    void anEventGroupWithoutSetsIsMetByAnything() {
        assertTrue(EventGroupRules.met(List.of(), Set.of(), Set.of()));
    }

    @Test
    void theDefaultRuleOfOneSetAskingForOnePartTakesAnySinglePart() {
        var anyPart = List.of(new SetRule(1, "Teile", 1, List.of(11, 12, 13)));

        assertTrue(EventGroupRules.met(anyPart, Set.of(12), Set.of()));
        assertFalse(EventGroupRules.met(anyPart, Set.of(), Set.of()));
    }

    @Test
    void aMinimumAboveTheSetAsksForAllOfItAndOneBelowZeroForNone() {
        var tooMany = List.of(new SetRule(1, "Übung", 5, List.of(11, 12, 13)));
        var negative = List.of(new SetRule(1, "Übung", -1, List.of(11, 12, 13)));

        assertEquals(
                List.of(new SetResult(1, "Übung", 3, 2)),
                EventGroupRules.shortfalls(tooMany, Set.of(11, 12), Set.of()));
        assertTrue(EventGroupRules.met(negative, Set.of(), Set.of()));
    }

    @Test
    void aPartNamedTwiceCountsOnce() {
        var twice = List.of(new SetRule(1, "Übung", 2, List.of(11, 11, 12)));

        assertFalse(EventGroupRules.met(twice, Set.of(11), Set.of()));
    }

    @Test
    void everySetIsEvaluatedWithItsMinimumAfterCancellations() {
        List<SetResult> results = EventGroupRules.evaluate(JUGENDFLAMME, Set.of(11), Set.of(12, 13));

        assertEquals(List.of(new SetResult(1, "Übung", 1, 1), new SetResult(2, "Prüfung", 1, 0)), results);
        assertTrue(results.getFirst().met());
        assertEquals(0, results.getFirst().missing());
        assertEquals(1, results.get(1).missing());
    }
}
