/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who an appointment's table is about, and which of its questions may be on it.
 *
 * <p>The drawing itself belongs to the shared table and is tested there. What belongs here is the
 * naming: every answer given for the day, whatever it was, and never a question the appointment keeps
 * for whoever runs it.
 */
class EventMemberTableServiceTest extends RepositoryTestBase {
    private static final LocalDate DAY = LocalDate.of(2027, 4, 17);

    private static EventMemberTableService service;
    private static Station station;
    private static int eventId;
    private static int openQuestionId;
    private static int hiddenQuestionId;
    private static int comingMemberId;
    private static int withdrawnMemberId;

    private static EventRegistrationFieldRepository questionRepo;

    @BeforeAll
    static void setup() {
        questionRepo = new EventRegistrationFieldRepository();
        service = new EventMemberTableService(
                eventRegistrationRepo,
                new EventRegistrationFieldService(questionRepo),
                new MemberTableService(profileFieldRepo, stationMemberRepo));

        station = stationRepo.create("TabellenTerminWache");
        var comingAccount = accountRepo.create("kommt@test.com", "Kim", "Kommt");
        var goneAccount = accountRepo.create("weg@test.com", "Wanda", "Weg");
        comingMemberId =
                stationMemberRepo.create(station.id(), comingAccount.id()).id();
        withdrawnMemberId =
                stationMemberRepo.create(station.id(), goneAccount.id()).id();

        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        eventId = eventRepo
                .create(
                        station.id(),
                        "Tabellentermin",
                        "desc",
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

        openQuestionId = questionRepo
                .create(eventId, "Schuhgröße", EventFieldType.STRING, EventRegistrationFieldConfig.parse("{}"), 0, true)
                .id();
        hiddenQuestionId = questionRepo
                .create(
                        eventId,
                        "Interne Notiz",
                        EventFieldType.STRING,
                        EventRegistrationFieldConfig.parse("{\"managersOnly\":true}"),
                        1,
                        true)
                .id();

        var coming = eventRegistrationRepo.create(eventId, comingMemberId, DAY, RegistrationStatus.ACCEPTED, null);
        questionRepo.setValue(coming.id(), openQuestionId, "43");
        questionRepo.setValue(coming.id(), hiddenQuestionId, "spricht kein Deutsch");
        eventRegistrationRepo.create(eventId, withdrawnMemberId, DAY, RegistrationStatus.WITHDRAWN, null);
    }

    private static Set<StationPermission> runsTheAppointment() {
        return Set.of(StationPermission.USER, StationPermission.EVENT_REGISTRATION, StationPermission.EVENT_EDIT);
    }

    /** A question the appointment asks openly may be a column; one kept back may not. */
    @Test
    void onlyTheOpenQuestionsAreOffered() {
        var openly = service.offerableQuestions(eventId, false);

        assertTrue(openly.containsKey(openQuestionId), "what the appointment asks everybody is offered");
        assertFalse(openly.containsKey(hiddenQuestionId), "what it keeps for itself is not");

        var toWhoeverRunsIt = service.offerableQuestions(eventId, true);
        assertTrue(toWhoeverRunsIt.containsKey(hiddenQuestionId), "and is offered to whoever runs the appointment");
    }

    /**
     * Everybody who answered is on the table, whatever they answered.
     *
     * <p>Which of them to show is the screen's to decide, and it opens on the confirmed ones. A table
     * that held only the people who are coming could not be asked who is not.
     */
    @Test
    void everyAnswerForTheDayIsOnIt() {
        var table = service.table(
                station,
                eventId,
                DAY,
                List.of(MemberTableColumn.builtin("name"), MemberTableColumn.builtin("registrationStatus")),
                runsTheAppointment(),
                true);

        assertEquals(2, table.rows().size(), "the one coming and the one who gave their place back");
        var statuses = table.rows().stream().map(row -> row.values().get(1)).toList();
        assertTrue(statuses.contains(RegistrationStatus.ACCEPTED.name()));
        assertTrue(statuses.contains(RegistrationStatus.WITHDRAWN.name()), "a place given back is still an answer");
    }

    /** The table carries the answer as it is, leaving the word for it to whoever shows it. */
    @Test
    void theStatusTravelsAsItself() {
        var table = service.table(
                station,
                eventId,
                DAY,
                List.of(MemberTableColumn.builtin("registrationStatus")),
                runsTheAppointment(),
                true);

        assertTrue(
                table.rows().stream()
                        .anyMatch(row -> "ACCEPTED".equals(row.values().getFirst())),
                "the screen and the file each put it in their own words, so the table holds neither");
    }

    /** An answer to one of the appointment's own questions reaches the row that gave it. */
    @Test
    void anAnswerReachesItsRow() {
        var table = service.table(
                station,
                eventId,
                DAY,
                List.of(MemberTableColumn.builtin("name"), MemberTableColumn.registrationField(openQuestionId)),
                runsTheAppointment(),
                true);

        var coming = table.rows().stream()
                .filter(row -> row.memberId() == comingMemberId)
                .findFirst()
                .orElseThrow();
        assertEquals("43", coming.values().get(1));

        var gone = table.rows().stream()
                .filter(row -> row.memberId() == withdrawnMemberId)
                .findFirst()
                .orElseThrow();
        assertEquals("", gone.values().get(1), "somebody who answered nothing has nothing in the cell");
    }

    /**
     * A question kept for whoever runs the appointment is no column for anybody else.
     *
     * <p>Naming it outright changes nothing: the table would otherwise be the softer door to what the
     * registration list already keeps shut.
     */
    @Test
    void aKeptQuestionIsNoWayIn() {
        var table = service.table(
                station,
                eventId,
                DAY,
                List.of(MemberTableColumn.registrationField(hiddenQuestionId)),
                Set.of(StationPermission.USER, StationPermission.EVENT_REGISTRATION),
                false);

        assertTrue(table.columns().isEmpty(), "the column is not drawn at all");
        assertTrue(
                table.rows().stream().allMatch(row -> row.values().isEmpty()),
                "and no row carries what it would have said");
    }

    /** A day nobody answered for is an empty table rather than somebody else's day. */
    @Test
    void anotherDayIsEmpty() {
        var table = service.table(
                station,
                eventId,
                DAY.plusDays(7),
                List.of(MemberTableColumn.builtin("name")),
                runsTheAppointment(),
                true);

        assertTrue(table.rows().isEmpty());
    }
}
