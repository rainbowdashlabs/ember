/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.entity.MailRule;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleSelectionTest {

    private static MailRule rule(int id, int position, String name, List<String> senders) {
        return rule(id, position, name, senders, null, null, true);
    }

    private static MailRule rule(
            int id,
            int position,
            String name,
            List<String> senders,
            String subjectFilter,
            String attachmentFilter,
            boolean enabled) {
        return new MailRule(
                id,
                1,
                name,
                position,
                enabled,
                subjectFilter,
                attachmentFilter,
                List.of("application/pdf"),
                0L,
                false,
                MailTitleSource.SUBJECT,
                false,
                false,
                false,
                MailRuleAction.MARK_SEEN,
                null,
                senders,
                List.of(),
                Instant.parse("2026-09-01T08:00:00Z"));
    }

    private static final List<String> ONE_PDF = List.of("bescheinigung.pdf");

    @Test
    void theFirstRuleInOrderTakesTheMessage() {
        var second = rule(2, 1, "second", List.of("*@musterstadt.de"));
        var first = rule(1, 0, "first", List.of("*@musterstadt.de"));

        var taken = RuleSelection.firstMatch(List.of(second, first), "post@musterstadt.de", "Attest", ONE_PDF);

        assertEquals("first", taken.orElseThrow().name());
    }

    @Test
    void aRuleTrustingNobodyNeverTakesAnything() {
        var open = rule(1, 0, "trusts nobody", List.of());

        assertTrue(RuleSelection.firstMatch(List.of(open), "post@musterstadt.de", "Attest", ONE_PDF)
                .isEmpty());
    }

    @Test
    void aRuleSwitchedOffIsPassedOverWithoutTakingTheMessage() {
        var off = rule(1, 0, "off", List.of("*@musterstadt.de"), null, null, false);
        var on = rule(2, 1, "on", List.of("*@musterstadt.de"));

        var taken = RuleSelection.firstMatch(List.of(off, on), "post@musterstadt.de", "Attest", ONE_PDF);

        assertEquals("on", taken.orElseThrow().name());
    }

    @Test
    void aSubjectFilterIsPartOfWhetherTheRuleTakesIt() {
        var attests = rule(1, 0, "attests", List.of("*@musterstadt.de"), "Attest", null, true);

        assertTrue(RuleSelection.takes(attests, "post@musterstadt.de", "Attest Anna", ONE_PDF));
        assertTrue(RuleSelection.takes(attests, "post@musterstadt.de", "ATTEST anna", ONE_PDF));
        assertFalse(RuleSelection.takes(attests, "post@musterstadt.de", "Rechnung", ONE_PDF));
        assertFalse(RuleSelection.takes(attests, "post@musterstadt.de", null, ONE_PDF));
    }

    @Test
    void anAttachmentNameFilterIsSatisfiedByAnyOneOfThem() {
        var scans = rule(1, 0, "scans", List.of("*@musterstadt.de"), null, "scan", true);

        assertTrue(RuleSelection.takes(scans, "post@musterstadt.de", "Post", List.of("logo.png", "scan-01.pdf")));
        assertFalse(RuleSelection.takes(scans, "post@musterstadt.de", "Post", List.of("logo.png")));
        assertFalse(RuleSelection.takes(scans, "post@musterstadt.de", "Post", List.of()));
    }

    /**
     * The consequence the concept settles on: a rule that takes a message and then refuses everything in
     * it has still taken it, and the rule behind it does not get a turn. The separation belongs in the
     * attachment name filter, which is why that filter is part of matching and the type is not.
     */
    @Test
    void theRuleThatTakesAMessageTakesItFromTheOnesBehind() {
        var everything = rule(1, 0, "everything", List.of("*@musterstadt.de"));
        var photographs = rule(2, 1, "photographs", List.of("*@musterstadt.de"), null, "foto", true);

        var taken = RuleSelection.firstMatch(
                List.of(everything, photographs), "post@musterstadt.de", "Post", List.of("foto-1.jpg"));

        assertEquals("everything", taken.orElseThrow().name());
    }

    @Test
    void twoRulesAtTheSamePositionAreStillOrderedTheSameWayEveryTime() {
        var later = rule(9, 0, "later", List.of("*@musterstadt.de"));
        var earlier = rule(4, 0, "earlier", List.of("*@musterstadt.de"));

        assertEquals(
                "earlier",
                RuleSelection.firstMatch(List.of(later, earlier), "post@musterstadt.de", "Post", ONE_PDF)
                        .orElseThrow()
                        .name());
        assertEquals(
                "earlier",
                RuleSelection.firstMatch(List.of(earlier, later), "post@musterstadt.de", "Post", ONE_PDF)
                        .orElseThrow()
                        .name());
    }

    @Test
    void noRulesAtAllTakeNothing() {
        assertTrue(RuleSelection.firstMatch(List.of(), "post@musterstadt.de", "Post", ONE_PDF)
                .isEmpty());
        assertTrue(RuleSelection.firstMatch(null, "post@musterstadt.de", "Post", ONE_PDF)
                .isEmpty());
    }
}
