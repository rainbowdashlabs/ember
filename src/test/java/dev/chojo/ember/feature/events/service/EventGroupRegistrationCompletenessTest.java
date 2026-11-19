/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.EntrySource;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether an event group sign-up is complete: the questions it answers, the parts it is required to
 * choose, and the parts each participant was present at.
 */
class EventGroupRegistrationCompletenessTest extends EventGroupRegistrationTestBase {
    @Test
    void aSignUpAnswersTheEventGroupOnceAndARequiredQuestionIsAskedFor() {
        var asked = askShirtAndAllergies();
        int shirt = asked.get("T-Shirt");
        int allergies = asked.get("Allergien");

        assertRefused(EventRefusal.REGISTRATION_ANSWER_NOT_ACCEPTED, () -> signUp(anna, sat1, sun1, test));
        assertEquals(Map.of(), partsOf(anna));

        service.register(
                station.id(),
                eventGroup.id(),
                anna.id(),
                Set.of(sat1, sun1, test),
                Map.of(shirt, "M", allergies, "Nüsse"),
                null,
                false);
        assertEquals(Map.of(shirt, "M", allergies, "Nüsse"), answersOf(anna));
        assertEquals(List.of(), eventRegistrationFieldRepo().findValuesForRegistrations(registrationIdsOf(anna)));
    }

    @Test
    void anAnswerToAQuestionTheEventGroupDoesNotAskIsRefused() {
        assertRefused(
                EventRefusal.REGISTRATION_ANSWER_TO_UNKNOWN_QUESTION,
                () -> service.register(
                        station.id(),
                        eventGroup.id(),
                        anna.id(),
                        Set.of(sat1, sun1, test),
                        Map.of(-1, "x"),
                        null,
                        false));
    }

    @Test
    void changingAnswersLeavesTheOnesKeptForOrganisersToThem() {
        var asked = askShirtAndAllergies();
        int shirt = asked.get("T-Shirt");
        int allergies = asked.get("Allergien");
        service.register(
                station.id(),
                eventGroup.id(),
                anna.id(),
                Set.of(sat1, sun1, test),
                Map.of(shirt, "M", allergies, "Nüsse"),
                null,
                false);

        service.changeAnswers(station.id(), eventGroup.id(), anna.id(), Map.of(shirt, "L"), false, false);
        assertEquals(Map.of(shirt, "L", allergies, "Nüsse"), answersOf(anna));

        service.changeAnswers(station.id(), eventGroup.id(), anna.id(), Map.of(shirt, "S"), true, false);
        assertEquals(Map.of(shirt, "S"), answersOf(anna));
    }

