/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionValuesTest {

    @Test
    void parseEmpty() {
        assertTrue(QuestionValues.memberIds(null).isEmpty());
        assertTrue(QuestionValues.memberIds("").isEmpty());
        assertTrue(QuestionValues.memberIds("   ").isEmpty());
    }

    @Test
    void parseSingleScalar() {
        assertEquals(List.of(42), QuestionValues.memberIds("42"));
        assertEquals(List.of(42), QuestionValues.memberIds("\"42\""));
    }

    @Test
    void parseJsonArray() {
        assertEquals(List.of(1, 2, 3), QuestionValues.memberIds("[1,2,3]"));
        assertEquals(List.of(7), QuestionValues.memberIds("[7]"));
        assertTrue(QuestionValues.memberIds("[]").isEmpty());
    }

    @Test
    void parseGarbageStaysEmpty() {
        assertTrue(QuestionValues.memberIds("abc").isEmpty());
        assertTrue(QuestionValues.memberIds("[a,b]").isEmpty());
    }

    @Test
    void formatList() {
        assertEquals("[]", QuestionValues.formatMembers(List.of()));
        assertEquals("[1,2]", QuestionValues.formatMembers(List.of(1, 2)));
    }

    @Test
    void formatSingle() {
        assertEquals("", QuestionValues.formatMember(null));
        assertEquals("9", QuestionValues.formatMember(9));
    }
}
