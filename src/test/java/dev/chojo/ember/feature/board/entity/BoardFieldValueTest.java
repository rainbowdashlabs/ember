/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How a board ticket's custom field value is read into the record its field type names, and the
 * shape it keeps.
 *
 * <p>Reading only asks that the value fits the record: a date reads as any text and a choice as any
 * word. Whether a date is a day and a choice one of the options is asked when the value is saved,
 * which {@code BoardFieldValueRouteTest} pins.
 */
class BoardFieldValueTest {

    private static String kept(FieldType type, String sent) {
        var value = BoardFieldValue.parse(type, sent);
        return value == null ? null : value.toJson();
    }

    @Test
    void aLineOfTextIsKeptInAnObject() {
        assertEquals("{\"value\":\"Florian\"}", kept(FieldType.TEXT, "{\"value\":\"Florian\"}"));
    }

    @Test
    void aNumberReadsAsAFractionAndTextIsRefused() {
        assertEquals("{\"value\":2.5}", kept(FieldType.NUMBER, "{\"value\":2.5}"));
        assertEquals("{\"value\":3.0}", kept(FieldType.NUMBER, "{\"value\":3}"));
        assertNull(kept(FieldType.NUMBER, "{\"value\":\"drei\"}"));
    }

    @Test
    void aYesOrNoIsKeptAsABoolean() {
        assertEquals("{\"value\":true}", kept(FieldType.BOOLEAN, "{\"value\":true}"));
        assertNull(kept(FieldType.BOOLEAN, "{\"value\":\"ja\"}"));
    }

    @Test
    void aDateReadsAsAnyText() {
        assertEquals("{\"value\":\"irgendwann\"}", kept(FieldType.DATE, "{\"value\":\"irgendwann\"}"));
    }

    @Test
    void aChoiceReadsAsAnyWord() {
        assertEquals("{\"value\":\"XL\"}", kept(FieldType.CHOICE, "{\"value\":\"XL\"}"));
    }

    @Test
    void aLaneAssigneeIsKeptAsAMemberId() {
        assertEquals("{\"memberId\":4}", kept(FieldType.LANE_ASSIGNEE, "{\"memberId\":4}"));
    }

    @Test
    void nothingAndNoDocumentAreNoValue() {
        assertNull(kept(FieldType.TEXT, ""));
        assertNull(kept(FieldType.TEXT, "kein json"));
    }
}
