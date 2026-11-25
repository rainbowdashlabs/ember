/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field of an event group shown on one of its parts carries the negated id of the event group's
 * field, and only such a field names one. An own field replacing a group field tied to the attendance
 * sheet fills in that sheet field, unless it is tied itself or of another kind.
 */
class AppointmentFieldTest {
    private static AppointmentField withId(int id) {
        return new AppointmentField(
                id, 4, "Fahrer", FieldType.TEXT, EventQuestionSettings.empty(), "", 0, false, null, false);
    }

    @Test
    void anInheritedFieldNamesTheEventGroupsField() {
        var field = withId(-12);

        assertTrue(field.inherited());
        assertEquals(12, field.groupFieldId());
    }

    @Test
    void anAppointmentsOwnFieldNamesNoEventGroupField() {
        var field = withId(12);

        assertFalse(field.inherited());
        assertThrows(IllegalStateException.class, field::groupFieldId);
    }

    private static AppointmentField field(int id, String name, String value, Integer sheetFieldId) {
        return field(id, name, FieldType.TEXT, value, sheetFieldId);
    }

    private static AppointmentField field(int id, String name, FieldType type, String value, Integer sheetFieldId) {
        return new AppointmentField(
                id, 4, name, type, EventQuestionSettings.empty(), value, 0, false, sheetFieldId, false);
    }

    @Test
    void anOwnFieldOfAnotherKindDoesNotTakeOverTheTie() {
        var group = field(-3, "Ausbilder", FieldType.MEMBER_LIST, "", 70);
        var own = field(9, "Ausbilder", FieldType.TEXT, "Hans Meier, extern", null);

        var filling = AppointmentField.fillingTheSheet(List.of(own), List.of(group));

        assertEquals(1, filling.size());
        assertNull(filling.getFirst().attendanceFieldId());
    }

    @Test
    void anOwnFieldReplacingATiedGroupFieldTakesOverItsTie() {
        var group = field(-3, "Thema", "Knoten", 70);
        var own = field(9, " thema ", "Leinen", null);

        var filling = AppointmentField.fillingTheSheet(List.of(own), List.of(group));

        assertEquals(1, filling.size());
        assertEquals("Leinen", filling.getFirst().value());
        assertEquals(70, filling.getFirst().attendanceFieldId());
    }

    @Test
    void anOwnTieWinsAndUnreplacedGroupFieldsKeepTheirs() {
        var replaced = field(-3, "Thema", "Knoten", 70);
        var kept = field(-4, "Ausbilder", "", 71);
        var own = field(9, "Thema", "Leinen", 72);

        var filling = AppointmentField.fillingTheSheet(List.of(own), List.of(replaced, kept));

        assertEquals(
                List.of(71, 72),
                filling.stream().map(AppointmentField::attendanceFieldId).toList());
    }
}
