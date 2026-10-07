/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keys with and without a date format, read from a text and filled back into it. */
class PlaceholderTokensTest {
    @Test
    void aFormatIsPartOfTheKeyWithoutTheSpacesAroundIt() {
        String text = "{{ member.birthDate }} {{member.birthDate|long}} {{ today | T. MMMM JJJJ }} {{a|\"b}}";

        assertEquals(
                List.of("member.birthDate", "member.birthDate|long", "today|T. MMMM JJJJ"),
                PlaceholderTokens.occurrences(text));
        assertEquals(
                "1 2 3 {{a|\"b}}",
                PlaceholderTokens.fill(
                        text,
                        Map.of("member.birthDate", "1", "member.birthDate|long", "2", "today|T. MMMM JJJJ", "3")));
    }

    @Test
    void aKeyReadsAsADateAndItsFormat() {
        assertEquals(
                new PlaceholderKey("member.joinDate", "monthYear"), PlaceholderKey.parse("member.joinDate|monthYear"));
        assertEquals(new PlaceholderKey("today.long", null), PlaceholderKey.parse("today.long"));
        assertEquals(new PlaceholderKey("today", null), PlaceholderKey.parse("today"));
        assertEquals(
                BuiltInPlaceholder.TODAY, BuiltInPlaceholder.of("today|year").orElseThrow());
    }

    @Test
    void anOwnFormatIsMadeOfTokensAndSeparatorsOnly() {
        assertTrue(DateFormat.of("TT.MM.JJJJ hh:mm").orElseThrow().readsClock());
        assertFalse(DateFormat.of("TTTT, T. MMMM JJ").orElseThrow().readsClock());
        assertTrue(DateFormat.of("TT.MM.JJJJ Uhr").isEmpty());
        assertTrue(DateFormat.of("TTTTT").isEmpty());
        assertTrue(DateFormat.of("JJJ").isEmpty());
        assertTrue(DateFormat.of(" .-").isEmpty());
        assertTrue(DateFormat.of("T'x'").isEmpty());
        assertTrue(DateFormat.of("T.".repeat(21)).isEmpty());
        assertTrue(DateFormat.of("T.".repeat(20)).isPresent());
    }
}
