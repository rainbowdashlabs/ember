/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.PronounRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The languages a gender field gives pronouns in, and the two predefined answers in each. */
class PronounLanguageTest {
    @Test
    void germanTellsTheDativeApartAndEnglishDoesNot() {
        var languages = PronounLanguage.all();

        assertEquals(
                List.of(DocumentLanguage.DE, DocumentLanguage.EN),
                languages.stream().map(PronounLanguage::language).toList());
        assertEquals(
                List.of("de", "en"),
                languages.stream().map(PronounLanguage::code).toList());
        assertEquals(List.of(PronounRole.values()), languages.getFirst().roles());
        assertEquals(
                List.of(PronounRole.SUBJECT, PronounRole.OBJECT, PronounRole.POSSESSIVE),
                languages.getLast().roles());
        assertEquals(
                new PronounSet("er", "ihn", "ihm", "sein"), languages.getFirst().male());
        assertEquals(
                new PronounSet("she", "her", null, "her"), languages.getLast().female());
    }

    @Test
    void theRolesAreNamedByThePredefinedWords() {
        assertArrayEquals(new String[] {"ihm", "ihr"}, PronounRole.DATIVE.examples(DocumentLanguage.DE));
        assertArrayEquals(new String[] {"him", "her"}, PronounRole.DATIVE.examples(DocumentLanguage.EN));
        assertEquals("Whose (his / her)", PronounRole.POSSESSIVE.title(DocumentLanguage.EN));
    }
}
