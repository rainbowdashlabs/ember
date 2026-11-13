/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.feature.events.entity.EventCourseSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Shaping a course like a Jugendflamme weekend: three practice days and a test, sorted into two
 * sets, with the course's sign-up and audiences handed down to every part.
 */
class EventCourseServiceTest extends RepositoryTestBase {
    private static Station station;
    private static Station otherStation;
    private static StationMember anna;
    private static StationMember outsider;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventCourseRepository courseRepo = new EventCourseRepository();
    private EventCourseService service;
    private Instant deadline;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Course Station");
        otherStation = stationRepo.create("Other Course Station");
        var account = accountRepo.create("anna@course.test", "Anna", "Course");
        accounts.add(account.id());
        anna = stationMemberRepo.create(station.id(), account.id());
        var otherAccount = accountRepo.create("otto@course.test", "Otto", "Course");
        accounts.add(otherAccount.id());
        outsider = stationMemberRepo.create(otherStation.id(), otherAccount.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        service = new EventCourseService(courseRepo, eventRepo, restrictionService, occurrenceCalendar);
        deadline = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);
    }

    private EventCourseDetails details(String name, boolean confirm) {
        return new EventCourseDetails(name, "Lehrgang", 12, deadline, confirm);
    }

    private EventCourse jugendflamme() {
        return service.create(
                station.id(),
                details("Jugendflamme 1", true),
                List.of(new EventCourseSetDraft(null, "Übung", 2), new EventCourseSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty());
    }

    private EventCourse create(EventCourseDetails details, List<EventCourseSetDraft> sets) {
        return service.create(station.id(), details, sets, RestrictionAudience.empty(), RestrictionAudience.empty());
    }

    private StationEvent appointment(Station at, int daysAhead, StationEvent.EventType type) {
        var start = LocalDate.now().plusDays(daysAhead).atTime(9, 0).toInstant(ZoneOffset.UTC);
        return eventRepo.create(
                at.id(),
                "Tag " + daysAhead,
                null,
                type,
                type == StationEvent.EventType.ONE_TIME ? null : 6,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                false,
                null,
                false,
                null,
                5,
                null,
                null,
                3);
    }

    private StationEvent oneTime(int daysAhead) {
        return appointment(station, daysAhead, StationEvent.EventType.ONE_TIME);
    }

    private EventCourseSet set(EventCourse course, String name) {
        return service.findSets(course.id()).stream()
                .filter(set -> set.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static RestrictionAudience youth() {
        return new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);
    }

    @Test
    void aCourseIsCreatedWithItsSetsInOrder() {
        var course = jugendflamme();

        var sets = service.findSets(course.id());
        assertEquals(
                List.of("Übung", "Prüfung"),
                sets.stream().map(EventCourseSet::name).toList());
        assertEquals(List.of(2, 1), sets.stream().map(EventCourseSet::minimum).toList());
        assertEquals(12, course.registrationLimit());
        assertEquals(deadline, course.registrationDeadline());
        assertTrue(course.requiresConfirmation());
    }

    @Test
    void aCourseNeedsANameAndASetAndNoSetAsksForLessThanNothing() {
        var sets = List.of(new EventCourseSetDraft(null, "Teile", 1));
        assertRefused(EventRefusal.COURSE_NAME_MISSING, () -> create(details(" ", false), sets));
        assertRefused(EventRefusal.COURSE_NEEDS_A_SET, () -> create(details("Kurs", false), List.of()));
        assertRefused(
                EventRefusal.COURSE_NAME_MISSING,
                () -> create(details("Kurs", false), List.of(new EventCourseSetDraft(null, "", 1))));
        assertRefused(
                EventRefusal.COURSE_SET_MINIMUM_NEGATIVE,
                () -> create(details("Kurs", false), List.of(new EventCourseSetDraft(null, "Teile", -1))));
        assertRefused(
                EventRefusal.COURSE_LIMIT_NOT_POSITIVE,
                () -> create(new EventCourseDetails("Kurs", null, 0, null, false), sets));
        assertRefused(
                EventRefusal.COURSE_NEEDS_A_SET,
                () -> service.replaceSets(station.id(), jugendflamme().id(), List.of()));
    }

    @Test
    void aPartTakesTheCoursesSignUpAndCarriesNoLimitOfItsOwn() {
        var course = jugendflamme();
        var saturday = oneTime(20);

        var part = service.addPart(
                station.id(), course.id(), saturday.id(), set(course, "Übung").id());

        assertEquals(course.id(), part.courseId());
        var stored = eventRepo.findById(saturday.id()).orElseThrow();
        assertTrue(stored.requiresRegistration());
        assertTrue(stored.requiresConfirmation());
        assertEquals(deadline, stored.registrationDeadline());
        assertNull(stored.registrationLimit());
        assertNull(stored.registrationCloseDays());
    }

    @Test
    void changingTheCourseChangesTheSignUpOfEveryPart() {
        var course = jugendflamme();
        var saturday = oneTime(21);
        service.addPart(
                station.id(), course.id(), saturday.id(), set(course, "Übung").id());

        service.update(station.id(), course.id(), new EventCourseDetails("Jugendflamme 1", null, null, null, false));

        var stored = eventRepo.findById(saturday.id()).orElseThrow();
        assertFalse(stored.requiresConfirmation());
        assertNull(stored.registrationDeadline());
    }

    @Test
    void onlyAOneTimeAppointmentOfTheStationWithoutSignUpsOfItsOwnBecomesAPart() {
        var course = jugendflamme();
        int practice = set(course, "Übung").id();
        var series = appointment(station, 22, StationEvent.EventType.RECURRING);
        var elsewhere = appointment(otherStation, 22, StationEvent.EventType.ONE_TIME);
        var signedUp = oneTime(23);
        eventRegistrationRepo.create(
                signedUp.id(), anna.id(), LocalDate.now().plusDays(23), RegistrationStatus.ACCEPTED, null);

        assertRefused(
                EventRefusal.COURSE_PART_NOT_ONE_TIME,
                () -> service.addPart(station.id(), course.id(), series.id(), practice));
        assertRefused(
                EventRefusal.COURSE_PART_NOT_HERE,
                () -> service.addPart(station.id(), course.id(), elsewhere.id(), practice));
        assertRefused(
                EventRefusal.COURSE_PART_HAS_SIGN_UPS,
                () -> service.addPart(station.id(), course.id(), signedUp.id(), practice));
    }

    @Test
    void anAppointmentIsAPartOfOneCourseAndOnlyInItsSets() {
        var course = jugendflamme();
        var other = jugendflamme();
        var sunday = oneTime(24);

        assertRefused(
                EventRefusal.COURSE_SET_NOT_IN_COURSE,
                () -> service.addPart(
                        station.id(),
                        course.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
        service.addPart(
                station.id(), course.id(), sunday.id(), set(course, "Übung").id());
        assertRefused(
                EventRefusal.COURSE_PART_TAKEN,
                () -> service.addPart(
                        station.id(),
                        other.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
    }

    @Test
    void aPartMovesBetweenTheSetsOfItsCourse() {
        var course = jugendflamme();
        var sunday = oneTime(25);
        service.addPart(
                station.id(), course.id(), sunday.id(), set(course, "Übung").id());

        var moved = service.movePart(
                station.id(), course.id(), sunday.id(), set(course, "Prüfung").id());

        assertEquals(set(course, "Prüfung").id(), moved.setId());
    }

    @Test
    void aRemovedPartKeepsWhoWasSignedUpForIt() {
        var course = jugendflamme();
        var sunday = oneTime(26);
        service.addPart(
                station.id(), course.id(), sunday.id(), set(course, "Prüfung").id());
        var date = LocalDate.now().plusDays(26);
        eventRegistrationRepo.create(sunday.id(), anna.id(), date, RegistrationStatus.ACCEPTED, null);

        service.removePart(station.id(), course.id(), sunday.id());

        assertTrue(service.findParts(course.id()).isEmpty());
        assertEquals(1, eventRegistrationRepo.countAccepted(sunday.id(), date));
    }

    @Test
    void setsAreRenamedReorderedAddedAndEmptyOnesRemoved() {
        var course = jugendflamme();
        var practice = set(course, "Übung");
        var test = set(course, "Prüfung");

        var sets = service.replaceSets(
                station.id(),
                course.id(),
                List.of(
                        new EventCourseSetDraft(test.id(), "Abschlussprüfung", 1),
                        new EventCourseSetDraft(null, "Zusatz", 0)));

        assertEquals(
                List.of("Abschlussprüfung", "Zusatz"),
                sets.stream().map(EventCourseSet::name).toList());
        assertEquals(test.id(), sets.getFirst().id());
        assertTrue(sets.stream().noneMatch(set -> set.id() == practice.id()));
    }

    @Test
    void aSetHoldingPartsOrOneOfAnotherCourseIsRefused() {
        var course = jugendflamme();
        var other = jugendflamme();
        var practice = set(course, "Übung");
        var test = set(course, "Prüfung");
        service.addPart(station.id(), course.id(), oneTime(27).id(), practice.id());

        assertRefused(
                EventRefusal.COURSE_SET_HOLDS_PARTS,
                () -> service.replaceSets(
                        station.id(), course.id(), List.of(new EventCourseSetDraft(test.id(), "Prüfung", 1))));
        assertRefused(
                EventRefusal.COURSE_SET_NOT_IN_COURSE,
                () -> service.replaceSets(
                        station.id(),
                        course.id(),
                        List.of(new EventCourseSetDraft(set(other, "Übung").id(), "Fremd", 1))));
        assertRefused(
                EventRefusal.COURSE_SET_NOT_IN_COURSE,
                () -> service.replaceSets(
                        station.id(),
                        course.id(),
                        List.of(
                                new EventCourseSetDraft(practice.id(), "Übung", 2),
                                new EventCourseSetDraft(practice.id(), "Übung", 2))));
    }

    @Test
    void theCoursesAudiencesReachEveryPartAndEveryPartAddedLater() {
        var course = jugendflamme();
        var first = oneTime(28);
        service.addPart(
                station.id(), course.id(), first.id(), set(course, "Übung").id());

        service.setAudiences(station.id(), course.id(), youth(), youth());
        var later = oneTime(29);
        service.addPart(
                station.id(), course.id(), later.id(), set(course, "Übung").id());

        for (var part : List.of(first, later)) {
            assertEquals(
                    List.of(StationUserType.MEMBER),
                    restrictionService
                            .audienceOf(RestrictionType.EVENT, part.id())
                            .userTypes());
            assertEquals(
                    List.of(StationUserType.MEMBER),
                    restrictionService
                            .audienceOf(RestrictionType.EVENT_VIEW, part.id())
                            .userTypes());
        }
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(course.id()).userTypes());
    }

    @Test
    void deletingACourseMakesItsPartsAppointmentsOfTheirOwnWithTheirSignUps() {
        var course = jugendflamme();
        var saturday = oneTime(30);
        service.addPart(
                station.id(), course.id(), saturday.id(), set(course, "Übung").id());
        var date = LocalDate.now().plusDays(30);
        eventRegistrationRepo.create(saturday.id(), anna.id(), date, RegistrationStatus.ACCEPTED, null);

        service.delete(station.id(), course.id());

        assertTrue(courseRepo.findById(course.id()).isEmpty());
        assertTrue(courseRepo.findPart(saturday.id()).isEmpty());
        assertTrue(eventRepo.findById(saturday.id()).isPresent());
        assertEquals(1, eventRegistrationRepo.countAccepted(saturday.id(), date));
    }

    @Test
    void aCourseOfAnotherStationIsNotHere() {
        var course = jugendflamme();

        assertRefused(EventRefusal.COURSE_NOT_HERE, () -> service.requireCourse(otherStation.id(), course.id()));
        assertRefused(
                EventRefusal.COURSE_NOT_HERE,
                () -> service.update(otherStation.id(), course.id(), details("Fremd", false)));
        assertRefused(EventRefusal.COURSE_NOT_HERE, () -> service.delete(otherStation.id(), course.id()));
    }

    @Test
    void aCourseIsCreatedWithItsAudiencesAndATrimmedName() {
        var course = service.create(
                station.id(),
                details("  Jugendflamme 2  ", false),
                List.of(new EventCourseSetDraft(null, "Teile", 1)),
                youth(),
                youth());

        assertEquals("Jugendflamme 2", course.name());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.registrationAudience(course.id()).userTypes());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(course.id()).userTypes());
    }

    @Test
    void anAudienceNamingSomebodyOfAnotherStationIsRefused() {
        var course = jugendflamme();
        var stranger =
                new RestrictionAudience(List.of(), List.of(), List.of(), List.of(outsider.id()), RestrictionMode.AND);

        assertRefused(
                EventRefusal.COURSE_AUDIENCE_NOT_OF_STATION,
                () -> service.setAudiences(station.id(), course.id(), stranger, RestrictionAudience.empty()));
        assertRefused(
                EventRefusal.COURSE_AUDIENCE_NOT_OF_STATION,
                () -> service.create(
                        station.id(),
                        details("Kurs", false),
                        List.of(new EventCourseSetDraft(null, "Teile", 1)),
                        RestrictionAudience.empty(),
                        stranger));
        assertRefused(
                EventRefusal.COURSE_NOT_HERE,
                () -> service.setAudiences(
                        otherStation.id(), course.id(), RestrictionAudience.empty(), RestrictionAudience.empty()));
    }

    @Test
    void anAppointmentMeantForOtherPeopleThanTheCourseIsRefusedAndOneForTheSamePeopleTaken() {
        var course = jugendflamme();
        int practice = set(course, "Übung").id();
        var forYouth = oneTime(31);
        restrictionService.setAudience(RestrictionType.EVENT_VIEW, forYouth.id(), youth());

        assertRefused(
                EventRefusal.COURSE_PART_AUDIENCE_DIFFERS,
                () -> service.addPart(station.id(), course.id(), forYouth.id(), practice));
        assertFalse(restrictionService
                .audienceOf(RestrictionType.EVENT_VIEW, forYouth.id())
                .namesNobody());

        service.setAudiences(station.id(), course.id(), RestrictionAudience.empty(), youth());
        service.addPart(station.id(), course.id(), forYouth.id(), practice);
        assertEquals(
                List.of(StationUserType.MEMBER),
                restrictionService
                        .audienceOf(RestrictionType.EVENT_VIEW, forYouth.id())
                        .userTypes());
    }

    @Test
    void aCalledOffOrSharedAppointmentIsRefused() {
        var course = jugendflamme();
        int practice = set(course, "Übung").id();
        var calledOff = oneTime(32);
        newEventServices(new DomainEventBus(Set.of()))
                .cancellation()
                .cancelDate(
                        calledOff,
                        calledOff
                                .startTime()
                                .atZone(occurrenceCalendar.zoneOf(station.id()))
                                .toLocalDate(),
                        "Unwetter",
                        null);
        var shared = oneTime(33);
        query("INSERT INTO event_federation_share(event_id) VALUES (:event_id);")
                .single(call().bind("event_id", shared.id()))
                .insert();

        assertRefused(
                EventRefusal.COURSE_PART_CANCELLED,
                () -> service.addPart(station.id(), course.id(), calledOff.id(), practice));
        assertRefused(
                EventRefusal.COURSE_PART_SHARED,
                () -> service.addPart(station.id(), course.id(), shared.id(), practice));
    }

    @Test
    void aRemovedPartKeepsTheSignUpAndAudienceItHadAsAPart() {
        var course = jugendflamme();
        service.setAudiences(station.id(), course.id(), youth(), youth());
        var sunday = oneTime(34);
        service.addPart(
                station.id(), course.id(), sunday.id(), set(course, "Übung").id());

        service.removePart(station.id(), course.id(), sunday.id());

        var stored = eventRepo.findById(sunday.id()).orElseThrow();
        assertTrue(stored.requiresRegistration());
        assertTrue(stored.requiresConfirmation());
        assertEquals(
                List.of(StationUserType.MEMBER),
                restrictionService
                        .audienceOf(RestrictionType.EVENT_VIEW, sunday.id())
                        .userTypes());
    }

    @Test
    void aPartIsMovedAndRemovedOnlyThroughItsOwnCourse() {
        var course = jugendflamme();
        var other = jugendflamme();
        var sunday = oneTime(35);
        int practice = set(course, "Übung").id();
        service.addPart(station.id(), course.id(), sunday.id(), practice);

        assertRefused(
                EventRefusal.COURSE_SET_NOT_IN_COURSE,
                () -> service.movePart(
                        station.id(),
                        course.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
        assertRefused(
                EventRefusal.COURSE_PART_NOT_IN_COURSE,
                () -> service.movePart(
                        station.id(),
                        other.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
        assertRefused(
                EventRefusal.COURSE_PART_NOT_IN_COURSE,
                () -> service.removePart(station.id(), other.id(), sunday.id()));
        assertEquals(
                practice,
                service.movePart(station.id(), course.id(), sunday.id(), practice)
                        .setId());
    }

    @Test
    void editingAPartLeavesItTheCoursesSignUpAndKeepsItOneTime() {
        var course = jugendflamme();
        var saturday = oneTime(36);
        service.addPart(
                station.id(), course.id(), saturday.id(), set(course, "Übung").id());
        var crud = newEventServices(new DomainEventBus(Set.of())).crud();

        crud.update(
                saturday.id(),
                "Samstag",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                saturday.startTime(),
                saturday.endTime(),
                null,
                false,
                null,
                false,
                null,
                null,
                40,
                3,
                2,
                5);

        var stored = eventRepo.findById(saturday.id()).orElseThrow();
        assertEquals("Samstag", stored.name());
        assertTrue(stored.requiresRegistration());
        assertTrue(stored.requiresConfirmation());
        assertEquals(deadline, stored.registrationDeadline());
        assertNull(stored.registrationLimit());
        assertNull(stored.minRegistrations());
        assertRefused(
                EventRefusal.COURSE_PART_STAYS_ONE_TIME,
                () -> crud.update(
                        saturday.id(),
                        "Samstag",
                        null,
                        StationEvent.EventType.RECURRING,
                        6,
                        saturday.startTime(),
                        saturday.endTime(),
                        null,
                        true,
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void listedPartsSayWhichCourseTheyBelongToAndWhereInIt() {
        var course = service.create(
                otherStation.id(),
                details("Leistungsabzeichen", false),
                List.of(new EventCourseSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty());
        int parts = service.findSets(course.id()).getFirst().id();
        var first = appointment(otherStation, 40, StationEvent.EventType.ONE_TIME);
        var second = appointment(otherStation, 41, StationEvent.EventType.ONE_TIME);
        var alone = appointment(otherStation, 42, StationEvent.EventType.ONE_TIME);
        service.addPart(otherStation.id(), course.id(), second.id(), parts);
        service.addPart(otherStation.id(), course.id(), first.id(), parts);
        var occurrences = newEventServices(new DomainEventBus(Set.of()))
                .occurrence()
                .findUpcomingOccurrences(
                        otherStation.id(),
                        null,
                        new EventOccurrenceService.OccurrenceQuery(null, null, false, null, null, null, 10, 0));

        var byEvent =
                occurrences.stream().collect(Collectors.toMap(o -> o.event().id(), o -> o.event()));
        assertEquals(1, byEvent.get(first.id()).course().number());
        assertEquals(2, byEvent.get(second.id()).course().number());
        assertEquals(2, byEvent.get(second.id()).course().count());
        assertEquals("Leistungsabzeichen", byEvent.get(first.id()).course().courseName());
        assertNull(byEvent.get(alone.id()).course());
    }

    @Test
    void aPartCreatedFromTheCourseIsQuietAndTakesTheCoursesSignUp() {
        var course = jugendflamme();
        var bus = mock(DomainEventBus.class);
        var factory = new EventCoursePartFactory(newEventServices(bus).crud(), service);
        var start = LocalDate.now().plusDays(37).atTime(9, 0).toInstant(ZoneOffset.UTC);

        var part = factory.create(
                station.id(), course.id(), set(course, "Übung").id(), "Samstag", null, start, start.plusSeconds(28800));

        var stored = eventRepo.findById(part.eventId()).orElseThrow();
        assertEquals(course.id(), part.courseId());
        assertTrue(stored.requiresRegistration());
        assertTrue(stored.requiresConfirmation());
        verify(bus, never()).publish(any(EventCreated.class));
    }

    @Test
    void onlyCoursesStillOpenWithAPartToComeAwaitAnswers() {
        var open = service.create(
                otherStation.id(),
                new EventCourseDetails("Offen", null, null, Instant.now().plus(Duration.ofDays(3)), false),
                List.of(new EventCourseSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty());
        var empty = service.create(
                otherStation.id(),
                new EventCourseDetails("Leer", null, null, Instant.now().plus(Duration.ofDays(3)), false),
                List.of(new EventCourseSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty());
        var day = appointment(otherStation, 50, StationEvent.EventType.ONE_TIME);
        service.addPart(
                otherStation.id(),
                open.id(),
                day.id(),
                service.findSets(open.id()).getFirst().id());

        var ids = service.findOpenWithDeadline(otherStation.id()).stream()
                .map(EventCourse::id)
                .toList();

        assertTrue(ids.contains(open.id()));
        assertFalse(ids.contains(empty.id()));
    }

    @Test
    void savingFromTheEditorKeepsAllOrNothing() {
        var course = jugendflamme();
        var practice = set(course, "Übung");
        var test = set(course, "Prüfung");
        service.addPart(station.id(), course.id(), oneTime(51).id(), practice.id());

        assertRefused(
                EventRefusal.COURSE_SET_HOLDS_PARTS,
                () -> service.save(
                        station.id(),
                        course.id(),
                        details("Umbenannt", false),
                        List.of(new EventCourseSetDraft(test.id(), "Prüfung", 1)),
                        youth(),
                        youth()));
        assertEquals(
                "Jugendflamme 1", courseRepo.findById(course.id()).orElseThrow().name());
        assertTrue(service.viewAudience(course.id()).namesNobody());

        service.save(
                station.id(),
                course.id(),
                details("Umbenannt", false),
                List.of(
                        new EventCourseSetDraft(practice.id(), "Übung", 2),
                        new EventCourseSetDraft(test.id(), "Prüfung", 1)),
                youth(),
                youth());
        assertEquals("Umbenannt", courseRepo.findById(course.id()).orElseThrow().name());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(course.id()).userTypes());
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }
}
