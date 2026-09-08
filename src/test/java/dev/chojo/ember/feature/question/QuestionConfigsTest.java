/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings a question carries, read and written the one way.
 *
 * <p>What is on disk is what matters here: five features hold years of stored settings, and the one
 * reader has to give back exactly what each of them wrote, down to the property names. A round trip
 * that loses a setting loses it for every station at once.
 */
class QuestionConfigsTest {

    @Test
    void aProfileFieldReadsAndWritesWhatIsStored() {
        String stored = """
                {"required":true,"description":"Wozu","readonly":false,"notifyOnChange":true,"overview":true,\
                "options":["S","M"],"defaultValue":"M","computed":false,"sourceField":null,"ageMode":null,\
                "groupId":7,"width":"half"}""";
        var config = ProfileFieldConfig.parse(stored);

        assertTrue(config.required());
        assertEquals("Wozu", config.description());
        assertEquals(List.of("S", "M"), config.options());
        assertEquals("M", config.defaultValue());
        assertEquals(7, config.groupId());
        assertEquals("half", config.width());

        assertEquals(config, ProfileFieldConfig.parse(config.toJson()), "what is written reads back the same");
    }

    @Test
    void anAttendanceFieldReadsAndWritesWhatIsStored() {
        String stored = """
                {"required":true,"groupId":3,"autoAttend":true,"options":["Ja","Nein"],"defaultValue":"Ja",\
                "width":"third"}""";
        var config = AttendanceFieldConfig.parse(stored);

        assertTrue(config.required());
        assertTrue(config.autoAttend());
        assertEquals(3, config.groupId());
        assertEquals(List.of("Ja", "Nein"), config.options());
        assertEquals("Ja", config.defaultValue());

        assertEquals(config, AttendanceFieldConfig.parse(config.toJson()));
    }

    @Test
    void aWaitingListFieldReadsAndWritesWhatIsStored() {
        var config = WaitingListFieldConfig.parse("{\"options\":[\"A\",\"B\"],\"placeholder\":\"Bitte\"}");

        assertEquals(List.of("A", "B"), config.options());
        assertEquals("Bitte", config.placeholder());
        assertEquals(config, WaitingListFieldConfig.parse(config.toJson()));
    }

    @Test
    void anAppointmentFieldReadsAndWritesWhatIsStored() {
        var config = EventFieldConfig.parse(
                "{\"options\":[\"A\"],\"groupId\":4,\"userType\":\"MEMBER\",\"tagId\":9,\"selfRegistration\":true,\"width\":\"full\"}");

        assertEquals(List.of("A"), config.options());
        assertEquals(4, config.groupId());
        assertEquals(9, config.tagId());
        assertTrue(config.selfRegistration());
        assertEquals(config, EventFieldConfig.parse(config.toJson()));
    }

    @Test
    void aRegistrationQuestionReadsAndWritesWhatIsStored() {
        var config = EventRegistrationFieldConfig.parse(
                "{\"required\":true,\"defaultValue\":\"2\",\"options\":null,\"min\":0,\"max\":5,\"managersOnly\":true}");

        assertTrue(config.required());
        assertEquals("2", config.defaultValue());
        assertEquals(0, config.min());
        assertEquals(5, config.max());
        assertTrue(config.managersOnly());
        assertEquals(config, EventRegistrationFieldConfig.parse(config.toJson()));
    }

    /**
     * A column that says nothing, or says something unreadable, is a field to put right rather than
     * a feature to take out: every one of the five answers with its empty settings.
     */
    @Test
    void nothingAndNonsenseReadAsNoSettingsAtAll() {
        for (String said : List.of("", "   ", "{}", "not json at all")) {
            assertEquals(ProfileFieldConfig.empty(), ProfileFieldConfig.parse(said), said);
            assertEquals(AttendanceFieldConfig.parse("{}"), AttendanceFieldConfig.parse(said), said);
            assertEquals(WaitingListFieldConfig.EMPTY, WaitingListFieldConfig.parse(said), said);
            assertEquals(EventFieldConfig.empty(), EventFieldConfig.parse(said), said);
            assertEquals(EventRegistrationFieldConfig.empty(), EventRegistrationFieldConfig.parse(said), said);
        }
        assertEquals(ProfileFieldConfig.empty(), ProfileFieldConfig.parse(null));
    }

    /**
     * The settings each feature stores are the same settings, which is what lets one check measure
     * an answer to any of them: the options of a choice, the bounds of a number, what it starts from
     * and whether it has to be answered.
     */
    @Test
    void theFiveHandOverTheSameSettings() {
        var choice = ProfileFieldConfig.parse("{\"required\":true,\"options\":[\"S\"],\"defaultValue\":\"S\"}")
                .settings();
        assertTrue(choice.required());
        assertEquals(List.of("S"), choice.options());
        assertEquals("S", choice.defaultValue());

        var bounded =
                EventRegistrationFieldConfig.parse("{\"min\":1,\"max\":4}").settings();
        assertEquals(BigDecimal.ONE, bounded.min());
        assertEquals(BigDecimal.valueOf(4), bounded.max());
        assertFalse(bounded.required());

        var listed = WaitingListFieldConfig.parse("{\"options\":[\"A\"]}").settings();
        assertEquals(List.of("A"), listed.options());
    }

    /** The rules follow the kind, so a choice is measured by its options and a date by being one. */
    @Test
    void whatIsMeasuredFollowsTheKind() {
        var settings =
                QuestionSettings.required(true).withOptions(List.of("S", "M")).withBounds(1, 3);

        assertEquals(
                QuestionRules.choice(List.of("S", "M")),
                settings.asQuestion("Größe", QuestionKind.CHOICE).rules());
        assertEquals(
                QuestionRules.bounds(1, 3),
                settings.asQuestion("Gäste", QuestionKind.NUMBER).rules());
        assertEquals(
                QuestionRules.none(),
                settings.asQuestion("Geburtstag", QuestionKind.DATE).rules());
    }
}
