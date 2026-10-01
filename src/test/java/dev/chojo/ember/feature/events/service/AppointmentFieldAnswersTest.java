/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.members.service.UserTagService;
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
                memberGroupRepo,
                new UserTagService(userTagRepo, memberGroupRepo),
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);
        templates = new EventTemplateService(new EventTemplateRepository(), attendanceRepo);
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

    private static String kept(EventFieldType type, String config, String value) {
        service.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        type.name(), type, EventFieldConfig.parse(config), value, false, null, false)));
        return service.findByEvent(eventId).getFirst().value();
    }

    private static void refused(EventFieldType type, String config, String value) {
        assertThrows(
                BadRequestResponse.class,
                () -> service.replaceFields(
                        eventId,
                        List.of(new EventFieldDraft(
                                type.name(), type, EventFieldConfig.parse(config), value, false, null, false))),
                value + " under " + type);
    }

    @Test
    void aNumberIsWhole() {
        assertEquals("3", kept(EventFieldType.NUMBER, "{}", "3"));
        refused(EventFieldType.NUMBER, "{}", "2.5");
        refused(EventFieldType.NUMBER, "{}", "drei");
    }

    @Test
    void aDayATimeAndALinkMustReadAsOne() {
        assertEquals("2026-03-09", kept(EventFieldType.DATE, "{}", "2026-03-09"));
        refused(EventFieldType.DATE, "{}", "09.03.2026");
        assertEquals("18:30", kept(EventFieldType.TIME, "{}", "18:30"));
        refused(EventFieldType.TIME, "{}", "halb sieben");
        assertEquals("https://ember.example", kept(EventFieldType.URL, "{}", "https://ember.example"));
        refused(EventFieldType.URL, "{}", "ember.example");
    }

    @Test
    void aYesOrNoAndAChoiceAreMeasured() {
        assertEquals("1", kept(EventFieldType.BOOLEAN, "{}", "1"));
        refused(EventFieldType.BOOLEAN, "{}", "ja");
        assertEquals("M", kept(EventFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "M"));
        refused(EventFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "XL");
    }

    @Test
    void aPlaceAndAnyTextAreKeptAsTyped() {
        assertEquals("Wache Nord, Halle 2", kept(EventFieldType.LOCATION, "{}", "Wache Nord, Halle 2"));
        assertEquals("Zeile\nzwei", kept(EventFieldType.TEXTAREA, "{}", "Zeile\nzwei"));
    }

    @Test
    void membersAreKeptAsBareText() {
        assertEquals(String.valueOf(firstMember), kept(EventFieldType.MEMBER, "{}", String.valueOf(firstMember)));
        String both = "[" + firstMember + "," + secondMember + "]";
        assertEquals(both, kept(EventFieldType.MEMBER_LIST, "{}", both));
        refused(EventFieldType.MEMBER, "{}", both);
        refused(EventFieldType.MEMBER_LIST, "{}", "Anna");
    }

    @Test
    void theOrganiserMayNameAMemberOutsideTheGroup() {
        String config = "{\"groupId\":" + groupId + "}";

        assertEquals(
                String.valueOf(firstMember), kept(EventFieldType.MEMBER_OF_GROUP, config, String.valueOf(firstMember)));
        service.replaceFields(eventId, List.of());
    }

    @Test
    void anEmptyFieldIsNeverRequired() {
        assertEquals("", kept(EventFieldType.NUMBER, "{\"required\":true}", ""));
    }

    @Test
    void aTemplateStartsOnlyFromAValueItsFieldTakes() {
        int templateId = templates.create(station.id(), "Vorlage Antworten").id();

        assertDoesNotThrow(() -> templates.replaceFields(
                templateId,
                List.of(new EventTemplateFieldData(
                        "Größe",
                        EventFieldType.ENUM,
                        EventFieldConfig.parse("{\"options\":[\"S\",\"M\"]}"),
                        0,
                        false,
                        false,
                        null,
                        "M"))));
        assertThrows(
                BadRequestResponse.class,
                () -> templates.replaceFields(
                        templateId,
                        List.of(new EventTemplateFieldData(
                                "Größe",
                                EventFieldType.ENUM,
                                EventFieldConfig.parse("{\"options\":[\"S\",\"M\"]}"),
                                0,
                                false,
                                false,
                                null,
                                "XL"))));
        assertThrows(
                BadRequestResponse.class,
                () -> templates.replaceFields(
                        templateId,
                        List.of(new EventTemplateFieldData(
                                "Gäste",
                                EventFieldType.NUMBER,
                                EventFieldConfig.empty(),
                                0,
                                false,
                                false,
                                null,
                                "2.5"))));
    }
}
