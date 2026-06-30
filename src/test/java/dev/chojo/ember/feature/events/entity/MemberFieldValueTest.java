/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemberFieldValueTest {

    @Test
    void parseEmpty() {
        assertTrue(MemberFieldValue.parseIds(null).isEmpty());
        assertTrue(MemberFieldValue.parseIds("").isEmpty());
        assertTrue(MemberFieldValue.parseIds("   ").isEmpty());
    }

    @Test
    void parseSingleScalar() {
        assertEquals(List.of(42), MemberFieldValue.parseIds("42"));
        assertEquals(List.of(42), MemberFieldValue.parseIds("\"42\""));
    }

    @Test
    void parseJsonArray() {
        assertEquals(List.of(1, 2, 3), MemberFieldValue.parseIds("[1,2,3]"));
        assertEquals(List.of(7), MemberFieldValue.parseIds("[7]"));
        assertTrue(MemberFieldValue.parseIds("[]").isEmpty());
    }

    @Test
    void parseGarbageStaysEmpty() {
        assertTrue(MemberFieldValue.parseIds("abc").isEmpty());
        assertTrue(MemberFieldValue.parseIds("[a,b]").isEmpty());
    }

    @Test
    void formatList() {
        assertEquals("[]", MemberFieldValue.formatList(List.of()));
        assertEquals("[1,2]", MemberFieldValue.formatList(List.of(1, 2)));
    }

    @Test
    void formatSingle() {
        assertEquals("", MemberFieldValue.formatSingle(null));
        assertEquals("9", MemberFieldValue.formatSingle(9));
    }
}
