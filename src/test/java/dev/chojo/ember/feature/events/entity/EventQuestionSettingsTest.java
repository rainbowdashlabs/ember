/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionKind;
import dev.chojo.ember.feature.question.QuestionRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventQuestionSettingsTest {

    @Test
    void parseEmptyReturnsEmpty() {
        var empty = EventQuestionSettings.parse("{}");
        assertNull(empty.options());
        assertNull(empty.groupId());
        assertNull(empty.userType());
        assertNull(empty.tagId());
        assertFalse(empty.selfRegistration());
        assertSame(empty, EventQuestionSettings.parse(null));
        assertSame(empty, EventQuestionSettings.parse(""));
        assertSame(empty, EventQuestionSettings.parse("  "));
    }

    @Test
    void parseInvalidReturnsEmpty() {
        var fallback = EventQuestionSettings.parse("not json");
        assertSame(fallback, EventQuestionSettings.parse("{"));
    }

    @Test
    void anOrganisersQuestionRoundTripsThroughTheColumnAndTheWire() {
        var wire = new EventFieldConfig(List.of("a", "b"), 7, StationUserType.TEAM, 12, true, "half", true);
        var parsed = EventQuestionSettings.parse(wire.settings().toJson());

        assertEquals(List.of("a", "b"), parsed.options());
        assertEquals(7, parsed.groupId());
        assertEquals(StationUserType.TEAM, parsed.userType());
        assertEquals(12, parsed.tagId());
        assertTrue(parsed.selfRegistration());
        assertEquals("half", parsed.width());
        assertTrue(parsed.perDate());
        assertEquals(wire, EventFieldConfig.of(parsed));
    }

    @Test
    void aRegistrantsQuestionRoundTripsThroughTheColumnAndTheScreen() {
        var screen = new EventQuestionSettings(null, null, null, null, null, false, false, true, "2", 0, 5, true);
        var parsed = EventQuestionSettings.parse(screen.toJson());

        assertTrue(parsed.required());
        assertEquals("2", parsed.defaultValue());
        assertEquals(0, parsed.min());
        assertEquals(5, parsed.max());
        assertTrue(parsed.managersOnly());
        assertEquals(screen, parsed.registrants());
    }

    @Test
    void unknownKeysAreIgnored() {
        var parsed = EventQuestionSettings.parse("{\"options\":[\"x\"],\"unknownKey\":42}");
        assertEquals(List.of("x"), parsed.options());
    }

    @Test
    void aMemberQuestionCarriesItsNarrowing() {
        var question = EventQuestionSettings.parse("{\"groupId\":3}").asQuestion("Fahrer", FieldType.MEMBER_OF_GROUP);

        assertEquals(QuestionKind.MEMBER, question.kind());
        assertInstanceOf(QuestionRules.Members.class, question.rules());
    }

    @Test
    void aTypeThatHoldsNoAnswerAsksNothing() {
        assertThrows(IllegalArgumentException.class, () -> EventQuestionSettings.empty()
                .asQuestion("Abschnitt", FieldType.SECTION));
    }
}
