/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a board ticket takes in a custom field today, and the shape it keeps.
 *
 * <p>The only measure is that the value reads as the record its field type names: a date is any
 * text and a choice is any word. Written down before the field types are brought together, so the
 * change that starts checking them shows exactly what it turned away.
 */
class BoardFieldValueTest {

    private static String kept(BoardFieldType type, String sent) {
        var value = BoardFieldValue.parse(type, sent);
        return value == null ? null : value.toJson();
    }

    @Test
    void aLineOfTextIsKeptInAnObject() {
        assertEquals("{\"value\":\"Florian\"}", kept(BoardFieldType.STRING, "{\"value\":\"Florian\"}"));
    }

    @Test
    void aNumberIsKeptAsAFractionAndTextIsRefused() {
        assertEquals("{\"value\":2.5}", kept(BoardFieldType.NUMBER, "{\"value\":2.5}"));
        assertEquals("{\"value\":3.0}", kept(BoardFieldType.NUMBER, "{\"value\":3}"));
        assertNull(kept(BoardFieldType.NUMBER, "{\"value\":\"drei\"}"));
    }

    @Test
    void aYesOrNoIsKeptAsABoolean() {
        assertEquals("{\"value\":true}", kept(BoardFieldType.BOOLEAN, "{\"value\":true}"));
        assertNull(kept(BoardFieldType.BOOLEAN, "{\"value\":\"ja\"}"));
    }

    @Test
    void aDateTakesAnyText() {
        assertEquals("{\"value\":\"irgendwann\"}", kept(BoardFieldType.DATE, "{\"value\":\"irgendwann\"}"));
    }

    @Test
    void aChoiceTakesAnyWordWhateverItOffers() {
        assertEquals("{\"value\":\"XL\"}", kept(BoardFieldType.ENUM, "{\"value\":\"XL\"}"));
    }

    @Test
    void aLaneAssigneeIsKeptAsAMemberId() {
        assertEquals("{\"memberId\":4}", kept(BoardFieldType.LANE_ASSIGNEE, "{\"memberId\":4}"));
    }

    @Test
    void nothingAndNoDocumentAreNoValue() {
        assertNull(kept(BoardFieldType.STRING, ""));
        assertNull(kept(BoardFieldType.STRING, "kein json"));
    }
}
