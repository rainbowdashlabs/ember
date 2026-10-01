/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the appointment list prints in a column chosen from an appointment's own fields.
 *
 * <p>The fields keep their values as bare text, so a member field held a number and a yes the word
 * {@code true} until the list printed them the way every other export does.
 */
class EventExportFieldCellsTest {

    private static AppointmentField fieldOf(String name, FieldType type, String value) {
        return new AppointmentField(1, 1, name, type, EventQuestionSettings.empty(), value, 0, false, null, false);
    }

    private static final List<AppointmentField> FIELDS = List.of(
            fieldOf("Fahrzeug", FieldType.BOOLEAN, "1"),
            fieldOf("Bereitschaft", FieldType.BOOLEAN, "false"),
            fieldOf("Leitung", FieldType.MEMBER_OF_GROUP, "7"),
            fieldOf("Helfer", FieldType.MEMBER_LIST_OF_TAG, "[7,9]"),
            fieldOf("Treffpunkt", FieldType.LOCATION, "Halle 3"),
            fieldOf("Beginn", FieldType.TIME, "18:30:00"),
            fieldOf("Stichtag", FieldType.DATE, "2026-03-06"),
            fieldOf("Notiz", FieldType.TEXT, ""));

    @Test
    void theListPrintsEachFieldAsEveryExportDoes() {
        var cells = EventExportService.fieldCells(FIELDS, Map.of(7, "Anna Berg"), "de");

        assertEquals("Ja", cells.get("Fahrzeug"));
        assertEquals("Nein", cells.get("Bereitschaft"));
        assertEquals("Anna Berg", cells.get("Leitung"));
        assertEquals("Anna Berg, 9", cells.get("Helfer"));
        assertEquals("Halle 3", cells.get("Treffpunkt"));
        assertEquals("18:30", cells.get("Beginn"));
        assertEquals("06.03.2026", cells.get("Stichtag"));
        assertEquals("", cells.get("Notiz"));
    }

    @Test
    void aYesFollowsTheStationsLanguage() {
        var cells = EventExportService.fieldCells(FIELDS, Map.of(), "en");

        assertEquals("Yes", cells.get("Fahrzeug"));
        assertEquals("No", cells.get("Bereitschaft"));
    }
}
