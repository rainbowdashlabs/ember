/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.mockito.Mockito.mock;

/**
 * Signing up for a Jugendflamme event group: three practice days of which two count, and a test day that
 * is mandatory. Holds the station, members and helpers the event group sign-up tests share.
 */
abstract class EventGroupRegistrationTestBase extends RepositoryTestBase {
    protected static Station station;
    protected static StationMember anna;
    protected static StationMember ben;
    protected static StationMember carla;
    protected static StationMember leader;
    protected static final List<Integer> accounts = new ArrayList<>();

    protected final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    protected EventGroupService eventGroups;
    protected EventGroupRegistrationService service;
    protected EventGroupQuestionService questions;
    protected EventRegistrationService registrations;
    protected EventCancellationService cancellation;
    protected DomainEventBus bus;
    protected ZoneId zone;

    protected EventGroup eventGroup;
    protected int sat1;
    protected int sun1;
    protected int sat2;
    protected int test;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event group Sign-up Station");
        anna = memberOf("anna@signup.test", "Anna");
        ben = memberOf("ben@signup.test", "Ben");
        carla = memberOf("carla@signup.test", "Carla");
        leader = memberOf("leader@signup.test", "Lea");
    }

    protected static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Sign-up");
        accounts.add(account.id());
        return stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
        accounts.clear();
    }

    @BeforeEach
    void wire() {
        bus = mock(DomainEventBus.class);
        var eventServices = newEventServices(new DomainEventBus(Set.of()));
        registrations = eventServices.registration();
        cancellation = eventServices.cancellation();
        eventGroups = newEventGroupService();
        questions = new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility);
        service = new EventGroupRegistrationService(
                eventGroupRepo,
                new EventGroupRegistrationRepository(),
                eventRegistrationRepo,
                registrations,
                bus,
                memberNameResolver,
                questions,
                newEventGroupSignUpRules());
        zone = occurrenceCalendar.zoneOf(station.id());
        eventGroup = jugendflamme(null, false, Instant.now().plus(Duration.ofDays(5)));
    }

    protected EventGroup jugendflamme(Integer limit, boolean confirm, Instant deadline) {
        var created = eventGroups.create(
                station.id(),
                new EventGroupDetails("Jugendflamme 1", null, limit, deadline, confirm, null),
                List.of(new EventGroupSetDraft(null, "Übung", 2), new EventGroupSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var sets = eventGroups.findSets(created.id()).stream()
                .collect(Collectors.toMap(EventGroupSet::name, EventGroupSet::id));
        sat1 = part(created, sets.get("Übung"), 20).id();
        sun1 = part(created, sets.get("Übung"), 21).id();
        sat2 = part(created, sets.get("Übung"), 27).id();
        test = part(created, sets.get("Prüfung"), 28).id();
        return created;
    }

    protected void callOff(int partId) {
        var part = eventRepo.findById(partId).orElseThrow();
        cancellation.cancelDate(part, dateOf(partId), "Unwetter", null);
    }

    protected LocalDate dateOf(int partId) {
        return eventRepo.findById(partId).orElseThrow().startTime().atZone(zone).toLocalDate();
    }

    protected Map<Integer, RegistrationStatus> partsOf(StationMember member) {
        return registrationsOf(member, List.of(sat1, sun1, sat2, test)).stream()
                .collect(Collectors.toMap(EventRegistration::eventId, EventRegistration::status));
    }

    protected EventGroupRegistrationService.EventGroupStanding standingOf(StationMember member) {
        return service.standings(eventGroup.id(), List.of(member.id())).getFirst();
    }

    protected List<EventRegistration> registrationsOf(StationMember member, List<Integer> eventIds) {
        return eventIds.stream()
                .flatMap(eventId -> eventRegistrationRepo.findByEvent(eventId).stream())
                .filter(registration -> registration.memberId() == member.id())
                .sorted(Comparator.comparing(EventRegistration::eventId).thenComparing(EventRegistration::eventDate))
                .toList();
    }

    protected void signUp(StationMember member, Integer... parts) {
        service.register(station.id(), eventGroup.id(), member.id(), Set.of(parts), Map.of(), null, false);
    }

    protected Map<String, Integer> askShirtAndAllergies() {
        var required = new EventQuestionSettings(
                null, null, null, null, null, false, false, true, null, null, null, false, null);
        var forOrganisers = new EventQuestionSettings(
                null, null, null, null, null, false, false, false, null, null, null, true, null);
        questions.replaceQuestions(
                eventGroup.id(),
                List.of(
                        new RegistrationFieldDraft("T-Shirt", FieldType.TEXT, required, false),
                        new RegistrationFieldDraft("Allergien", FieldType.TEXT, forOrganisers, false)));
        return questions.findByEventGroup(eventGroup.id()).stream()
                .collect(Collectors.toMap(EventRegistrationField::name, EventRegistrationField::id));
    }

    protected Map<Integer, String> answersOf(StationMember member) {
        int signUpId = service.find(eventGroup.id(), member.id()).id();
        return questions.answersBySignUp(List.of(signUpId)).getOrDefault(signUpId, List.of()).stream()
                .collect(Collectors.toMap(RegistrationFieldValue::fieldId, RegistrationFieldValue::value));
    }

    protected void markPresent(int templateId, int partId, StationMember member) {
        var part = eventRepo.findById(partId).orElseThrow();
        var sheet = attendanceRepo.createSession(templateId, part.startTime(), part.endTime(), partId, null, null);
        attendanceRepo.createEntry(sheet.id(), member.id(), AttendanceStatus.PRESENT, EntrySource.EXPECTED);
    }

    protected List<Integer> registrationIdsOf(StationMember member) {
        return registrationsOf(member, List.of(sat1, sun1, sat2, test)).stream()
                .map(EventRegistration::id)
                .toList();
    }

    protected static EventRegistrationFieldRepository eventRegistrationFieldRepo() {
        return new EventRegistrationFieldRepository();
    }
}
