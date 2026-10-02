/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.question.MemberConstraint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EventFieldTypeTest {

    @Test
    void everyWireNameIsOneTypeAnAppointmentOffersAndBack() {
        var shared = Arrays.stream(EventFieldType.values())
                .map(EventFieldType::fieldType)
                .collect(Collectors.toSet());

        assertEquals(FieldTypes.APPOINTMENT, shared);
        for (var type : EventFieldType.values()) {
            assertEquals(type, EventFieldType.of(type.fieldType()), type.name());
        }
    }

    @Test
    void theRenamedOnesMapOntoTheirSharedNames() {
        assertEquals(FieldType.TEXT, EventFieldType.STRING.fieldType());
        assertEquals(FieldType.LONG_TEXT, EventFieldType.TEXTAREA.fieldType());
        assertEquals(FieldType.CHOICE, EventFieldType.ENUM.fieldType());
        assertEquals(FieldType.LOCATION, EventFieldType.LOCATION.fieldType());
    }

    @Test
    void aTypeNoAppointmentOffersHasNoWireName() {
        assertThrows(IllegalArgumentException.class, () -> EventFieldType.of(FieldType.BIRTH_DATE));
    }

    @Test
    void memberQuestionsKeepTheirNarrowing() {
        for (var type : EventFieldType.values()) {
            assertEquals(type.name().startsWith("MEMBER"), type.fieldType().namesMembers(), type.name());
        }
        assertEquals(
                MemberConstraint.GROUP,
                EventFieldType.MEMBER_LIST_OF_GROUP.fieldType().constraint());
        assertEquals(
                MemberConstraint.USER_TYPE,
                EventFieldType.MEMBER_OF_TYPE.fieldType().constraint());
        assertEquals(
                MemberConstraint.TAG,
                EventFieldType.MEMBER_LIST_OF_TAG.fieldType().constraint());
        assertEquals(MemberConstraint.NONE, EventFieldType.MEMBER.fieldType().constraint());
    }
}
