/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CourseCancelled;
import dev.chojo.ember.event.events.CourseDeadlineExpired;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCourseSetDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.handler.EventCancelledHandler;
import dev.chojo.ember.feature.events.repository.EventCoursePartnerRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
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

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What people hear about a course: once for the course when it is called off or its deadline comes
 * and goes, never once for every part.
 */
class CourseNoticesTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventCourseRepository courseRepo = new EventCourseRepository();
    private EventCourseService courses;
    private EventCourseRegistrationService signUps;
    private Notifier notifier;
    private DomainEventBus announcements;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Course Notice Station");
        anna = memberOf("anna@notices.test", "Anna");
        ben = memberOf("ben@notices.test", "Ben");
    }

    private static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Notice");
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
        notifier = mock(Notifier.class);
        announcements = mock(DomainEventBus.class);
        courses = new EventCourseService(
                courseRepo,
                eventRepo,
                restrictionService,
                occurrenceCalendar,
                new CourseQuestionService(new EventRegistrationFieldRepository(), memberEligibility));
        var registrations = newEventServices(new DomainEventBus(Set.of())).registration();
        signUps = new EventCourseRegistrationService(
                courseRepo,
                new EventCourseRegistrationRepository(),
                eventRepo,
                eventRegistrationRepo,
                registrations,
                occurrenceCalendar,
                mock(DomainEventBus.class),
                memberNameResolver,
                new CourseQuestionService(new EventRegistrationFieldRepository(), memberEligibility),
                new EventCoursePartnerRepository());
        zone = occurrenceCalendar.zoneOf(station.id());
    }

    private EventCourse course(Instant deadline, boolean confirm) {
        return courses.create(
                station.id(),
                new EventCourseDetails("Jugendflamme 1", null, null, deadline, confirm),
                List.of(new EventCourseSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
    }

    private StationEvent part(EventCourse course, int daysAhead) {
        var start = LocalDate.now(zone)
                .plusDays(daysAhead)
                .atTime(9, 0)
                .atZone(zone)
                .toInstant();
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
        courses.addPart(
                station.id(),
                course.id(),
                event.id(),
                courses.findSets(course.id()).getFirst().id());
        return event;
    }

    @Test
    void callingACourseOffCallsOffItsPartsToComeAndTellsItsPeopleOnce() {
        var course = course(null, false);
        var past = part(course, -2);
        var first = part(course, 10);
        var second = part(course, 11);
        signUps.register(station.id(), course.id(), anna.id(), Set.of(first.id(), second.id()), Map.of(), null, false);
        var bus = new DomainEventBus(
                Set.of(new EventCancelledHandler(notifier, eventRegistrationRepo, occurrenceCalendar, courseRepo)));
        var cancellation = new EventCourseCancellationService(
                courseRepo,
                new EventCourseRegistrationRepository(),
                eventRepo,
                newEventServices(bus).cancellation(),
                occurrenceCalendar,
                announcements);

        cancellation.cancel(station.id(), course.id(), "Zu wenige Ausbilder", null);

        var calendar = occurrenceCalendar.forStation(station.id());
        assertTrue(calendar.cancelledAltogether(eventRepo.findById(first.id()).orElseThrow()));
        assertTrue(calendar.cancelledAltogether(eventRepo.findById(second.id()).orElseThrow()));
        assertFalse(calendar.cancelledAltogether(eventRepo.findById(past.id()).orElseThrow()));
        verify(notifier, never()).notify(any(), any(), any(), any());
        var announced = ArgumentCaptor.forClass(DomainEvent.class);
        verify(announcements).publish(announced.capture());
        var cancelled = (CourseCancelled) announced.getValue();
        assertEquals(List.of(anna.id()), cancelled.memberIds());
        assertEquals("Zu wenige Ausbilder", cancelled.reason());
        assertTrue(courseRepo.findById(course.id()).orElseThrow().cancelled());

        var again =
                assertThrows(RefusalResponse.class, () -> cancellation.cancel(station.id(), course.id(), null, null));
        assertEquals(EventRefusal.COURSE_CANCELLED, again.refusal());
    }

    @Test
    void aPassedDeadlineWithSignUpsWaitingIsToldOnceForTheCourse() {
        var course = course(Instant.now().plus(Duration.ofDays(2)), true);
        var day = part(course, 12);
        signUps.register(station.id(), course.id(), anna.id(), Set.of(day.id()), Map.of(), null, false);
        query("UPDATE event_course SET registration_deadline = now() - INTERVAL '1 minute' WHERE id = :id;")
                .single(call().bind("id", course.id()))
                .update();
        var checker = checker();

        checker.check();
        checker.check();

        var announced = ArgumentCaptor.forClass(DomainEvent.class);
        verify(announcements, atLeastOnce()).publish(announced.capture());
        var expired = announced.getAllValues().stream()
                .filter(event -> event instanceof CourseDeadlineExpired)
                .map(event -> (CourseDeadlineExpired) event)
                .filter(event -> event.courseId() == course.id())
                .toList();
        assertEquals(1, expired.size());
        assertEquals(1, expired.getFirst().pendingCount());
        assertTrue(eventRepo.findOneTimeEventsWithExpiredDeadline().stream()
                .noneMatch(entry -> entry.eventId() == day.id()));
    }

    @Test
    void membersWhoNeverAnsweredAreWarnedOnceBeforeTheCourseCloses() {
        var course = course(Instant.now().plus(Duration.ofHours(20)), false);
        var day = part(course, 13);
        signUps.register(station.id(), course.id(), anna.id(), Set.of(day.id()), Map.of(), null, false);
        var checker = checker();

        checker.check();
        checker.check();

        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier, times(1)).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), data.capture(), any());
        assertEquals("event-course", data.getValue().link().route());
        assertTrue(eventRepo.findEventsClosingIn(1).stream().noneMatch(entry -> entry.eventId() == day.id()));
    }

    private EventCourseCancellationService cancellationOver(EventCancellationService dates) {
        return new EventCourseCancellationService(
                courseRepo,
                new EventCourseRegistrationRepository(),
                eventRepo,
                dates,
                occurrenceCalendar,
                announcements);
    }

    @Test
    void aPartAlreadyOffIsPassedOverAndTheCourseStillCalledOffAndAnnounced() {
        var course = course(null, false);
        var first = part(course, 14);
        var second = part(course, 15);
        var dates = newEventServices(new DomainEventBus(Set.of())).cancellation();
        dates.cancelDate(first, first.startTime().atZone(zone).toLocalDate(), "Krank", null);

        cancellationOver(dates).cancel(station.id(), course.id(), null, null);

        assertTrue(occurrenceCalendar
                .forStation(station.id())
                .cancelledAltogether(eventRepo.findById(second.id()).orElseThrow()));
        verify(announcements).publish(any(CourseCancelled.class));
    }

    @Test
    void aCalledOffCourseStaysOffPartByPartAndTakesNoNewParts() {
        var course = course(null, false);
        var first = part(course, 16);
        var dates = newEventServices(new DomainEventBus(Set.of())).cancellation();
        cancellationOver(dates).cancel(station.id(), course.id(), null, null);
        var date = first.startTime().atZone(zone).toLocalDate();
        var stored = eventRepo.findById(first.id()).orElseThrow();

        var restore = assertThrows(RefusalResponse.class, () -> dates.restoreDate(stored, date));
        assertEquals(EventRefusal.COURSE_PART_STAYS_CANCELLED, restore.refusal());
        var another = eventRepo.create(
                station.id(),
                "Nachzügler",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                first.startTime(),
                first.endTime(),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var add = assertThrows(
                RefusalResponse.class,
                () -> courses.addPart(
                        station.id(),
                        course.id(),
                        another.id(),
                        courses.findSets(course.id()).getFirst().id()));
        assertEquals(EventRefusal.COURSE_CANCELLED, add.refusal());
    }

    @Test
    void movingTheDeadlineWarnsAgainAndACourseWithNothingLeftWarnsNobody() {
        var course = course(Instant.now().plus(Duration.ofHours(20)), false);
        part(course, 17);
        var checker = checker();
        checker.check();
        verify(notifier, atLeastOnce()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());

        courses.update(
                station.id(),
                course.id(),
                new EventCourseDetails(
                        "Jugendflamme 1", null, null, Instant.now().plus(Duration.ofHours(30)), false));
        var later = mock(Notifier.class);
        notifier = later;
        checker().check();
        verify(later, atLeastOnce()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());

        var empty = course(Instant.now().plus(Duration.ofHours(20)), false);
        var quiet = mock(Notifier.class);
        notifier = quiet;
        checker().check();
        assertTrue(courseRepo.findClosingIn(1).stream().noneMatch(entry -> entry.courseId() == empty.id()));
        verify(quiet, never()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());
    }

    @Test
    void onlyMembersTheCourseIsMeantForWhoNeverAnsweredAreWarned() {
        var course = course(Instant.now().plus(Duration.ofHours(20)), false);
        var day = part(course, 18);
        var former = memberOf("former@notices.test", "Fritz");
        query("UPDATE station_member SET former = TRUE WHERE id = :id;")
                .single(call().bind("id", former.id()))
                .update();
        signUps.register(station.id(), course.id(), anna.id(), Set.of(day.id()), Map.of(), null, false);
        signUps.withdraw(station.id(), course.id(), anna.id());
        courses.setAudiences(
                station.id(),
                course.id(),
                RestrictionAudience.empty(),
                new RestrictionAudience(List.of(), List.of(), List.of(), List.of(anna.id()), RestrictionMode.AND));

        assertEquals(
                List.of(),
                courseRepo.findUnansweredMemberIds(course.id(), station.id()).stream()
                        .filter(id -> id == anna.id() || id == former.id())
                        .toList());
        checker().check();

        verify(notifier, never()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());
    }

    @Test
    void theListOfAnswersStillOwedLeavesPartsOut() {
        var course = course(Instant.now().plus(Duration.ofDays(5)), false);
        var day = part(course, 19);

        assertTrue(eventRegistrationRepo.findAwaitingAnswer(List.of(ben.id())).stream()
                .noneMatch(entry -> entry.eventId() == day.id()));
    }

    @Test
    void aSinglePartCalledOffOnALiveCourseStillTellsItsPeople() {
        var course = course(null, false);
        var day = part(course, 20);
        signUps.register(station.id(), course.id(), ben.id(), Set.of(day.id()), Map.of(), null, false);
        var bus = new DomainEventBus(
                Set.of(new EventCancelledHandler(notifier, eventRegistrationRepo, occurrenceCalendar, courseRepo)));

        newEventServices(bus)
                .cancellation()
                .cancelDate(day, day.startTime().atZone(zone).toLocalDate(), null, null);

        verify(notifier).notify(any(), eq(NotificationType.EVENT_CANCELLED), any(), any());
    }

    private CourseDeadlineChecker checker() {
        var readOnly = mock(StationReadOnlyGuard.class);
        when(readOnly.isWritable(anyInt())).thenReturn(true);
        return new CourseDeadlineChecker(
                courseRepo, announcements, notifier, memberNameResolver, restrictionService, readOnly);
    }
}
