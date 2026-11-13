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
import dev.chojo.ember.event.events.CourseRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.feature.events.entity.EventCourseSetDraft;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCourseRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
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
 * Signing up for a Jugendflamme course: three practice days of which two count, and a test day that
 * is mandatory.
 */
class EventCourseRegistrationServiceTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static StationMember carla;
    private static StationMember leader;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventCourseRepository courseRepo = new EventCourseRepository();
    private EventCourseService courses;
    private EventCourseRegistrationService service;
    private EventRegistrationService registrations;
    private EventCancellationService cancellation;
    private DomainEventBus bus;
    private ZoneId zone;

    private EventCourse course;
    private int sat1;
    private int sun1;
    private int sat2;
    private int test;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Course Sign-up Station");
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
        courses = new EventCourseService(courseRepo, eventRepo, restrictionService, occurrenceCalendar);
        service = new EventCourseRegistrationService(
                courseRepo,
                new EventCourseRegistrationRepository(),
                eventRepo,
                eventRegistrationRepo,
                registrations,
                occurrenceCalendar,
                bus,
                memberNameResolver);
        zone = occurrenceCalendar.zoneOf(station.id());
        course = jugendflamme(null, false, Instant.now().plus(Duration.ofDays(5)));
    }

    private EventCourse jugendflamme(Integer limit, boolean confirm, Instant deadline) {
        var created = courses.create(
                station.id(),
                new EventCourseDetails("Jugendflamme 1", null, limit, deadline, confirm),
                List.of(new EventCourseSetDraft(null, "Übung", 2), new EventCourseSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty());
        var sets = courses.findSets(created.id()).stream()
                .collect(Collectors.toMap(EventCourseSet::name, EventCourseSet::id));
        sat1 = part(created, sets.get("Übung"), 20);
        sun1 = part(created, sets.get("Übung"), 21);
        sat2 = part(created, sets.get("Übung"), 27);
        test = part(created, sets.get("Prüfung"), 28);
        return created;
    }

    private int part(EventCourse into, int setId, int daysAhead) {
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
        courses.addPart(station.id(), into.id(), event.id(), setId);
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
        service.register(station.id(), course.id(), member.id(), Set.of(parts), null, false);
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
                service.find(course.id(), anna.id()).status());
        assertEquals(Set.of(sat1, sun1, test), service.chosenParts(course.id(), anna.id()));
    }

    @Test
    void aChoiceBreakingTheRulesIsRefusedUnlessWhoeverRunsEventsSignsUp() {
        assertRefused(EventRefusal.COURSE_CHOICE_SHORT, () -> signUp(anna, sat1, test));
        assertRefused(EventRefusal.COURSE_CHOICE_SHORT, () -> signUp(anna, sat1, sun1));

        service.register(station.id(), course.id(), anna.id(), Set.of(sat1), leader.id(), true);

        assertEquals(Map.of(sat1, RegistrationStatus.ACCEPTED), partsOf(anna));
        var standing = service.evaluate(course.id(), service.chosenParts(course.id(), anna.id()));
        assertTrue(standing.stream().anyMatch(result -> !result.met()));
    }

    @Test
    void aChoiceMustNameSomePartsOfThisCourse() {
        assertRefused(EventRefusal.COURSE_CHOICE_EMPTY, () -> signUp(anna));
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
        assertRefused(EventRefusal.COURSE_CHOICE_NOT_A_PART, () -> signUp(anna, sat1, sun1, test, stray.id()));
    }

    @Test
    void aFullCourseRefusesMembersAndAWithdrawalFreesThePlace() {
        course = jugendflamme(1, false, null);
        signUp(anna, sat1, sun1, test);

        assertRefused(EventRefusal.COURSE_FULL, () -> signUp(ben, sat1, sun1, test));
        service.register(station.id(), course.id(), carla.id(), Set.of(sat1, sun1, test), leader.id(), true);

        service.withdraw(station.id(), course.id(), anna.id());
        service.withdraw(station.id(), course.id(), carla.id());
        signUp(ben, sat1, sun1, test);
        assertEquals(
                RegistrationStatus.ACCEPTED, service.find(course.id(), ben.id()).status());
    }

    @Test
    void afterTheDeadlineOnlyWhoeverRunsEventsSignsUp() {
        course = jugendflamme(null, false, Instant.now().minus(Duration.ofHours(1)));

        assertRefused(EventRefusal.COURSE_REGISTRATION_CLOSED, () -> signUp(anna, sat1, sun1, test));
        service.register(station.id(), course.id(), anna.id(), Set.of(sat1, sun1, test), leader.id(), true);
        assertEquals(
                RegistrationStatus.ACCEPTED,
                service.find(course.id(), anna.id()).status());
    }

    @Test
    void aCalledOffCourseTakesNoSignUps() {
        query("UPDATE event_course SET cancelled = TRUE WHERE id = :id;")
                .single(call().bind("id", course.id()))
                .update();

        assertRefused(EventRefusal.COURSE_CANCELLED, () -> signUp(anna, sat1, sun1, test));
    }

    @Test
    void aSecondSignUpIsRefusedAndOneAfterWithdrawingTaken() {
        signUp(anna, sat1, sun1, test);
        assertRefused(EventRefusal.COURSE_ALREADY_SIGNED_UP, () -> signUp(anna, sat1, sat2, test));

        service.withdraw(station.id(), course.id(), anna.id());
        signUp(anna, sun1, sat2, test);

        assertEquals(
                RegistrationStatus.ACCEPTED,
                service.find(course.id(), anna.id()).status());
        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(course.id(), anna.id()));
        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(anna).get(sat1));
    }

    @Test
    void confirmingOrTurningDownASignUpReachesEveryChosenPart() {
        course = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);
        assertEquals(
                RegistrationStatus.PENDING, service.find(course.id(), anna.id()).status());
        assertEquals(
                Set.of(RegistrationStatus.PENDING), Set.copyOf(partsOf(anna).values()));

        service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.ACCEPTED);
        assertEquals(
                Set.of(RegistrationStatus.ACCEPTED), Set.copyOf(partsOf(anna).values()));

        service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.DENIED);
        assertEquals(Set.of(RegistrationStatus.DENIED), Set.copyOf(partsOf(anna).values()));
        assertEquals(
                RegistrationStatus.DENIED, service.find(course.id(), anna.id()).status());

        assertRefused(
                EventRefusal.COURSE_DECISION_UNKNOWN,
                () -> service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.WITHDRAWN));
        assertRefused(
                EventRefusal.COURSE_NOT_SIGNED_UP,
                () -> service.decide(station.id(), course.id(), ben.id(), RegistrationStatus.ACCEPTED));
    }

    @Test
    void partsAreAddedAndDroppedWhileTheRulesStillHold() {
        signUp(anna, sat1, sun1, test);

        service.changeParts(station.id(), course.id(), anna.id(), Set.of(sun1, sat2, test), null, false);

        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(course.id(), anna.id()));
        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(anna).get(sat1));
        assertRefused(
                EventRefusal.COURSE_CHOICE_SHORT,
                () -> service.changeParts(station.id(), course.id(), anna.id(), Set.of(sun1, sat2), null, false));
        assertRefused(
                EventRefusal.COURSE_NOT_SIGNED_UP,
                () -> service.changeParts(station.id(), course.id(), ben.id(), Set.of(sun1, sat2, test), null, false));
    }

    @Test
    void aCalledOffPartAlreadyChosenStaysAndCountsButCannotBeAdded() {
        signUp(anna, sat1, sun1, test);
        callOff(sun1);

        service.changeParts(station.id(), course.id(), anna.id(), Set.of(sat1, sun1, test), null, false);
        assertEquals(Set.of(sat1, sun1, test), service.chosenParts(course.id(), anna.id()));

        assertRefused(EventRefusal.COURSE_CHOICE_CALLED_OFF, () -> signUp(ben, sat1, sun1, test));
        signUp(ben, sat1, sat2, test);
    }

    @Test
    void withdrawingGivesUpAPlaceOrDeclinesASignUpStillWaiting() {
        course = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        service.decide(station.id(), course.id(), ben.id(), RegistrationStatus.ACCEPTED);

        service.withdraw(station.id(), course.id(), anna.id());
        service.withdraw(station.id(), course.id(), ben.id());

        assertEquals(
                RegistrationStatus.DECLINED,
                service.find(course.id(), anna.id()).status());
        assertEquals(
                Set.of(RegistrationStatus.DECLINED), Set.copyOf(partsOf(anna).values()));
        assertEquals(
                RegistrationStatus.WITHDRAWN,
                service.find(course.id(), ben.id()).status());
        assertEquals(
                Set.of(RegistrationStatus.WITHDRAWN), Set.copyOf(partsOf(ben).values()));
        assertRefused(EventRefusal.COURSE_NOT_SIGNED_UP, () -> service.withdraw(station.id(), course.id(), ben.id()));
    }

    @Test
    void aPartTakesNoSignUpAnswerOrDecisionOfItsOwn() {
        var date = occurrenceCalendar.dateToAnswerFor(eventRepo.findById(sat1).orElseThrow(), null);
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_COURSE,
                () -> registrations.register(sat1, ben.id(), date, true, null));
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_COURSE, () -> registrations.decline(sat1, ben.id(), date, null));

        signUp(anna, sat1, sun1, test);
        int partRegistration = eventRegistrationRepo
                .findByMemberInEvents(anna.id(), List.of(sat1))
                .getFirst()
                .id();
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_COURSE,
                () -> registrations.updateStatus(partRegistration, RegistrationStatus.DENIED));
        assertRefused(EventRefusal.REGISTRATION_THROUGH_COURSE, () -> registrations.withdraw(partRegistration));
        assertRefused(EventRefusal.REGISTRATION_THROUGH_COURSE, () -> registrations.refuse(partRegistration));
        assertRefused(EventRefusal.REGISTRATION_THROUGH_COURSE, () -> registrations.undoWithdrawal(partRegistration));
    }

    @Test
    void aCalledOffTestDayIsNoLongerAskedForAndCannotBeChosen() {
        callOff(test);

        assertRefused(EventRefusal.COURSE_CHOICE_CALLED_OFF, () -> signUp(anna, sat1, sun1, test));
        signUp(anna, sat1, sun1);
        assertTrue(service.evaluate(course.id(), Set.of(sat1, sun1)).stream().allMatch(result -> result.met()));
    }

    @Test
    void signingUpAgainAfterBeingTurnedDownBringsBackOnlyTheNewChoice() {
        course = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.DENIED);

        signUp(anna, sun1, sat2, test);
        service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.ACCEPTED);

        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(course.id(), anna.id()));
        assertEquals(RegistrationStatus.DENIED, partsOf(anna).get(sat1));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(sat2));
    }

    @Test
    void aPlaceAnAppointmentFieldHoldsKeepsItsStatus() {
        course = jugendflamme(null, true, null);
        var held = eventRegistrationRepo.create(test, anna.id(), dateOf(test), RegistrationStatus.ACCEPTED, null);
        query("UPDATE event_registration SET from_field = TRUE WHERE id = :id;")
                .single(call().bind("id", held.id()))
                .update();

        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.DENIED);

        assertEquals(Set.of(sat1, sun1, test), service.chosenParts(course.id(), anna.id()));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(test));
        assertEquals(RegistrationStatus.DENIED, partsOf(anna).get(sat1));
    }

    @Test
    void aPartThatTookPlaceStaysAsItIs() {
        var sets = courses.findSets(course.id()).stream()
                .collect(Collectors.toMap(EventCourseSet::name, EventCourseSet::id));
        int yesterday = part(course, sets.get("Übung"), -1);
        service.register(station.id(), course.id(), anna.id(), Set.of(yesterday, sat1, test), leader.id(), true);

        assertRefused(
                EventRefusal.COURSE_PART_TOOK_PLACE,
                () -> service.changeParts(station.id(), course.id(), anna.id(), Set.of(sat1, sun1, test), null, false));
        assertRefused(EventRefusal.COURSE_PART_TOOK_PLACE, () -> signUp(ben, yesterday, sat1, test));

        service.withdraw(station.id(), course.id(), anna.id());

        var rows = eventRegistrationRepo.findByMemberInEvents(anna.id(), List.of(yesterday, sat1));
        var byPart = rows.stream().collect(Collectors.toMap(EventRegistration::eventId, EventRegistration::status));
        assertEquals(RegistrationStatus.ACCEPTED, byPart.get(yesterday));
        assertEquals(RegistrationStatus.WITHDRAWN, byPart.get(sat1));
    }

    @Test
    void afterTheDeadlineOnlyWhoeverRunsEventsChangesParts() {
        signUp(anna, sat1, sun1, test);
        query("UPDATE event_course SET registration_deadline = now() - INTERVAL '1 hour' WHERE id = :id;")
                .single(call().bind("id", course.id()))
                .update();

        assertRefused(
                EventRefusal.COURSE_REGISTRATION_CLOSED,
                () -> service.changeParts(station.id(), course.id(), anna.id(), Set.of(sun1, sat2, test), null, false));
        service.changeParts(station.id(), course.id(), anna.id(), Set.of(sun1, sat2, test), leader.id(), true);
        assertEquals(Set.of(sun1, sat2, test), service.chosenParts(course.id(), anna.id()));
    }

    @Test
    void aPartAddedToASignUpStillWaitingWaitsToo() {
        course = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);

        service.changeParts(station.id(), course.id(), anna.id(), Set.of(sat1, sun1, sat2, test), null, false);

        assertEquals(RegistrationStatus.PENDING, partsOf(anna).get(sat2));
    }

    @Test
    void theLimitCountsOnlySignUpsHoldingOrWaitingForAPlace() {
        course = jugendflamme(2, true, null);
        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.DENIED);
        signUp(ben, sat1, sun1, test);
        service.withdraw(station.id(), course.id(), ben.id());

        signUp(carla, sat1, sun1, test);
        signUp(leader, sat1, sun1, test);

        assertRefused(EventRefusal.COURSE_FULL, () -> signUp(ben, sat1, sun1, test));
    }

    @Test
    void aPartRemovedFromTheCourseIsNoLongerTouchedAndOneAddedLaterNotChosen() {
        signUp(anna, sat1, sun1, test);
        courses.removePart(station.id(), course.id(), sat1);
        var sets = courses.findSets(course.id()).stream()
                .collect(Collectors.toMap(EventCourseSet::name, EventCourseSet::id));
        int later = part(course, sets.get("Übung"), 29);

        assertEquals(Set.of(sun1, test), service.chosenParts(course.id(), anna.id()));
        assertTrue(!service.chosenParts(course.id(), anna.id()).contains(later));
        service.withdraw(station.id(), course.id(), anna.id());
        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(anna).get(sun1));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(sat1));
    }

    @Test
    void theStandingsOfSeveralMembersAreReadInOneGo() {
        signUp(anna, sat1, sun1, test);
        service.register(station.id(), course.id(), ben.id(), Set.of(sat1), leader.id(), true);

        var standings = service.standings(course.id(), List.of(anna.id(), ben.id(), carla.id()));

        assertEquals(Set.of(sat1, sun1, test), standings.get(0).counted());
        assertTrue(standings.get(0).sets().stream().allMatch(CourseRules.SetResult::met));
        assertTrue(standings.get(1).sets().stream().anyMatch(result -> !result.met()));
        assertEquals(null, standings.get(2).signUp());
        assertTrue(standings.get(2).counted().isEmpty());
    }

    @Test
    void aDecisionAndAWithdrawnPlaceAreAnnouncedOnceForTheCourse() {
        course = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);

        service.decide(station.id(), course.id(), anna.id(), RegistrationStatus.ACCEPTED);
        service.withdraw(station.id(), course.id(), anna.id());

        var announced = ArgumentCaptor.forClass(DomainEvent.class);
        verify(bus, times(2)).publish(announced.capture());
        var statuses = announced.getAllValues().stream()
                .map(event -> (CourseRegistrationStatusChanged) event)
                .map(CourseRegistrationStatusChanged::newStatus)
                .toList();
        assertEquals(List.of(RegistrationStatus.ACCEPTED, RegistrationStatus.WITHDRAWN), statuses);
        assertEquals(course.id(), ((CourseRegistrationStatusChanged) announced.getValue()).courseId());
    }

    @Test
    void withdrawingASignUpStillWaitingAnnouncesNothing() {
        course = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);

        service.withdraw(station.id(), course.id(), anna.id());

        verify(bus, never()).publish(any());
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }
}