    @Test
    void signingUpAgainStartsFromTheAnswersGivenThen() {
        var asked = askShirtAndAllergies();
        int shirt = asked.get("T-Shirt");
        int allergies = asked.get("Allergien");
        service.register(
                station.id(),
                eventGroup.id(),
                anna.id(),
                Set.of(sat1, sun1, test),
                Map.of(shirt, "M", allergies, "Nüsse"),
                null,
                false);
        service.withdraw(station.id(), eventGroup.id(), anna.id());

        service.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), Map.of(shirt, "S"), null, false);
        assertEquals(Map.of(shirt, "S"), answersOf(anna));
    }

    @Test
    void aQuestionKeepingItsNameKeepsItsAnswers() {
        var asked = askShirtAndAllergies();
        int shirt = asked.get("T-Shirt");
        service.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), Map.of(shirt, "M"), null, false);

        questions.replaceQuestions(
                eventGroup.id(),
                List.of(new RegistrationFieldDraft("T-Shirt", FieldType.TEXT, EventQuestionSettings.empty(), true)));

        assertEquals(Map.of(shirt, "M"), answersOf(anna));
    }

    @Test
    void twoQuestionsUnderOneNameAreRefused() {
        var twice = List.of(
                new RegistrationFieldDraft("Größe", FieldType.TEXT, EventQuestionSettings.empty(), false),
                new RegistrationFieldDraft("Größe", FieldType.TEXT, EventQuestionSettings.empty(), false));

        assertRefused(
                EventRefusal.EVENT_GROUP_QUESTION_NAME_TWICE, () -> questions.replaceQuestions(eventGroup.id(), twice));
    }

    @Test
    void aWithdrawalForgetsTheAnswers() {
        var asked = askShirtAndAllergies();
        int shirt = asked.get("T-Shirt");
        service.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), Map.of(shirt, "M"), null, false);
        int signUpId = service.find(eventGroup.id(), anna.id()).id();

        service.withdraw(station.id(), eventGroup.id(), anna.id());

        assertEquals(Map.of(), questions.answersBySignUp(List.of(signUpId)));
    }

    @Test
    void afterTheDeadlineOnlyWhoeverRunsEventsChangesAnswers() {
        var asked = askShirtAndAllergies();
        int shirt = asked.get("T-Shirt");
        service.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), Map.of(shirt, "M"), null, false);
        eventGroups.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Jugendflamme 1",
                        null,
                        null,
                        RegistrationDeadline.fixed(Instant.now().minus(Duration.ofHours(1))),
                        false,
                        null));

        assertRefused(
                EventRefusal.EVENT_GROUP_REGISTRATION_CLOSED,
                () -> service.changeAnswers(station.id(), eventGroup.id(), anna.id(), Map.of(shirt, "L"), true, false));
        service.changeAnswers(station.id(), eventGroup.id(), anna.id(), Map.of(shirt, "L"), false, true);
        assertEquals(Map.of(shirt, "L"), answersOf(anna));
    }

    @Test
    void anAppointmentJoiningAnEventGroupLeavesItsOwnQuestionsBehind() {
        var start = LocalDate.now().plusDays(40).atTime(9, 0).atZone(zone).toInstant();
        var appointment = eventRepo.create(
                station.id(),
                "Zusatztag",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(4)),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        var required = new EventQuestionSettings(
                null, null, null, null, null, false, false, true, null, null, null, false, null);
        eventRegistrationFieldRepo()
                .replaceFields(
                        appointment.id(),
                        List.of(new RegistrationFieldDraft("Schuhgröße", FieldType.TEXT, required, false)));

        var set = eventGroups.findSets(eventGroup.id()).getFirst();
        eventGroups.addPart(station.id(), eventGroup.id(), appointment.id(), set.id());

        assertEquals(List.of(), eventRegistrationFieldRepo().findByEvent(appointment.id()));
    }

    @Test
    void attendanceCountsThePartsEachParticipantWasPresentAt() {
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        var sheet = attendanceRepo.createTemplate(station.id(), "Lehrgang " + eventGroup.id());
        markPresent(sheet.id(), sat1, anna);
        markPresent(sheet.id(), sun1, anna);
        markPresent(sheet.id(), test, anna);
        markPresent(sheet.id(), sat1, ben);
        markPresent(sheet.id(), test, ben);

        var rows = new EventGroupAttendanceService(eventGroups, service, eventGroupRepo)
                .summary(station.id(), eventGroup.id()).stream()
                        .collect(Collectors.toMap(EventGroupAttendanceService.AttendanceRow::memberId, row -> row));

        assertTrue(rows.get(anna.id()).complete());
        assertEquals(List.of(sat1, test), rows.get(ben.id()).attended());
        assertEquals(false, rows.get(ben.id()).complete());
        var practice = rows.get(ben.id()).sets().getFirst();
        assertEquals(1, practice.chosen());
        assertEquals(2, practice.required());
    }

    @Test
    void attendanceCountsEachPresentPartOnceAndOnlyForAcceptedParticipants() {
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        service.withdraw(station.id(), eventGroup.id(), ben.id());
        var sheet = attendanceRepo.createTemplate(station.id(), "Lehrgang " + eventGroup.id());
        markPresent(sheet.id(), sat1, anna);
        markPresent(sheet.id(), sat1, anna);
        markPresent(sheet.id(), sat1, ben);
        markPresent(sheet.id(), sat1, carla);
        var part = eventRepo.findById(test).orElseThrow();
        var absentSheet = attendanceRepo.createSession(sheet.id(), part.startTime(), part.endTime(), test, null, null);
        attendanceRepo.createEntry(absentSheet.id(), anna.id(), AttendanceStatus.ABSENT, EntrySource.EXPECTED);
        callOff(sun1);
        callOff(sat2);

        var rows = new EventGroupAttendanceService(eventGroups, service, eventGroupRepo)
                .summary(station.id(), eventGroup.id());

        assertEquals(
                List.of(anna.id()),
                rows.stream()
                        .map(EventGroupAttendanceService.AttendanceRow::memberId)
                        .toList());
        var row = rows.getFirst();
        assertEquals(List.of(sat1), row.attended());
        var practice = row.sets().getFirst();
        assertEquals(1, practice.chosen());
        assertEquals(1, practice.required());
        assertEquals(false, row.complete());
    }

    @Test
    void aCalledOffTestDayIsNoLongerAskedForAndCannotBeChosen() {
        callOff(test);

        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_CALLED_OFF, () -> signUp(anna, sat1, sun1, test));
        signUp(anna, sat1, sun1);
        assertTrue(service.evaluator(eventGroup.id()).apply(Set.of(sat1, sun1)).stream()
                .allMatch(result -> result.met()));
    }

    @Test
    void aRequiredPartHasToBeChosenUnlessWhoeverRunsEventsSignsUp() {
        eventGroups.setRequired(station.id(), eventGroup.id(), sat1, true);

        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_SHORT, () -> signUp(anna, sun1, sat2, test));
        signUp(anna, sat1, sat2, test);
        assertRefused(
                EventRefusal.EVENT_GROUP_CHOICE_SHORT,
                () -> service.changeParts(
                        station.id(), eventGroup.id(), anna.id(), Set.of(sun1, sat2, test), null, false));

        service.register(
                station.id(), eventGroup.id(), ben.id(), Set.of(sun1, sat2, test), Map.of(), leader.id(), true);
        var standing = standingOf(ben).sets();
        assertTrue(standing.stream().anyMatch(result -> result.missingRequired().contains(sat1)));
    }

    @Test
    void aSignUpLackingANewlyRequiredPartStaysAndShowsShort() {
        signUp(anna, sun1, sat2, test);

        eventGroups.setRequired(station.id(), eventGroup.id(), sat1, true);

        assertEquals(Set.of(sun1, sat2, test), standingOf(anna).counted());
        var standing = standingOf(anna).sets();
        assertTrue(standing.stream().anyMatch(result -> !result.met()));
        assertTrue(service.partStates(eventGroup.id()).stream()
                .anyMatch(part -> part.eventId() == sat1 && part.required()));
    }

    @Test
    void aCalledOffRequiredPartIsNoLongerAskedFor() {
        eventGroups.setRequired(station.id(), eventGroup.id(), sat1, true);
        callOff(sat1);

        signUp(anna, sun1, sat2, test);

        assertEquals(Set.of(sun1, sat2, test), standingOf(anna).counted());
    }
}
