/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.AppointmentTemplateFieldDraft;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What an appointment's own fields and an appointment template's starting values take today, and
 * how the appointment keeps them.
 *
 * <p>Written down before the field types are brought together, so that every later change can show
 * which of these it meant to change and that it left the rest alone.
 */
class AppointmentFieldAnswersTest extends RepositoryTestBase {

    private static EventFieldService service;
    private static EventTemplateService templates;
    private static Station station;
    private static int eventId;
    private static int firstMember;
    private static int secondMember;
    private static int groupId;

    @BeforeAll
    static void setup() {
        service = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);
        templates = new EventTemplateService(new EventTemplateRepository(), attendanceRepo, memberEligibility);
        station = stationRepo.create("AppointmentAnswersStation");
        firstMember = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("appointment-a@test.com", "Anna", "A")
                                .id())
                .id();
        secondMember = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo.create("appointment-b@test.com", "Ben", "B").id())
                .id();
        groupId = memberGroupRepo.create(station.id(), "Leere Gruppe").id();

        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        eventId = eventRepo
                .create(
                        station.id(),
                        "Antworten",
                        "",
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        null,
                        false,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static String kept(FieldType type, String config, String value) {
        service.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        type.name(), type, EventQuestionSettings.parse(config), value, false, null, false)));
        return service.findByEvent(eventId).getFirst().value();
    }

    private static void refused(FieldType type, String config, String value) {
        assertThrows(
                BadRequestResponse.class,
                () -> service.replaceFields(
                        eventId,
                        List.of(new EventFieldDraft(
                                type.name(), type, EventQuestionSettings.parse(config), value, false, null, false))),
                value + " under " + type);
    }

    @Test
    void aNumberIsWhole() {
        assertEquals("3", kept(FieldType.NUMBER, "{}", "3"));
        refused(FieldType.NUMBER, "{}", "2.5");
        refused(FieldType.NUMBER, "{}", "drei");
    }

    @Test
    void aDayATimeAndALinkMustReadAsOne() {
        assertEquals("2026-03-09", kept(FieldType.DATE, "{}", "2026-03-09"));
        refused(FieldType.DATE, "{}", "09.03.2026");
        assertEquals("18:30", kept(FieldType.TIME, "{}", "18:30"));
        refused(FieldType.TIME, "{}", "halb sieben");
        assertEquals("https://ember.example", kept(FieldType.URL, "{}", "https://ember.example"));
        refused(FieldType.URL, "{}", "ember.example");
    }

    @Test
    void aYesIsStoredAsTrueHoweverItWasSent() {
        assertEquals("true", kept(FieldType.BOOLEAN, "{}", "1"));
        assertEquals("false", kept(FieldType.BOOLEAN, "{}", "0"));
        refused(FieldType.BOOLEAN, "{}", "ja");
    }

    @Test
    void aChoiceIsMeasured() {
        assertEquals("M", kept(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "M"));
        refused(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "XL");
    }

    @Test
    void aPlaceAndAnyTextAreKeptAsTyped() {
        assertEquals("Wache Nord, Halle 2", kept(FieldType.LOCATION, "{}", "Wache Nord, Halle 2"));
        assertEquals("Zeile\nzwei", kept(FieldType.LONG_TEXT, "{}", "Zeile\nzwei"));
    }

    @Test
    void membersAreKeptInTheOneShapeTheirTypeIsStoredIn() {
        assertEquals(String.valueOf(firstMember), kept(FieldType.MEMBER, "{}", String.valueOf(firstMember)));
        String both = "[" + firstMember + "," + secondMember + "]";
        assertEquals(both, kept(FieldType.MEMBER_LIST, "{}", both));
        assertEquals(both, kept(FieldType.MEMBER_LIST, "{}", "[\"" + firstMember + "\", " + secondMember + "]"));
        refused(FieldType.MEMBER, "{}", both);
        refused(FieldType.MEMBER_LIST, "{}", "Anna");
    }

    @Test
    void theOrganiserMayNotNameAMemberOutsideTheGroup() {
        String config = "{\"groupId\":" + groupId + "}";

        refused(FieldType.MEMBER_OF_GROUP, config, String.valueOf(firstMember));
        service.replaceFields(eventId, List.of());
    }

    @Test
    void aMemberNamedBeforeStaysNamedAfterLeavingTheGroup() {
        int group = memberGroupRepo.create(station.id(), "Fahrer").id();
        memberGroupRepo.addMember(group, firstMember);
        var config = EventQuestionSettings.parse("{\"groupId\":" + group + "}");
        service.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        "Fahrer",
                        FieldType.MEMBER_LIST_OF_GROUP,
                        config,
                        "[" + firstMember + "]",
                        false,
                        null,
                        false)));
        memberGroupRepo.removeMember(group, firstMember);
        var stored = service.findByEvent(eventId).getFirst();

        assertDoesNotThrow(() -> service.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        stored.id(),
                        "Fahrer",
                        FieldType.MEMBER_LIST_OF_GROUP,
                        config,
                        stored.value(),
                        false,
                        null,
                        false))));
        assertThrows(
                BadRequestResponse.class,
                () -> service.replaceFields(
                        eventId,
                        List.of(new EventFieldDraft(
                                stored.id(),
                                "Fahrer",
                                FieldType.MEMBER_LIST_OF_GROUP,
                                config,
                                "[" + firstMember + "," + secondMember + "]",
                                false,
                                null,
                                false))));
        service.replaceFields(eventId, List.of());
    }

    @Test
    void anEmptyFieldIsNeverRequired() {
        assertEquals("", kept(FieldType.NUMBER, "{\"required\":true}", ""));
    }

    @Test
    void aTemplateStartsOnlyFromAValueItsFieldTakes() {
        int templateId = templates.create(station.id(), "Vorlage Antworten").id();

        assertDoesNotThrow(() -> templates.replaceFields(
                templateId,
                List.of(new AppointmentTemplateFieldDraft(
                        "Größe",
                        FieldType.CHOICE,
                        EventQuestionSettings.parse("{\"options\":[\"S\",\"M\"]}"),
                        0,
                        false,
                        false,
                        null,
                        "M"))));
        assertThrows(
                BadRequestResponse.class,
                () -> templates.replaceFields(
                        templateId,
                        List.of(new AppointmentTemplateFieldDraft(
                                "Größe",
                                FieldType.CHOICE,
                                EventQuestionSettings.parse("{\"options\":[\"S\",\"M\"]}"),
                                0,
                                false,
                                false,
                                null,
                                "XL"))));
        assertThrows(
                BadRequestResponse.class,
                () -> templates.replaceFields(
                        templateId,
                        List.of(new AppointmentTemplateFieldDraft(
                                "Gäste",
                                FieldType.NUMBER,
                                EventQuestionSettings.empty(),
                                0,
                                false,
                                false,
                                null,
                                "2.5"))));
    }

    @Test
    void aTemplateMayNotStartAMemberQuestionOnSomebodyOutsideItsGroup() {
        int templateId = templates.create(station.id(), "Vorlage Gruppe").id();

        assertThrows(
                BadRequestResponse.class,
                () -> templates.replaceFields(
                        templateId,
                        List.of(new AppointmentTemplateFieldDraft(
                                "Fahrer",
                                FieldType.MEMBER_OF_GROUP,
                                EventQuestionSettings.parse("{\"groupId\":" + groupId + "}"),
                                0,
                                false,
                                false,
                                null,
                                String.valueOf(firstMember)))));
    }
}
