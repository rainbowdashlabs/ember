/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An answer as an export prints it, kind by kind and in both export languages.
 */
class QuestionTextTest {

    private static final Map<Integer, String> NAMES = Map.of(1, "Anna Berg", 2, "Ben Kurz");

    private static String de(FieldType type, String stored) {
        return QuestionText.format(type, stored, NAMES, "de");
    }

    private static String en(FieldType type, String stored) {
        return QuestionText.format(type, stored, NAMES, "en");
    }

    @Test
    void yesAndNoFollowTheExportLanguage() {
        assertEquals("Ja", de(FieldType.BOOLEAN, "true"));
        assertEquals("Nein", de(FieldType.BOOLEAN, "\"false\""));
        assertEquals("Yes", en(FieldType.BOOLEAN, "1"));
        assertEquals("No", en(FieldType.BOOLEAN, "0"));
        assertEquals("vielleicht", de(FieldType.BOOLEAN, "vielleicht"));
    }

    @Test
    void aDateReadsAsADay() {
        assertEquals("01.09.2011", de(FieldType.DATE, "\"2011-09-01\""));
        assertEquals("24.12.2020", en(FieldType.BIRTH_DATE, "2020-12-24T00:00:00Z"));
        assertEquals("bald", de(FieldType.EXPIRY_DATE, "bald"));
    }

    @Test
    void aTimeReadsAsHoursAndMinutes() {
        assertEquals("18:30", de(FieldType.TIME, "18:30:00"));
        assertEquals("abends", de(FieldType.TIME, "abends"));
    }

    @Test
    void membersReadByName() {
        assertEquals("Anna Berg", de(FieldType.MEMBER_OF_GROUP, "1"));
        assertEquals("Anna Berg, Ben Kurz, 9", de(FieldType.MEMBER_LIST, "[1,2,9]"));
        assertEquals("Ben Kurz", de(FieldType.LANE_ASSIGNEE, "\"2\""));
        assertEquals("Paul", de(FieldType.MEMBER, "Paul"));
    }

    @Test
    void everythingElseReadsAsStored() {
        assertEquals("Mischkost", de(FieldType.CHOICE, "\"Mischkost\""));
        assertEquals("2.5", de(FieldType.NUMBER, "2.5"));
        assertEquals("Halle 3", de(FieldType.LOCATION, "Halle 3"));
    }

    @Test
    void nothingAnsweredAndNothingHeldReadEmpty() {
        assertEquals("", de(FieldType.TEXT, null));
        assertEquals("", de(FieldType.TEXT, "null"));
        assertEquals("", de(FieldType.SECTION, "\"Kopf\""));
        assertEquals("", de(FieldType.AGE, "12"));
    }
}
