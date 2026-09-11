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
    private static final Candidate ANNA_MARIA = new Candidate(4, "Anna Maria Weber");
    private static final Candidate WEBER_SCHMIDT = new Candidate(5, "Anna Weber-Schmidt");
    private static final Candidate ANNA_MUELLER = new Candidate(6, "Anna Müller");
    private static final Candidate TOM = new Candidate(7, "Tom Weber");

    private static int found(String subject, List<Candidate> candidates) {
        return SubjectMemberMatch.soleMatch(subject, candidates).orElseThrow().memberId();
    }

    /**
     * Leaving out a middle name is what whoever types a subject line actually does, and the whole reason
     * the test is a score rather than every word of the name having to be there.
     */
    @Test
    void aSubjectSayingMostOfANameStillFindsTheMember() {
        assertEquals(4, found("Bescheinigung Anna Weber 2026", List.of(ANNA_MARIA, CLARA)));
    }

    @Test
    void theOrderTheNameIsWrittenInDoesNotMatter() {
        assertEquals(1, found("Weber, Anna: Attest", List.of(ANNA, CLARA)));
        assertEquals(3, found("SCHMIDT CLARA untersuchung", List.of(ANNA, CLARA)));
    }

    /** Everything between the words is a separator, so a file name reads the same as a sentence. */
    @Test
    void aNameRunTogetherWithPunctuationIsStillRead() {
        assertEquals(1, found("Bescheinigung_Anna-Weber.pdf", List.of(ANNA, CLARA)));
    }

    /** Mueller, Müller and Muller are one word, whichever of them was typed. */
    @Test
    void aNameSpelledWithAnUmlautOrAroundOneIsTheSameName() {
        assertEquals(6, found("Attest Mueller Anna", List.of(ANNA_MUELLER, CLARA)));
        assertEquals(6, found("Attest Müller Anna", List.of(ANNA_MUELLER, CLARA)));
        assertEquals(6, found("Attest Muller Anna", List.of(ANNA_MUELLER, CLARA)));
    }

    /**
     * The guard the whole mechanism turns on. A bare surname is not a name, however unique it is in the
     * station, because a document on the wrong member is worse than a document on none.
     */
    @Test
    void oneWordOfANameNeverBindsAnybody() {
        assertTrue(SubjectMemberMatch.soleMatch("Rechnung Weber", List.of(ANNA)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("Rechnung Anna", List.of(ANNA)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("Weber", List.of(ANNA, TOM)).isEmpty());
    }

    /**
     * Words are compared whole and never as parts of one another. Partial matching without this would let
     * a subject about Anastasia reach a member called Ana.
     */
    @Test
    void aLongerWordDoesNotCountAsTheShorterOneInsideIt() {
        var ana = new Candidate(8, "Ana Weber");

        assertTrue(SubjectMemberMatch.soleMatch("Anastasia Weber", List.of(ana)).isEmpty());
    }

    @Test
    void twoMembersSharingASurnameBindNobody() {
        assertTrue(SubjectMemberMatch.soleMatch("Bescheinigung Weber", List.of(ANNA, BERND))
                .isEmpty());
    }

    @Test
    void aSubjectNamingTwoDifferentPeopleBindsNobody() {
        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber und Clara Schmidt", List.of(ANNA, CLARA, BERND))
                .isEmpty());
    }

    @Test
    void aSubjectNamingNobodyBindsNobody() {
        assertTrue(SubjectMemberMatch.soleMatch("Rechnung 2026-04", List.of(ANNA, CLARA))
                .isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("", List.of(ANNA)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch(null, List.of(ANNA)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber", List.of()).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber", null).isEmpty());
    }

    /**
     * Not an ambiguity: the subject says all of one name and only part of the other, so it does say which
     * of the two it means. Both directions of that are worth holding, because a station with both members
     * has to be able to file for either of them.
     */
    @Test
    void theMemberTheSubjectNamesMoreCompletelyWins() {
        assertEquals(1, found("Anna Weber", List.of(ANNA, WEBER_SCHMIDT)));
        assertEquals(5, found("Anna Weber-Schmidt", List.of(ANNA, WEBER_SCHMIDT)));
    }

    /** Two names the subject fits exactly as well is a real ambiguity, and an ambiguity binds nobody. */
    @Test
    void twoEquallyCompleteNamesBindNobody() {
        var annaMeyer = new Candidate(9, "Anna Meyer");

        assertTrue(SubjectMemberMatch.soleMatch("Anna Weber Anna Meyer", List.of(ANNA, annaMeyer))
                .isEmpty());
    }

    /** A single word name is the one case where every word of it has to be there, since there is one. */
    @Test
    void aMemberKnownByOneWordIsFoundByThatWord() {
        var cher = new Candidate(10, "Cher");

        assertEquals(10, found("Attest Cher 2026", List.of(cher, ANNA)));
    }

    @Test
    void aMemberWithNoNameToSpeakOfIsNeverGuessedAt() {
        var nameless = new Candidate(11, "   ");
        var initial = new Candidate(12, "A B");

        assertTrue(
                SubjectMemberMatch.soleMatch("Bescheinigung", List.of(nameless)).isEmpty());
        assertTrue(SubjectMemberMatch.soleMatch("Bescheinigung A B", List.of(initial))
                .isEmpty());
    }
}
