/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a registration question takes today beyond what the question service tests already say, how
 * a registration keeps the answer and how the registration table prints it.
 *
 * <p>Written down before the field types are brought together, so that every later change can show
 * which of these it meant to change and that it left the rest alone.
 */
class RegistrationAnswersTest extends RepositoryTestBase {
    private static final LocalDate DAY = LocalDate.of(2027, 5, 8);

    private static EventRegistrationFieldService service;
    private static EventMemberTableService table;
    private static Station station;
    private static int eventId;
    private static int memberId;

    @BeforeAll
    static void setup() {
        service = new EventRegistrationFieldService(new EventRegistrationFieldRepository());
        table = new EventMemberTableService(
                eventRegistrationRepo, service, new MemberTableService(profileFieldRepo, stationMemberRepo));
        station = stationRepo.create("RegistrationAnswersStation");
        memberId = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("registration-answers@test.com", "Rita", "R")
                                .id())
                .id();
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        eventId = eventRepo
                .create(
                        station.id(),
                        "Anmeldung",
                        "",
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        null,
                        true,
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

    private static int ask(EventFieldType type) {
        service.replaceFields(
                eventId,
                List.of(new RegistrationFieldDraft(type.name(), type, EventRegistrationFieldConfig.empty(), true)));
        return service.findByEvent(eventId).getFirst().id();
    }

    /** Registers the member once more with one answer, and reads back what the registration keeps. */
    private static String kept(int fieldId, String answer) {
        eventRegistrationRepo.findByEvent(eventId).forEach(r -> eventRegistrationRepo.delete(r.id()));
        var registration = eventRegistrationRepo.create(eventId, memberId, DAY, RegistrationStatus.ACCEPTED, null);
        service.persistAnswers(registration.id(), service.resolveAnswers(eventId, Map.of(fieldId, answer)));
        return service.findValues(registration.id()).getFirst().value();
    }

    private static String printed(int fieldId) {
        return table.table(
                        station,
                        eventId,
                        DAY,
                        List.of(MemberTableColumn.registrationField(fieldId)),
                        Set.of(StationPermission.USER, StationPermission.EVENT_EDIT),
                        true)
                .rows()
                .getFirst()
                .values()
                .getFirst();
    }

    @Test
    void aYesIsKeptAndPrintedAsItWasSent() {
        int field = ask(EventFieldType.BOOLEAN);

        assertEquals("true", kept(field, "true"));
        assertEquals("true", printed(field));
        assertEquals("1", kept(field, "1"));
        assertEquals("1", printed(field));
    }

    @Test
    void membersAreKeptAndPrintedAsTheirNumbers() {
        int field = ask(EventFieldType.MEMBER_LIST);
        String named = "[" + memberId + "]";

        assertEquals(named, kept(field, named));
        assertEquals(named, printed(field));
    }

    @Test
    void aQuestionOfAnyOfTheSeventeenTypesIsAccepted() {
        int field = ask(EventFieldType.LOCATION);

        assertEquals("Wache Nord", kept(field, "Wache Nord"));
    }

    @Test
    void aDecimalIsRefusedWhereANumberIsAsked() {
        int field = ask(EventFieldType.NUMBER);

        assertThrows(BadRequestResponse.class, () -> service.resolveAnswers(eventId, Map.of(field, "1.5")));
    }
}
