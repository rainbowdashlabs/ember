/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.PronounForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The four forms in both columns and with the name, in German and in English.
 */
class PronounsTest {

    @Test
    void theGermanColumns() {
        assertEquals("er", Pronouns.of(PronounForm.ER, Pronouns.Case.SUBJECT, "Max", "de", false));
        assertEquals("ihn", Pronouns.of(PronounForm.ER, Pronouns.Case.OBJECT, "Max", "de", false));
        assertEquals("ihm", Pronouns.of(PronounForm.ER, Pronouns.Case.DATIVE, "Max", "de", false));
        assertEquals("sein", Pronouns.of(PronounForm.ER, Pronouns.Case.POSSESSIVE, "Max", "de", false));
        assertEquals("sie", Pronouns.of(PronounForm.SIE, Pronouns.Case.SUBJECT, "Lena", "de", false));
        assertEquals("sie", Pronouns.of(PronounForm.SIE, Pronouns.Case.OBJECT, "Lena", "de", false));
        assertEquals("ihr", Pronouns.of(PronounForm.SIE, Pronouns.Case.DATIVE, "Lena", "de", false));
        assertEquals("Ihr", Pronouns.of(PronounForm.SIE, Pronouns.Case.POSSESSIVE, "Lena", "de", true));
    }

    @Test
    void theNameStandsForEveryPronounAndItsGenitiveForThePossessive() {
        assertEquals("Lena", Pronouns.of(PronounForm.NAME, Pronouns.Case.SUBJECT, "Lena", "de", true));
        assertEquals("Lena", Pronouns.of(PronounForm.NAME, Pronouns.Case.DATIVE, "Lena", "de", false));
        assertEquals("Lenas", Pronouns.of(PronounForm.NAME, Pronouns.Case.POSSESSIVE, "Lena", "de", false));
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
    void theEnglishColumns() {
        assertEquals("He", Pronouns.of(PronounForm.ER, Pronouns.Case.SUBJECT, "Max", "en", true));
        assertEquals("her", Pronouns.of(PronounForm.SIE, Pronouns.Case.DATIVE, "Lena", "en", false));
        assertEquals("his", Pronouns.of(PronounForm.ER, Pronouns.Case.POSSESSIVE, "Max", "en", false));
        assertEquals("Lena's", Pronouns.of(PronounForm.NAME, Pronouns.Case.POSSESSIVE, "Lena", "en", false));
        assertEquals("James'", Pronouns.genitive("James", "en"));
    }
}
