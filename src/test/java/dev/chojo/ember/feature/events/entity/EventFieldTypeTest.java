/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventFieldTypeTest {

    @Test
    void memberFieldDetection() {
        for (var t : EventFieldType.values()) {
            boolean expected = t.name().startsWith("MEMBER");
            assertEquals(expected, t.isMemberField(), t.name());
        }
    }

    @Test
    void listFieldDetection() {
        for (var t : EventFieldType.values()) {
            boolean expected = t.name().startsWith("MEMBER_LIST");
            assertEquals(expected, t.isMemberListField(), t.name());
        }
    }

    @Test
    void constraintMapping() {
        assertEquals(EventFieldType.MemberFieldConstraint.NONE, EventFieldType.MEMBER.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.NONE, EventFieldType.MEMBER_LIST.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.GROUP, EventFieldType.MEMBER_OF_GROUP.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.GROUP, EventFieldType.MEMBER_LIST_OF_GROUP.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.USER_TYPE, EventFieldType.MEMBER_OF_TYPE.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.USER_TYPE, EventFieldType.MEMBER_LIST_OF_TYPE.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.TAG, EventFieldType.MEMBER_OF_TAG.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.TAG, EventFieldType.MEMBER_LIST_OF_TAG.constraint());
        assertEquals(EventFieldType.MemberFieldConstraint.NONE, EventFieldType.STRING.constraint());
    }
}
