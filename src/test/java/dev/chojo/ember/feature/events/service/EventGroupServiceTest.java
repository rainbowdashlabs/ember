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
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
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
import java.util.Map;
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
 * Shaping an event group like a Jugendflamme weekend: three practice days and a test, sorted into two
 * sets, with the event group's sign-up and audiences handed down to every part.
 */
class EventGroupServiceTest extends RepositoryTestBase {
    private static Station station;
    private static Station otherStation;
    private static StationMember anna;
    private static StationMember outsider;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    private EventGroupService service;
    private Instant deadline;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event group Station");
        otherStation = stationRepo.create("Other Event group Station");
        var account = accountRepo.create("anna@eventGroup.test", "Anna", "EventGroup");
        accounts.add(account.id());
        anna = stationMemberRepo.create(station.id(), account.id());
        var otherAccount = accountRepo.create("otto@eventGroup.test", "Otto", "EventGroup");
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
        service = new EventGroupService(
                eventGroupRepo,
                eventRepo,
                restrictionService,
                occurrenceCalendar,
                new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility),
                new EventFieldRepository());
        deadline = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);
    }

    private EventGroupDetails details(String name, boolean confirm) {
        return new EventGroupDetails(name, "Lehrgang", 12, deadline, confirm);
    }

    private EventGroup jugendflamme() {
        return service.create(
                station.id(),
                details("Jugendflamme 1", true),
                List.of(new EventGroupSetDraft(null, "Übung", 2), new EventGroupSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
    }

    private EventGroup create(EventGroupDetails details, List<EventGroupSetDraft> sets) {
        return service.create(
                station.id(), details, sets, RestrictionAudience.empty(), RestrictionAudience.empty(), null);
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

    private EventGroupSet set(EventGroup eventGroup, String name) {
        return service.findSets(eventGroup.id()).stream()
                .filter(set -> set.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static RestrictionAudience youth() {
        return new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);
    }

    @Test
    void anEventGroupIsCreatedWithItsSetsInOrder() {
        var eventGroup = jugendflamme();

        var sets = service.findSets(eventGroup.id());
        assertEquals(
                List.of("Übung", "Prüfung"),
                sets.stream().map(EventGroupSet::name).toList());
        assertEquals(List.of(2, 1), sets.stream().map(EventGroupSet::minimum).toList());
        assertEquals(12, eventGroup.registrationLimit());
        assertEquals(deadline, eventGroup.registrationDeadline());
        assertTrue(eventGroup.requiresConfirmation());
    }

    @Test
    void anEventGroupNeedsANameAndASetAndNoSetAsksForLessThanNothing() {
        var sets = List.of(new EventGroupSetDraft(null, "Teile", 1));
        assertRefused(EventRefusal.EVENT_GROUP_NAME_MISSING, () -> create(details(" ", false), sets));
        assertRefused(EventRefusal.EVENT_GROUP_NEEDS_A_SET, () -> create(details("Kurs", false), List.of()));
        assertRefused(
                EventRefusal.EVENT_GROUP_NAME_MISSING,
                () -> create(details("Kurs", false), List.of(new EventGroupSetDraft(null, "", 1))));
        assertRefused(
                EventRefusal.EVENT_GROUP_SET_MINIMUM_NEGATIVE,
                () -> create(details("Kurs", false), List.of(new EventGroupSetDraft(null, "Teile", -1))));
        assertRefused(
                EventRefusal.EVENT_GROUP_LIMIT_NOT_POSITIVE,
                () -> create(new EventGroupDetails("Kurs", null, 0, null, false), sets));
        assertRefused(
                EventRefusal.EVENT_GROUP_NEEDS_A_SET,
                () -> service.replaceSets(station.id(), jugendflamme().id(), List.of()));
    }

    @Test
    void aPartTakesTheEventGroupsSignUpAndCarriesNoLimitOfItsOwn() {
        var eventGroup = jugendflamme();
        var saturday = oneTime(20);

        var part = service.addPart(
                station.id(),
                eventGroup.id(),
                saturday.id(),
                set(eventGroup, "Übung").id());

        assertEquals(eventGroup.id(), part.eventGroupId());
        var stored = eventRepo.findById(saturday.id()).orElseThrow();
        assertTrue(stored.requiresRegistration());
        assertTrue(stored.requiresConfirmation());
        assertEquals(deadline, stored.registrationDeadline());
        assertNull(stored.registrationLimit());
        assertNull(stored.registrationCloseDays());
    }

    @Test
    void changingTheEventGroupChangesTheSignUpOfEveryPart() {
        var eventGroup = jugendflamme();
        var saturday = oneTime(21);
        service.addPart(
                station.id(),
                eventGroup.id(),
                saturday.id(),
                set(eventGroup, "Übung").id());

        service.update(station.id(), eventGroup.id(), new EventGroupDetails("Jugendflamme 1", null, null, null, false));

        var stored = eventRepo.findById(saturday.id()).orElseThrow();
        assertFalse(stored.requiresConfirmation());
        assertNull(stored.registrationDeadline());
    }

    @Test
    void onlyAOneTimeAppointmentOfTheStationWithoutSignUpsOfItsOwnBecomesAPart() {
        var eventGroup = jugendflamme();
        int practice = set(eventGroup, "Übung").id();
        var series = appointment(station, 22, StationEvent.EventType.RECURRING);
        var elsewhere = appointment(otherStation, 22, StationEvent.EventType.ONE_TIME);
        var signedUp = oneTime(23);
        eventRegistrationRepo.create(
                signedUp.id(), anna.id(), LocalDate.now().plusDays(23), RegistrationStatus.ACCEPTED, null);

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_NOT_ONE_TIME,
                () -> service.addPart(station.id(), eventGroup.id(), series.id(), practice));
        assertRefused(
                EventRefusal.EVENT_GROUP_PART_NOT_HERE,
                () -> service.addPart(station.id(), eventGroup.id(), elsewhere.id(), practice));
        assertRefused(
                EventRefusal.EVENT_GROUP_PART_HAS_SIGN_UPS,
                () -> service.addPart(station.id(), eventGroup.id(), signedUp.id(), practice));
    }

    @Test
    void anAppointmentIsAPartOfOneEventGroupAndOnlyInItsSets() {
        var eventGroup = jugendflamme();
        var other = jugendflamme();
        var sunday = oneTime(24);

        assertRefused(
                EventRefusal.EVENT_GROUP_SET_NOT_IN_EVENT_GROUP,
                () -> service.addPart(
                        station.id(),
                        eventGroup.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
        service.addPart(
                station.id(),
                eventGroup.id(),
                sunday.id(),
                set(eventGroup, "Übung").id());
        assertRefused(
                EventRefusal.EVENT_GROUP_PART_TAKEN,
                () -> service.addPart(
                        station.id(),
                        other.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
    }

    @Test
    void aPartMovesBetweenTheSetsOfItsEventGroup() {
        var eventGroup = jugendflamme();
        var sunday = oneTime(25);
        service.addPart(
                station.id(),
                eventGroup.id(),
                sunday.id(),
                set(eventGroup, "Übung").id());

        var moved = service.movePart(
                station.id(),
                eventGroup.id(),
                sunday.id(),
                set(eventGroup, "Prüfung").id());

        assertEquals(set(eventGroup, "Prüfung").id(), moved.setId());
    }

    @Test
    void aRemovedPartKeepsWhoWasSignedUpForIt() {
        var eventGroup = jugendflamme();
        var sunday = oneTime(26);
        service.addPart(
                station.id(),
                eventGroup.id(),
                sunday.id(),
                set(eventGroup, "Prüfung").id());
        var date = LocalDate.now().plusDays(26);
        eventRegistrationRepo.create(sunday.id(), anna.id(), date, RegistrationStatus.ACCEPTED, null);

        service.removePart(station.id(), eventGroup.id(), sunday.id());

        assertTrue(service.findParts(eventGroup.id()).isEmpty());
        assertEquals(1, eventRegistrationRepo.countAccepted(sunday.id(), date));
    }

    @Test
    void setsAreRenamedReorderedAddedAndEmptyOnesRemoved() {
        var eventGroup = jugendflamme();
        var practice = set(eventGroup, "Übung");
        var test = set(eventGroup, "Prüfung");

        var sets = service.replaceSets(
                station.id(),
                eventGroup.id(),
                List.of(
                        new EventGroupSetDraft(test.id(), "Abschlussprüfung", 1),
                        new EventGroupSetDraft(null, "Zusatz", 0)));

        assertEquals(
                List.of("Abschlussprüfung", "Zusatz"),
                sets.stream().map(EventGroupSet::name).toList());
        assertEquals(test.id(), sets.getFirst().id());
        assertTrue(sets.stream().noneMatch(set -> set.id() == practice.id()));
    }

    @Test
    void aSetHoldingPartsOrOneOfAnotherEventGroupIsRefused() {
        var eventGroup = jugendflamme();
        var other = jugendflamme();
        var practice = set(eventGroup, "Übung");
        var test = set(eventGroup, "Prüfung");
        service.addPart(station.id(), eventGroup.id(), oneTime(27).id(), practice.id());

        assertRefused(
                EventRefusal.EVENT_GROUP_SET_HOLDS_PARTS,
                () -> service.replaceSets(
                        station.id(), eventGroup.id(), List.of(new EventGroupSetDraft(test.id(), "Prüfung", 1))));
        assertRefused(
                EventRefusal.EVENT_GROUP_SET_NOT_IN_EVENT_GROUP,
                () -> service.replaceSets(
                        station.id(),
                        eventGroup.id(),
                        List.of(new EventGroupSetDraft(set(other, "Übung").id(), "Fremd", 1))));
        assertRefused(
                EventRefusal.EVENT_GROUP_SET_NOT_IN_EVENT_GROUP,
                () -> service.replaceSets(
                        station.id(),
                        eventGroup.id(),
                        List.of(
                                new EventGroupSetDraft(practice.id(), "Übung", 2),
                                new EventGroupSetDraft(practice.id(), "Übung", 2))));
    }

    @Test
    void theEventGroupsAudiencesReachEveryPartAndEveryPartAddedLater() {
        var eventGroup = jugendflamme();
        var first = oneTime(28);
        service.addPart(
                station.id(),
                eventGroup.id(),
                first.id(),
                set(eventGroup, "Übung").id());

        service.setAudiences(station.id(), eventGroup.id(), youth(), youth());
        var later = oneTime(29);
        service.addPart(
                station.id(),
                eventGroup.id(),
                later.id(),
                set(eventGroup, "Übung").id());

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
                service.viewAudience(eventGroup.id()).userTypes());
    }

    @Test
    void deletingAnEventGroupMakesItsPartsAppointmentsOfTheirOwnWithTheirSignUps() {
        var eventGroup = jugendflamme();
        var saturday = oneTime(30);
        service.addPart(
                station.id(),
                eventGroup.id(),
                saturday.id(),
                set(eventGroup, "Übung").id());
        var date = LocalDate.now().plusDays(30);
        eventRegistrationRepo.create(saturday.id(), anna.id(), date, RegistrationStatus.ACCEPTED, null);

        service.delete(station.id(), eventGroup.id());

        assertTrue(eventGroupRepo.findById(eventGroup.id()).isEmpty());
        assertTrue(eventGroupRepo.findPart(saturday.id()).isEmpty());
        assertTrue(eventRepo.findById(saturday.id()).isPresent());
        assertEquals(1, eventRegistrationRepo.countAccepted(saturday.id(), date));
    }

    @Test
    void anEventGroupOfAnotherStationIsNotHere() {
        var eventGroup = jugendflamme();

        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE, () -> service.requireEventGroup(otherStation.id(), eventGroup.id()));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> service.update(otherStation.id(), eventGroup.id(), details("Fremd", false)));
        assertRefused(EventRefusal.EVENT_GROUP_NOT_HERE, () -> service.delete(otherStation.id(), eventGroup.id()));
    }

    @Test
    void anEventGroupIsCreatedWithItsAudiencesAndATrimmedName() {
        var eventGroup = service.create(
                station.id(),
                details("  Jugendflamme 2  ", false),
                List.of(new EventGroupSetDraft(null, "Teile", 1)),
                youth(),
                youth(),
                null);

        assertEquals("Jugendflamme 2", eventGroup.name());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.registrationAudience(eventGroup.id()).userTypes());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(eventGroup.id()).userTypes());
    }

    @Test
    void anAudienceNamingSomebodyOfAnotherStationIsRefused() {
        var eventGroup = jugendflamme();
        var stranger =
                new RestrictionAudience(List.of(), List.of(), List.of(), List.of(outsider.id()), RestrictionMode.AND);

        assertRefused(
                EventRefusal.EVENT_GROUP_AUDIENCE_NOT_OF_STATION,
                () -> service.setAudiences(station.id(), eventGroup.id(), stranger, RestrictionAudience.empty()));
        assertRefused(
                EventRefusal.EVENT_GROUP_AUDIENCE_NOT_OF_STATION,
                () -> service.create(
                        station.id(),
                        details("Kurs", false),
                        List.of(new EventGroupSetDraft(null, "Teile", 1)),
                        RestrictionAudience.empty(),
                        stranger,
                        null));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> service.setAudiences(
                        otherStation.id(), eventGroup.id(), RestrictionAudience.empty(), RestrictionAudience.empty()));
    }

    @Test
    void anAppointmentMeantForOtherPeopleThanTheEventGroupIsRefusedAndOneForTheSamePeopleTaken() {
        var eventGroup = jugendflamme();
        int practice = set(eventGroup, "Übung").id();
        var forYouth = oneTime(31);
        restrictionService.setAudience(RestrictionType.EVENT_VIEW, forYouth.id(), youth());

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_AUDIENCE_DIFFERS,
                () -> service.addPart(station.id(), eventGroup.id(), forYouth.id(), practice));
        assertFalse(restrictionService
                .audienceOf(RestrictionType.EVENT_VIEW, forYouth.id())
                .namesNobody());

        service.setAudiences(station.id(), eventGroup.id(), RestrictionAudience.empty(), youth());
        service.addPart(station.id(), eventGroup.id(), forYouth.id(), practice);
        assertEquals(
                List.of(StationUserType.MEMBER),
                restrictionService
                        .audienceOf(RestrictionType.EVENT_VIEW, forYouth.id())
                        .userTypes());
    }

    @Test
    void aCalledOffOrSharedAppointmentIsRefused() {
        var eventGroup = jugendflamme();
        int practice = set(eventGroup, "Übung").id();
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
                EventRefusal.EVENT_GROUP_PART_CANCELLED,
                () -> service.addPart(station.id(), eventGroup.id(), calledOff.id(), practice));
        assertRefused(
                EventRefusal.EVENT_GROUP_PART_SHARED,
                () -> service.addPart(station.id(), eventGroup.id(), shared.id(), practice));
    }

    @Test
    void aRemovedPartKeepsTheSignUpAndAudienceItHadAsAPart() {
        var eventGroup = jugendflamme();
        service.setAudiences(station.id(), eventGroup.id(), youth(), youth());
        var sunday = oneTime(34);
        service.addPart(
                station.id(),
                eventGroup.id(),
                sunday.id(),
                set(eventGroup, "Übung").id());

        service.removePart(station.id(), eventGroup.id(), sunday.id());

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
    void aPartIsMovedAndRemovedOnlyThroughItsOwnEventGroup() {
        var eventGroup = jugendflamme();
        var other = jugendflamme();
        var sunday = oneTime(35);
        int practice = set(eventGroup, "Übung").id();
        service.addPart(station.id(), eventGroup.id(), sunday.id(), practice);

        assertRefused(
                EventRefusal.EVENT_GROUP_SET_NOT_IN_EVENT_GROUP,
                () -> service.movePart(
                        station.id(),
                        eventGroup.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
        assertRefused(
                EventRefusal.EVENT_GROUP_PART_NOT_IN_EVENT_GROUP,
                () -> service.movePart(
                        station.id(),
                        other.id(),
                        sunday.id(),
                        set(other, "Übung").id()));
        assertRefused(
                EventRefusal.EVENT_GROUP_PART_NOT_IN_EVENT_GROUP,
                () -> service.removePart(station.id(), other.id(), sunday.id()));
        assertEquals(
                practice,
                service.movePart(station.id(), eventGroup.id(), sunday.id(), practice)
                        .setId());
    }

    @Test
    void editingAPartLeavesItTheEventGroupsSignUpAndKeepsItOneTime() {
        var eventGroup = jugendflamme();
        var saturday = oneTime(36);
        service.addPart(
                station.id(),
                eventGroup.id(),
                saturday.id(),
                set(eventGroup, "Übung").id());
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
                EventRefusal.EVENT_GROUP_PART_STAYS_ONE_TIME,
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
    void listedPartsSayWhichEventGroupTheyBelongToAndWhereInIt() {
        var eventGroup = service.create(
                otherStation.id(),
                details("Leistungsabzeichen", false),
                List.of(new EventGroupSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        int parts = service.findSets(eventGroup.id()).getFirst().id();
        var first = appointment(otherStation, 40, StationEvent.EventType.ONE_TIME);
        var second = appointment(otherStation, 41, StationEvent.EventType.ONE_TIME);
        var alone = appointment(otherStation, 42, StationEvent.EventType.ONE_TIME);
        service.addPart(otherStation.id(), eventGroup.id(), second.id(), parts);
        service.addPart(otherStation.id(), eventGroup.id(), first.id(), parts);
        var occurrences = newEventServices(new DomainEventBus(Set.of()))
                .occurrence()
                .findUpcomingOccurrences(
                        otherStation.id(),
                        null,
                        new EventOccurrenceService.OccurrenceQuery(null, null, false, null, null, null, 10, 0));

        var byEvent =
                occurrences.stream().collect(Collectors.toMap(o -> o.event().id(), o -> o.event()));
        assertEquals(1, byEvent.get(first.id()).eventGroup().number());
        assertEquals(2, byEvent.get(second.id()).eventGroup().number());
        assertEquals(2, byEvent.get(second.id()).eventGroup().count());
        assertEquals("Leistungsabzeichen", byEvent.get(first.id()).eventGroup().eventGroupName());
        assertNull(byEvent.get(alone.id()).eventGroup());
    }

    @Test
    void aPartCreatedFromTheEventGroupIsQuietAndTakesTheEventGroupsSignUp() {
        var eventGroup = jugendflamme();
        var bus = mock(DomainEventBus.class);
        var factory = new EventGroupPartFactory(newEventServices(bus).crud(), service);
        var start = LocalDate.now().plusDays(37).atTime(9, 0).toInstant(ZoneOffset.UTC);

        var part = factory.create(
                station.id(),
                eventGroup.id(),
                set(eventGroup, "Übung").id(),
                "Samstag",
                null,
                start,
                start.plusSeconds(28800),
                null);

        var stored = eventRepo.findById(part.eventId()).orElseThrow();
        assertEquals(eventGroup.id(), part.eventGroupId());
        assertTrue(stored.requiresRegistration());
        assertTrue(stored.requiresConfirmation());
        verify(bus, never()).publish(any(EventCreated.class));
    }

    @Test
    void onlyEventGroupsStillOpenWithAPartToComeAwaitAnswers() {
        var open = service.create(
                otherStation.id(),
                new EventGroupDetails("Offen", null, null, Instant.now().plus(Duration.ofDays(3)), false),
                List.of(new EventGroupSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var empty = service.create(
                otherStation.id(),
                new EventGroupDetails("Leer", null, null, Instant.now().plus(Duration.ofDays(3)), false),
                List.of(new EventGroupSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var day = appointment(otherStation, 50, StationEvent.EventType.ONE_TIME);
        service.addPart(
                otherStation.id(),
                open.id(),
                day.id(),
                service.findSets(open.id()).getFirst().id());

        var ids = service.findOpenWithDeadline(otherStation.id()).stream()
                .map(EventGroup::id)
                .toList();

        assertTrue(ids.contains(open.id()));
        assertFalse(ids.contains(empty.id()));
    }

    @Test
    void savingFromTheEditorKeepsAllOrNothing() {
        var eventGroup = jugendflamme();
        var practice = set(eventGroup, "Übung");
        var test = set(eventGroup, "Prüfung");
        service.addPart(station.id(), eventGroup.id(), oneTime(51).id(), practice.id());

        assertRefused(
                EventRefusal.EVENT_GROUP_SET_HOLDS_PARTS,
                () -> service.save(
                        station.id(),
                        eventGroup.id(),
                        details("Umbenannt", false),
                        List.of(new EventGroupSetDraft(test.id(), "Prüfung", 1)),
                        youth(),
                        youth(),
                        null));
        assertEquals(
                "Jugendflamme 1",
                eventGroupRepo.findById(eventGroup.id()).orElseThrow().name());
        assertTrue(service.viewAudience(eventGroup.id()).namesNobody());

        service.save(
                station.id(),
                eventGroup.id(),
                details("Umbenannt", false),
                List.of(
                        new EventGroupSetDraft(practice.id(), "Übung", 2),
                        new EventGroupSetDraft(test.id(), "Prüfung", 1)),
                youth(),
                youth(),
                null);
        assertEquals(
                "Umbenannt",
                eventGroupRepo.findById(eventGroup.id()).orElseThrow().name());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(eventGroup.id()).userTypes());
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }

    private static EventFieldDraft valueField(String name, FieldType type, String value, boolean overview) {
        return new EventFieldDraft(null, name, type, EventQuestionSettings.empty(), value, overview, null, true);
    }

    @Test
    void anAppointmentShowsTheGroupFieldsAndItsOwnFieldOfTheSameNameReplacesOne() {
        var group = service.create(
                station.id(),
                details("Zeltlager", false),
                List.of(new EventGroupSetDraft(null, "Tage", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                new EventGroupContent(
                        null,
                        List.of(
                                valueField("Ort", FieldType.LOCATION, "Gerätehaus", true),
                                valueField("Mitbringen", FieldType.TEXT, "Schlafsack", false))));
        var part = appointment(station, 30, StationEvent.EventType.ONE_TIME);
        service.addPart(
                station.id(),
                group.id(),
                part.id(),
                service.findSets(group.id()).getFirst().id());
        var fields = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);

        assertEquals(
                List.of("Ort", "Mitbringen"),
                fields.findByEvent(part.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
        assertTrue(fields.findOwnByEvent(part.id()).isEmpty());
        assertTrue(fields.findByEvent(part.id()).stream().allMatch(field -> field.id() < 0));

        fields.replaceFields(part.id(), List.of(valueField("ort", FieldType.LOCATION, "Zeltplatz", false)));

        var shown = fields.findByEvent(part.id());
        assertEquals(
                List.of("Mitbringen", "ort"),
                shown.stream().map(AppointmentField::name).toList());
        assertEquals("Zeltplatz", shown.get(1).value());
        assertEquals(
                Map.of(),
                fields.findOverviewFieldsByEvents(List.of(part.id())).entrySet().stream()
                        .filter(entry -> !entry.getValue().isEmpty())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Test
    void anEventGroupTakesOnlyPlainValueFields() {
        var member = new EventFieldDraft(
                null, "Leitung", FieldType.MEMBER, EventQuestionSettings.empty(), "", false, null, false);

        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED,
                () -> service.create(
                        station.id(),
                        details("Wettbewerb", false),
                        List.of(new EventGroupSetDraft(null, "Tage", 1)),
                        RestrictionAudience.empty(),
                        RestrictionAudience.empty(),
                        new EventGroupContent(null, List.of(member))));
    }

    private EventGroup groupWith(String name, EventFieldDraft... fields) {
        return service.create(
                station.id(),
                details(name, false),
                List.of(new EventGroupSetDraft(null, "Tage", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                new EventGroupContent(null, List.of(fields)));
    }

    private StationEvent partOf(EventGroup group, int daysAhead) {
        var part = appointment(station, daysAhead, StationEvent.EventType.ONE_TIME);
        service.addPart(
                station.id(),
                group.id(),
                part.id(),
                service.findSets(group.id()).getFirst().id());
        return part;
    }

    private EventFieldService fieldService() {
        return new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);
    }

    @Test
    void listsAndDatedReadsShowTheGroupOverviewFields() {
        var group = groupWith("Wettbewerb", valueField("Ort", FieldType.LOCATION, "Sportplatz", true));
        var part = partOf(group, 31);
        var day = eventRepo
                .findById(part.id())
                .orElseThrow()
                .startTime()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        var fields = fieldService();

        assertEquals(
                List.of("Ort"),
                fields.findOverviewFieldsByEvents(List.of(part.id())).get(part.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
        assertEquals(
                List.of("Ort"),
                fields.findOverviewFieldsByEvents(List.of(part.id()), Map.of(part.id(), day)).get(part.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
        assertEquals("Sportplatz", AppointmentField.firstLocation(eventFieldRepo.findShownOn(part.id(), day)));
    }

    @Test
    void savingWithoutFieldsKeepsThemAndSavingWithFieldsReplacesThem() {
        var group = groupWith("Ausbildung", valueField("Ort", FieldType.LOCATION, "Gerätehaus", true));
        var unchanged = details("Ausbildung", false);

        service.save(station.id(), group.id(), unchanged, null, null, null, null);
        assertEquals(
                List.of("Ort"),
                service.findFields(group.id()).stream()
                        .map(AppointmentField::name)
                        .toList());

        service.save(
                station.id(),
                group.id(),
                unchanged,
                null,
                null,
                null,
                new EventGroupContent(null, List.of(valueField("Mitbringen", FieldType.TEXT, "Helm", false))));
        assertEquals(
                List.of("Mitbringen"),
                service.findFields(group.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
    }

    @Test
    void aGroupFieldIsCheckedLikeAnAppointmentField() {
        var choice = new EventFieldDraft(
                null,
                "Kleidung",
                FieldType.CHOICE,
                new EventQuestionSettings(
                        List.of("Uniform", "Zivil"),
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        false),
                "Badehose",
                false,
                null,
                false);
        var tied = new EventFieldDraft(null, "Ort", FieldType.TEXT, EventQuestionSettings.empty(), "", false, 4, false);

        assertRefused(EventRefusal.APPOINTMENT_FIELD_VALUE_NOT_ACCEPTED, () -> groupWith("Wahl", choice));
        assertRefused(EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED, () -> groupWith("Bindung", tied));
        var perDate = new EventFieldDraft(
                null,
                "Thema",
                FieldType.TEXT,
                new EventQuestionSettings(null, null, null, null, null, false, true, false, null, null, null, false),
                "",
                false,
                null,
                false);
        assertRefused(EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED, () -> groupWith("Datum", perDate));
    }

    @Test
    void aPartLeavingTheGroupNoLongerShowsItsFields() {
        var group = groupWith("Lager", valueField("Ort", FieldType.LOCATION, "Zeltplatz", true));
        var part = partOf(group, 32);
        var fields = fieldService();
        assertEquals(1, fields.findByEvent(part.id()).size());

        service.removePart(station.id(), group.id(), part.id());

        assertTrue(fields.findByEvent(part.id()).isEmpty());
    }

    @Test
    void aGroupStartedFromAnAppointmentTakesItInOrIsNotCreatedAtAll() {
        var forYouth = appointment(station, 33, StationEvent.EventType.ONE_TIME);
        restrictionService.setAudience(RestrictionType.EVENT_VIEW, forYouth.id(), youth());
        var before = eventGroupRepo.findByStation(station.id()).size();
        var sets = List.of(new EventGroupSetDraft(null, "Tage", 1));

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_AUDIENCE_DIFFERS,
                () -> service.createWith(
                        station.id(),
                        details("Wettkampf", false),
                        sets,
                        RestrictionAudience.empty(),
                        RestrictionAudience.empty(),
                        null,
                        forYouth.id()));
        assertEquals(before, eventGroupRepo.findByStation(station.id()).size());

        var created = service.createWith(
                station.id(),
                details("Wettkampf", false),
                sets,
                RestrictionAudience.empty(),
                youth(),
                null,
                forYouth.id());
        assertEquals(
                List.of(forYouth.id()),
                service.findParts(created.id()).stream()
                        .map(EventGroupPart::eventId)
                        .toList());
    }
}
