/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        service = new EventRegistrationFieldService(new EventRegistrationFieldRepository(), memberEligibility);
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

    private static int ask(FieldType type) {
        service.replaceFields(
                eventId, List.of(new RegistrationFieldDraft(type.name(), type, EventQuestionSettings.empty(), true)));
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
    void aYesIsStoredAsTrueAndPrintedAsAWordHoweverItWasSent() {
        int field = ask(FieldType.BOOLEAN);

        assertEquals("true", kept(field, "true"));
        assertEquals("Ja", printed(field));
        assertEquals("true", kept(field, "1"));
        assertEquals("Ja", printed(field));
        assertEquals("false", kept(field, "0"));
        assertEquals("Nein", printed(field));
    }

    @Test
    void aMemberIsKeptAsTheirNumberAndPrintedByName() {
        int field = ask(FieldType.MEMBER);
        String named = String.valueOf(memberId);

        assertEquals(named, kept(field, "\"" + named + "\""));
        assertTrue(printed(field).contains("Rita"), printed(field));
    }

    @Test
    void aDateIsPrintedAsADay() {
        int field = ask(FieldType.DATE);

        assertEquals("2027-05-08", kept(field, "2027-05-08"));
        assertEquals("08.05.2027", printed(field));
    }

    @Test
    void aQuestionOfAKindTheFormDoesNotOfferIsRefused() {
        assertThrows(RefusalResponse.class, () -> ask(FieldType.LOCATION));
    }

    @Test
    void aDecimalIsRefusedWhereANumberIsAsked() {
        int field = ask(FieldType.NUMBER);

        assertThrows(RefusalResponse.class, () -> service.resolveAnswers(eventId, Map.of(field, "1.5")));
    }
}
