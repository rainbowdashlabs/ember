/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.service.SubjectMemberMatch.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectMemberMatchTest {

    private static final Candidate ANNA = new Candidate(1, "Anna Weber");
    private static final Candidate BERND = new Candidate(2, "Bernd Weber");
    private static final Candidate CLARA = new Candidate(3, "Clara Schmidt");

    @Test
    void aSubjectNamingOneMemberFindsThem() {
        var found = SubjectMemberMatch.soleMatch("Bescheinigung Anna Weber 2026", List.of(ANNA, CLARA));

        assertEquals(1, found.orElseThrow().memberId());
    }

    @Test
    void theOrderTheNameIsWrittenInDoesNotMatter() {
        assertEquals(1, SubjectMemberMatch.soleMatch("Weber, Anna: Attest", List.of(ANNA, CLARA))
                .orElseThrow()
                .memberId());
        assertEquals(3, SubjectMemberMatch.soleMatch("SCHMIDT CLARA untersuchung", List.of(ANNA, CLARA))
                .orElseThrow()
                .memberId());
    }

    /**
     * The guard the whole mechanism turns on. A document on the wrong member is worse than a document on
     * none, and a hidden one on the wrong member is worse still.
     */
    @Test
    void twoMembersSharingASurnameBindNobody() {
        assertTrue(SubjectMemberMatch.soleMatch("Bescheinigung Weber", List.of(ANNA, BERND)).isEmpty());
    }

    @Test
    void aSubjectNamingTwoDifferentPeopleBindsNobody() {
        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber und Clara Schmidt", List.of(ANNA, CLARA, BERND))
                .isEmpty());
    }

    @Test
    void aSubjectNamingNobodyBindsNobody() {
        assertTrue(SubjectMemberMatch.soleMatch("Rechnung 2026-04", List.of(ANNA, CLARA)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("", List.of(ANNA)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch(null, List.of(ANNA)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber", List.of()).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber", null).isEmpty());
    }

    /**
     * Not an ambiguity: one of the two is named completely and the other only in part, so the subject
     * does say which. Treating this as ambiguous would make a double-barrelled name unmatchable at any
     * station that also has the shorter one.
     */
    @Test
    void aNameSittingInsideALongerOneIsNotAnAmbiguity() {
        var weberSchmidt = new Candidate(4, "Anna Weber-Schmidt");

        var found = SubjectMemberMatch.soleMatch("Attest Anna Weber-Schmidt", List.of(ANNA, weberSchmidt));

        assertEquals(4, found.orElseThrow().memberId());
    }

    /** Two names of the same length, neither inside the other, is a real ambiguity. */
    @Test
    void twoEquallyCompleteNamesBindNobody() {
        var annaMeyer = new Candidate(5, "Anna Meyer");

        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber Anna Meyer", List.of(ANNA, annaMeyer))
                .isEmpty());
    }

    @Test
    void aMemberWithNoNameToSpeakOfIsNeverGuessedAt() {
        var nameless = new Candidate(6, "   ");

        assertTrue(SubjectMemberMatch.soleMatch("Bescheinigung", List.of(nameless)).isEmpty());
    }
}
