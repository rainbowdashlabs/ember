/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.PossessiveEnding;
import dev.chojo.ember.feature.generator.entity.PronounKey;
import dev.chojo.ember.feature.generator.entity.PronounRole;
import dev.chojo.ember.feature.members.entity.PronounSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The words a pronoun key writes: from a gender answer's pronouns in the template's language, with the
 * ending a possessive takes, or the first name and its genitive where there are none.
 */
class PronounsTest {
    private static final PronounSet ER = new PronounSet("er", "ihn", "ihm", "sein");
    private static final PronounSet SIE = new PronounSet("sie", "sie", "ihr", "ihr");
    private static final PronounSet HE = new PronounSet("he", "him", null, "his");

    private static PronounKey key(PronounRole role, boolean start, PossessiveEnding ending) {
        return new PronounKey(role, start, ending);
    }

    private static PronounKey key(PronounRole role) {
        return key(role, false, PossessiveEnding.NONE);
    }

    @Test
    void theGermanForms() {
        assertEquals("er", Pronouns.of(key(PronounRole.SUBJECT), ER, "Max", "de"));
        assertEquals("ihn", Pronouns.of(key(PronounRole.OBJECT), ER, "Max", "de"));
        assertEquals("ihm", Pronouns.of(key(PronounRole.DATIVE), ER, "Max", "de"));
        assertEquals("sein", Pronouns.of(key(PronounRole.POSSESSIVE), ER, "Max", "de"));
        assertEquals("ihr", Pronouns.of(key(PronounRole.DATIVE), SIE, "Lena", "de"));
        assertEquals("Ihr", Pronouns.of(key(PronounRole.POSSESSIVE, true, PossessiveEnding.NONE), SIE, "Lena", "de"));
    }

    @Test
    void aPossessiveTakesItsEndingAndTheNameDoesNot() {
        assertEquals("seinen", Pronouns.of(key(PronounRole.POSSESSIVE, false, PossessiveEnding.EN), ER, "Max", "de"));
        assertEquals("ihre", Pronouns.of(key(PronounRole.POSSESSIVE, false, PossessiveEnding.E), SIE, "Lena", "de"));
        assertEquals("Seinem", Pronouns.of(key(PronounRole.POSSESSIVE, true, PossessiveEnding.EM), ER, "Max", "de"));
        assertEquals("Lenas", Pronouns.of(key(PronounRole.POSSESSIVE, false, PossessiveEnding.ES), null, "Lena", "de"));
    }

    @Test
    void theNameStandsForEveryPronounAndItsGenitiveForThePossessive() {
        assertEquals("Lena", Pronouns.of(key(PronounRole.SUBJECT, true, PossessiveEnding.NONE), null, "Lena", "de"));
        assertEquals("Lena", Pronouns.of(key(PronounRole.DATIVE), null, "Lena", "de"));
        assertEquals("Lenas", Pronouns.of(key(PronounRole.POSSESSIVE), null, "Lena", "de"));
    }

    @Test
    void aNameEndingInAHissingSoundTakesAnApostrophe() {
        assertEquals("Max'", Pronouns.genitive("Max", "de"));
        assertEquals("Hans'", Pronouns.genitive("Hans", "de"));
        assertEquals("Fritz'", Pronouns.genitive("Fritz", "de"));
        assertEquals("Joyce'", Pronouns.genitive("Joyce", "de"));
        assertEquals("", Pronouns.genitive("", "de"));
    }

    @Test
    void englishHasNoDativeOfItsOwnAndTheObjectStandsForIt() {
        assertEquals("He", Pronouns.of(key(PronounRole.SUBJECT, true, PossessiveEnding.NONE), HE, "Max", "en"));
        assertEquals("him", Pronouns.of(key(PronounRole.DATIVE), HE, "Max", "en"));
        assertEquals("his", Pronouns.of(key(PronounRole.POSSESSIVE), HE, "Max", "en"));
        assertEquals("Lena's", Pronouns.of(key(PronounRole.POSSESSIVE), null, "Lena", "en"));
        assertEquals("James'", Pronouns.genitive("James", "en"));
    }

    @Test
    void keysAreReadBackAsWritten() {
        for (var pronoun : PronounKey.all()) {
            assertEquals(pronoun, PronounKey.parse(pronoun.key()).orElseThrow());
        }
        assertEquals(
                "pronoun.possessive.start.en",
                key(PronounRole.POSSESSIVE, true, PossessiveEnding.EN).key());
        assertTrue(PronounKey.parse("pronoun.subject.en").isEmpty(), "only a possessive takes an ending");
        assertTrue(PronounKey.parse("pronoun.genitive").isEmpty());
        assertTrue(PronounKey.parse("pronoun.possessive.start.en.e").isEmpty());
        assertTrue(PronounKey.parse("member.firstName").isEmpty());
        assertEquals(
                "seinen / ihren / Vornamens",
                key(PronounRole.POSSESSIVE, false, PossessiveEnding.EN).in("de").label());
    }
}
