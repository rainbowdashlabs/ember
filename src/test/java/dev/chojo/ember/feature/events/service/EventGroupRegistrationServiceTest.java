/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.EntrySource;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Signing up for a Jugendflamme event group: three practice days of which two count, and a test day that
 * is mandatory.
 */
class EventGroupRegistrationServiceTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static StationMember carla;
    private static StationMember leader;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    private EventGroupService eventGroups;
    private EventGroupRegistrationService service;
    private EventGroupQuestionService questions;
    private EventRegistrationService registrations;
    private EventCancellationService cancellation;
    private DomainEventBus bus;
    private ZoneId zone;

    private EventGroup eventGroup;
    private int sat1;
    private int sun1;
    private int sat2;
    private int test;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event group Sign-up Station");
        anna = memberOf("anna@signup.test", "Anna");
        ben = memberOf("ben@signup.test", "Ben");
        carla = memberOf("carla@signup.test", "Carla");
        leader = memberOf("leader@signup.test", "Lea");
    }

    private static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Sign-up");
        accounts.add(account.id());
        return stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        bus = mock(DomainEventBus.class);
        var eventServices = newEventServices(new DomainEventBus(Set.of()));
        registrations = eventServices.registration();
        cancellation = eventServices.cancellation();
        eventGroups = new EventGroupService(
                eventGroupRepo,
                eventRepo,
                restrictionService,
                occurrenceCalendar,
                new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility));
        questions = new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility);
        service = new EventGroupRegistrationService(
                eventGroupRepo,
                new EventGroupRegistrationRepository(),
                eventRepo,
                eventRegistrationRepo,
                registrations,
                occurrenceCalendar,
                bus,
                memberNameResolver,
                questions,
                new EventGroupPartnerRepository());
        zone = occurrenceCalendar.zoneOf(station.id());
        eventGroup = jugendflamme(null, false, Instant.now().plus(Duration.ofDays(5)));
    }

    private EventGroup jugendflamme(Integer limit, boolean confirm, Instant deadline) {
        var created = eventGroups.create(
                station.id(),
                new EventGroupDetails("Jugendflamme 1", null, limit, deadline, confirm),
                List.of(new EventGroupSetDraft(null, "Übung", 2), new EventGroupSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var sets = eventGroups.findSets(created.id()).stream()
                .collect(Collectors.toMap(EventGroupSet::name, EventGroupSet::id));
        sat1 = part(created, sets.get("Übung"), 20);
        sun1 = part(created, sets.get("Übung"), 21);
        sat2 = part(created, sets.get("Übung"), 27);
        test = part(created, sets.get("Prüfung"), 28);
        return created;
    }

    private int part(EventGroup into, int setId, int daysAhead) {
        var start =
                LocalDate.now().plusDays(daysAhead).atTime(9, 0).atZone(zone).toInstant();
        var event = eventRepo.create(
                station.id(),
                "Tag " + daysAhead,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventGroups.addPart(station.id(), into.id(), event.id(), setId);
        return event.id();
    }

    private void callOff(int partId) {
        var part = eventRepo.findById(partId).orElseThrow();
        cancellation.cancelDate(part, dateOf(partId), "Unwetter", null);
    }

    private LocalDate dateOf(int partId) {
        return eventRepo.findById(partId).orElseThrow().startTime().atZone(zone).toLocalDate();
    }

    private Map<Integer, RegistrationStatus> partsOf(StationMember member) {
        return eventRegistrationRepo.findByMemberInEvents(member.id(), List.of(sat1, sun1, sat2, test)).stream()
                .collect(Collectors.toMap(EventRegistration::eventId, EventRegistration::status));
    }

    private void signUp(StationMember member, Integer... parts) {
        service.register(station.id(), eventGroup.id(), member.id(), Set.of(parts), Map.of(), null, false);
    }

    private Map<String, Integer> askShirtAndAllergies() {
        var required =
                new EventQuestionSettings(null, null, null, null, null, false, false, true, null, null, null, false);
        var forOrganisers =
                new EventQuestionSettings(null, null, null, null, null, false, false, false, null, null, null, true);
        questions.replaceQuestions(
                eventGroup.id(),
                List.of(
                        new RegistrationFieldDraft("T-Shirt", FieldType.TEXT, required, false),
                        new RegistrationFieldDraft("Allergien", FieldType.TEXT, forOrganisers, false)));
        return questions.findByEventGroup(eventGroup.id()).stream()
                .collect(Collectors.toMap(EventRegistrationField::name, EventRegistrationField::id));
    }

    private Map<Integer, String> answersOf(StationMember member) {
        int signUpId = service.find(eventGroup.id(), member.id()).id();
        return questions.answersBySignUp(List.of(signUpId)).getOrDefault(signUpId, List.of()).stream()
                .collect(Collectors.toMap(RegistrationFieldValue::fieldId, RegistrationFieldValue::value));
    }

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
                        "Jugendflamme 1", null, null, Instant.now().minus(Duration.ofHours(1)), false));

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
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var required =
                new EventQuestionSettings(null, null, null, null, null, false, false, true, null, null, null, false);
        eventRegistrationFieldRepo()
                .replaceFields(
                        appointment.id(),
                        List.of(new RegistrationFieldDraft("Schuhgröße", FieldType.TEXT, required, false)));

        var set = eventGroups.findSets(eventGroup.id()).getFirst();
        eventGroups.addPart(station.id(), eventGroup.id(), appointment.id(), set.id());

        assertEquals(List.of(), eventRegistrationFieldRepo().findByEvent(appointment.id()));
    }

    private void markPresent(int templateId, int partId, StationMember member) {
        var part = eventRepo.findById(partId).orElseThrow();
        var sheet = attendanceRepo.createSession(templateId, part.startTime(), part.endTime(), partId, null, null);
        attendanceRepo.createEntry(sheet.id(), member.id(), AttendanceStatus.PRESENT, EntrySource.EXPECTED);
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

    private List<Integer> registrationIdsOf(StationMember member) {
        return eventRegistrationRepo.findByMemberInEvents(member.id(), List.of(sat1, sun1, sat2, test)).stream()
                .map(EventRegistration::id)
                .toList();
    }

    private static EventRegistrationFieldRepository eventRegistrationFieldRepo() {
        return new EventRegistrationFieldRepository();
    }

    @Test
    void aSignUpPlacesTheMemberOnEveryChosenPart() {
        signUp(anna, sat1, sun1, test);

        assertEquals(
                Map.of(
                        sat1,
                        RegistrationStatus.ACCEPTED,
                        sun1,
                        RegistrationStatus.ACCEPTED,
                        test,
                        RegistrationStatus.ACCEPTED),
                partsOf(anna));
        assertEquals(
                RegistrationStatus.ACCEPTED,
                service.find(eventGroup.id(), anna.id()).status());
        assertEquals(Set.of(sat1, sun1, test), service.chosenParts(eventGroup.id(), anna.id()));
    }

    @Test
    void aChoiceBreakingTheRulesIsRefusedUnlessWhoeverRunsEventsSignsUp() {
        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_SHORT, () -> signUp(anna, sat1, test));
        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_SHORT, () -> signUp(anna, sat1, sun1));

        service.register(station.id(), eventGroup.id(), anna.id(), Set.of(sat1), Map.of(), leader.id(), true);

        assertEquals(Map.of(sat1, RegistrationStatus.ACCEPTED), partsOf(anna));
        var standing = service.evaluate(eventGroup.id(), service.chosenParts(eventGroup.id(), anna.id()));
        assertTrue(standing.stream().anyMatch(result -> !result.met()));
    }

    @Test
    void aChoiceMustNameSomePartsOfThisEventGroup() {
        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_EMPTY, () -> signUp(anna));
        var stray = eventRepo.create(
                station.id(),
                "Fremd",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plus(Duration.ofHours(1)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_NOT_A_PART, () -> signUp(anna, sat1, sun1, test, stray.id()));
    }

    @Test
    void aFullEventGroupRefusesMembersAndAWithdrawalFreesThePlace() {
        eventGroup = jugendflamme(1, false, null);
        signUp(anna, sat1, sun1, test);

        assertRefused(EventRefusal.EVENT_GROUP_FULL, () -> signUp(ben, sat1, sun1, test));
        service.register(
                station.id(), eventGroup.id(), carla.id(), Set.of(sat1, sun1, test), Map.of(), leader.id(), true);

        service.withdraw(station.id(), eventGroup.id(), anna.id());
        service.withdraw(station.id(), eventGroup.id(), carla.id());
        signUp(ben, sat1, sun1, test);
        assertEquals(
                RegistrationStatus.ACCEPTED,
                service.find(eventGroup.id(), ben.id()).status());
    }

    @Test
    void afterTheDeadlineOnlyWhoeverRunsEventsSignsUp() {
        eventGroup = jugendflamme(null, false, Instant.now().minus(Duration.ofHours(1)));

        assertRefused(EventRefusal.EVENT_GROUP_REGISTRATION_CLOSED, () -> signUp(anna, sat1, sun1, test));
        service.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), Map.of(), leader.id(), true);
        assertEquals(
                RegistrationStatus.ACCEPTED,
                service.find(eventGroup.id(), anna.id()).status());
    }

    @Test
    void aCalledOffEventGroupTakesNoSignUps() {
        query("UPDATE event_group SET cancelled = TRUE WHERE id = :id;")
                .single(call().bind("id", eventGroup.id()))
                .update();

        assertRefused(EventRefusal.EVENT_GROUP_CANCELLED, () -> signUp(anna, sat1, sun1, test));
    }

    @Test
    void aSecondSignUpIsRefusedAndOneAfterWithdrawingTaken() {
        signUp(anna, sat1, sun1, test);
        assertRefused(EventRefusal.EVENT_GROUP_ALREADY_SIGNED_UP, () -> signUp(anna, sat1, sat2, test));

        service.withdraw(station.id(), eventGroup.id(), anna.id());
        signUp(anna, sun1, sat2, test);

        assertEquals(
                RegistrationStatus.ACCEPTED,
                service.find(eventGroup.id(), anna.id()).status());
        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(eventGroup.id(), anna.id()));
        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(anna).get(sat1));
    }

    @Test
    void confirmingOrTurningDownASignUpReachesEveryChosenPart() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);
        assertEquals(
                RegistrationStatus.PENDING,
                service.find(eventGroup.id(), anna.id()).status());
        assertEquals(
                Set.of(RegistrationStatus.PENDING), Set.copyOf(partsOf(anna).values()));

        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.ACCEPTED);
        assertEquals(
                Set.of(RegistrationStatus.ACCEPTED), Set.copyOf(partsOf(anna).values()));

        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.DENIED);
        assertEquals(Set.of(RegistrationStatus.DENIED), Set.copyOf(partsOf(anna).values()));
        assertEquals(
                RegistrationStatus.DENIED,
                service.find(eventGroup.id(), anna.id()).status());

        assertRefused(
                EventRefusal.EVENT_GROUP_DECISION_UNKNOWN,
                () -> service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.WITHDRAWN));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_SIGNED_UP,
                () -> service.decide(station.id(), eventGroup.id(), ben.id(), RegistrationStatus.ACCEPTED));
    }

    @Test
    void partsAreAddedAndDroppedWhileTheRulesStillHold() {
        signUp(anna, sat1, sun1, test);

        service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sun1, sat2, test), null, false);

        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(eventGroup.id(), anna.id()));
        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(anna).get(sat1));
        assertRefused(
                EventRefusal.EVENT_GROUP_CHOICE_SHORT,
                () -> service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sun1, sat2), null, false));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_SIGNED_UP,
                () -> service.changeParts(
                        station.id(), eventGroup.id(), ben.id(), Set.of(sun1, sat2, test), null, false));
    }

    @Test
    void aCalledOffPartAlreadyChosenStaysAndCountsButCannotBeAdded() {
        signUp(anna, sat1, sun1, test);
        callOff(sun1);

        service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), null, false);
        assertEquals(Set.of(sat1, sun1, test), service.chosenParts(eventGroup.id(), anna.id()));

        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_CALLED_OFF, () -> signUp(ben, sat1, sun1, test));
        signUp(ben, sat1, sat2, test);
    }

    @Test
    void withdrawingGivesUpAPlaceOrDeclinesASignUpStillWaiting() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        service.decide(station.id(), eventGroup.id(), ben.id(), RegistrationStatus.ACCEPTED);

        service.withdraw(station.id(), eventGroup.id(), anna.id());
        service.withdraw(station.id(), eventGroup.id(), ben.id());

        assertEquals(
                RegistrationStatus.DECLINED,
                service.find(eventGroup.id(), anna.id()).status());
        assertEquals(
                Set.of(RegistrationStatus.DECLINED), Set.copyOf(partsOf(anna).values()));
        assertEquals(
                RegistrationStatus.WITHDRAWN,
                service.find(eventGroup.id(), ben.id()).status());
        assertEquals(
                Set.of(RegistrationStatus.WITHDRAWN), Set.copyOf(partsOf(ben).values()));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_SIGNED_UP,
                () -> service.withdraw(station.id(), eventGroup.id(), ben.id()));
    }

    @Test
    void aPartTakesNoSignUpAnswerOrDecisionOfItsOwn() {
        var date = occurrenceCalendar.dateToAnswerFor(eventRepo.findById(sat1).orElseThrow(), null);
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP,
                () -> registrations.register(sat1, ben.id(), date, true, null));
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, () -> registrations.decline(sat1, ben.id(), date, null));

        signUp(anna, sat1, sun1, test);
        int partRegistration = eventRegistrationRepo
                .findByMemberInEvents(anna.id(), List.of(sat1))
                .getFirst()
                .id();
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP,
                () -> registrations.updateStatus(partRegistration, RegistrationStatus.DENIED));
        assertRefused(EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, () -> registrations.withdraw(partRegistration));
        assertRefused(EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, () -> registrations.refuse(partRegistration));
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, () -> registrations.undoWithdrawal(partRegistration));
    }

    @Test
    void aCalledOffTestDayIsNoLongerAskedForAndCannotBeChosen() {
        callOff(test);

        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_CALLED_OFF, () -> signUp(anna, sat1, sun1, test));
        signUp(anna, sat1, sun1);
        assertTrue(
                service.evaluate(eventGroup.id(), Set.of(sat1, sun1)).stream().allMatch(result -> result.met()));
    }

    @Test
    void signingUpAgainAfterBeingTurnedDownBringsBackOnlyTheNewChoice() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.DENIED);

        signUp(anna, sun1, sat2, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.ACCEPTED);

        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(eventGroup.id(), anna.id()));
        assertEquals(RegistrationStatus.DENIED, partsOf(anna).get(sat1));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(sat2));
    }

    @Test
    void aPlaceAnAppointmentFieldHoldsKeepsItsStatus() {
        eventGroup = jugendflamme(null, true, null);
        var held = eventRegistrationRepo.create(test, anna.id(), dateOf(test), RegistrationStatus.ACCEPTED, null);
        query("UPDATE event_registration SET from_field = TRUE WHERE id = :id;")
                .single(call().bind("id", held.id()))
                .update();

        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.DENIED);

        assertEquals(Set.of(sat1, sun1, test), service.chosenParts(eventGroup.id(), anna.id()));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(test));
        assertEquals(RegistrationStatus.DENIED, partsOf(anna).get(sat1));
    }

    @Test
    void aPartThatTookPlaceStaysAsItIs() {
        var sets = eventGroups.findSets(eventGroup.id()).stream()
                .collect(Collectors.toMap(EventGroupSet::name, EventGroupSet::id));
        int yesterday = part(eventGroup, sets.get("Übung"), -1);
        service.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(yesterday, sat1, test), Map.of(), leader.id(), true);

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_TOOK_PLACE,
                () -> service.changeParts(
                        station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), null, false));
        assertRefused(EventRefusal.EVENT_GROUP_PART_TOOK_PLACE, () -> signUp(ben, yesterday, sat1, test));

        service.withdraw(station.id(), eventGroup.id(), anna.id());

        var rows = eventRegistrationRepo.findByMemberInEvents(anna.id(), List.of(yesterday, sat1));
        var byPart = rows.stream().collect(Collectors.toMap(EventRegistration::eventId, EventRegistration::status));
        assertEquals(RegistrationStatus.ACCEPTED, byPart.get(yesterday));
        assertEquals(RegistrationStatus.WITHDRAWN, byPart.get(sat1));
    }

    @Test
    void afterTheDeadlineOnlyWhoeverRunsEventsChangesParts() {
        signUp(anna, sat1, sun1, test);
        query("UPDATE event_group SET registration_deadline = now() - INTERVAL '1 hour' WHERE id = :id;")
                .single(call().bind("id", eventGroup.id()))
                .update();

        assertRefused(
                EventRefusal.EVENT_GROUP_REGISTRATION_CLOSED,
                () -> service.changeParts(
                        station.id(), eventGroup.id(), anna.id(), Set.of(sun1, sat2, test), null, false));
        service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sun1, sat2, test), leader.id(), true);
        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(eventGroup.id(), anna.id()));
    }

    @Test
    void aPartAddedToASignUpStillWaitingWaitsToo() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);

        service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, sat2, test), null, false);

        assertEquals(RegistrationStatus.PENDING, partsOf(anna).get(sat2));
    }

    @Test
    void theLimitCountsOnlySignUpsHoldingOrWaitingForAPlace() {
        eventGroup = jugendflamme(2, true, null);
        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.DENIED);
        signUp(ben, sat1, sun1, test);
        service.withdraw(station.id(), eventGroup.id(), ben.id());

        signUp(carla, sat1, sun1, test);
        signUp(leader, sat1, sun1, test);

        assertRefused(EventRefusal.EVENT_GROUP_FULL, () -> signUp(ben, sat1, sun1, test));
    }

    @Test
    void aPartRemovedFromTheEventGroupIsNoLongerTouchedAndOneAddedLaterNotChosen() {
        signUp(anna, sat1, sun1, test);
        eventGroups.removePart(station.id(), eventGroup.id(), sat1);
        var sets = eventGroups.findSets(eventGroup.id()).stream()
                .collect(Collectors.toMap(EventGroupSet::name, EventGroupSet::id));
        int later = part(eventGroup, sets.get("Übung"), 29);

        assertEquals(Set.of(sun1, test), service.chosenParts(eventGroup.id(), anna.id()));
        assertTrue(!service.chosenParts(eventGroup.id(), anna.id()).contains(later));
        service.withdraw(station.id(), eventGroup.id(), anna.id());
        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(anna).get(sun1));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(sat1));
    }

    @Test
    void theStandingsOfSeveralMembersAreReadInOneGo() {
        signUp(anna, sat1, sun1, test);
        service.register(station.id(), eventGroup.id(), ben.id(), Set.of(sat1), Map.of(), leader.id(), true);

        var standings = service.standings(eventGroup.id(), List.of(anna.id(), ben.id(), carla.id()));

        assertEquals(Set.of(sat1, sun1, test), standings.get(0).counted());
        assertTrue(standings.get(0).sets().stream().allMatch(EventGroupRules.SetResult::met));
        assertTrue(standings.get(1).sets().stream().anyMatch(result -> !result.met()));
        assertEquals(null, standings.get(2).signUp());
        assertTrue(standings.get(2).counted().isEmpty());
    }

    @Test
    void aDecisionAndAWithdrawnPlaceAreAnnouncedOnceForTheEventGroup() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);

        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.ACCEPTED);
        service.withdraw(station.id(), eventGroup.id(), anna.id());

        var announced = ArgumentCaptor.forClass(DomainEvent.class);
        verify(bus, times(2)).publish(announced.capture());
        var statuses = announced.getAllValues().stream()
                .map(event -> (EventGroupRegistrationStatusChanged) event)
                .map(EventGroupRegistrationStatusChanged::newStatus)
                .toList();
        assertEquals(List.of(RegistrationStatus.ACCEPTED, RegistrationStatus.WITHDRAWN), statuses);
        assertEquals(eventGroup.id(), ((EventGroupRegistrationStatusChanged) announced.getValue()).eventGroupId());
    }

    @Test
    void withdrawingASignUpStillWaitingAnnouncesNothing() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);

        service.withdraw(station.id(), eventGroup.id(), anna.id());

        verify(bus, never()).publish(any());
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }
}
