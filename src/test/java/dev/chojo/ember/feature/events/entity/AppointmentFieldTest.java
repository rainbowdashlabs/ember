/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field of an event group shown on one of its parts carries the negated id of the event group's
 * field, and only such a field names one.
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
}
