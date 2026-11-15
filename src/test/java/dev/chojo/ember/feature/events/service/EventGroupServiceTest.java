/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventGroupTally;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.repository.EventGroupFixtures.part;
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
 * An event group with its sets and parts: creating and changing it, the sets and their minimums, which
 * appointments become parts, moving, requiring and removing them, and deleting the event group.
 */
class EventGroupServiceTest extends EventGroupServiceTestBase {
    @Test
    void anEventGroupIsCreatedWithItsSetsInOrder() {
        var eventGroup = jugendflamme();

        var sets = service.findSets(eventGroup.id());
        assertEquals(
                List.of("Übung", "Prüfung"),
                sets.stream().map(EventGroupSet::name).toList());
        assertEquals(List.of(2, 1), sets.stream().map(EventGroupSet::minimum).toList());
        assertEquals(12, eventGroup.details().registrationLimit());
        assertEquals(deadline, eventGroup.details().registrationDeadline());
        assertTrue(eventGroup.details().requiresConfirmation());
    }

    @Test
    void anEventGroupNeedsANameAndASetAndNoSetAsksForLessThanNothing() {
        var sets = List.of(new EventGroupSetDraft(null, "Teile", 1));
        assertRefused(EventRefusal.EVENT_GROUP_NAME_MISSING, () -> create(details(" ", false), sets));
        assertRefused(EventRefusal.EVENT_GROUP_NEEDS_A_SET, () -> create(details("Kurs", false), List.of()));
        assertRefused(
                EventRefusal.EVENT_GROUP_NAME_MISSING,
                () -> create(details("Kurs", false), List.of(new EventGroupSetDraft(null, "", 1))));
        var negative = assertThrows(
                RefusalResponse.class,
                () -> create(details("Kurs", false), List.of(new EventGroupSetDraft(null, "Teile", -1))));
        assertEquals(EventRefusal.EVENT_GROUP_SET_MINIMUM_OUT_OF_RANGE, negative.refusal());
        assertEquals(RefusalDetail.range(0, null), negative.detail(), "a new set holds no parts yet");
        assertRefused(
                EventRefusal.EVENT_GROUP_LIMIT_NOT_POSITIVE,
                () -> create(new EventGroupDetails("Kurs", null, 0, null, false, null), sets));
        assertRefused(
                EventRefusal.EVENT_GROUP_NEEDS_A_SET,
                () -> service.replaceSets(station.id(), jugendflamme().id(), List.of()));
    }

    @Test
    void anEventGroupKeepsAColourWrittenAsHexAndHandsItToItsParts() {
        var sets = List.of(new EventGroupSetDraft(null, "Teile", 1));
        for (var invalid : List.of("red", "#abc", "#3694ffaa", "3694ff")) {
            assertRefused(
                    EventRefusal.EVENT_GROUP_COLOR_INVALID,
                    () -> create(new EventGroupDetails("Kurs", null, null, null, false, invalid), sets));
        }
        assertNull(create(new EventGroupDetails("Kurs", null, null, null, false, " "), sets)
                .details()
                .color());

        var eventGroup = create(new EventGroupDetails("Kurs", null, null, null, false, "#3694FF"), sets);
        var saturday = oneTime(20);
        service.addPart(
                station.id(),
                eventGroup.id(),
                saturday.id(),
                set(eventGroup, "Teile").id());

        assertEquals("#3694ff", eventGroup.details().color());
        assertEquals("#3694ff", service.partSummary(station.id(), saturday.id()).color());

        assertRefused(
                EventRefusal.EVENT_GROUP_COLOR_INVALID,
                () -> service.update(
                        station.id(), eventGroup.id(), new EventGroupDetails("Kurs", null, null, null, false, "#abc")));
        assertEquals("#3694ff", service.partSummary(station.id(), saturday.id()).color());

        service.update(station.id(), eventGroup.id(), new EventGroupDetails("Kurs", null, null, null, false, null));
        assertNull(service.partSummary(station.id(), saturday.id()).color());
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

        service.update(
                station.id(), eventGroup.id(), new EventGroupDetails("Jugendflamme 1", null, null, null, false, null));

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
    void aChangedMinimumAsksForNoMorePartsThanTheSetHolds() {
        var eventGroup = jugendflamme();
        var practice = set(eventGroup, "Übung");
        var test = set(eventGroup, "Prüfung");
        service.addPart(station.id(), eventGroup.id(), oneTime(40).id(), practice.id());

        var abovePart = assertThrows(
                RefusalResponse.class,
                () -> service.replaceSets(
                        station.id(),
                        eventGroup.id(),
                        List.of(
                                new EventGroupSetDraft(practice.id(), "Übung", 3),
                                new EventGroupSetDraft(test.id(), "Prüfung", 1))));
        assertEquals(EventRefusal.EVENT_GROUP_SET_MINIMUM_OUT_OF_RANGE, abovePart.refusal());
        assertEquals(RefusalDetail.range(0, 1L), abovePart.detail(), "the set holds one part and requires none");

        var filling = service.replaceSets(
                station.id(),
                eventGroup.id(),
                List.of(
                        new EventGroupSetDraft(practice.id(), "Übung", 2),
                        new EventGroupSetDraft(test.id(), "Prüfung", 1)));
        assertEquals(2, filling.getFirst().minimum());

        var sets = service.replaceSets(
                station.id(),
                eventGroup.id(),
                List.of(
                        new EventGroupSetDraft(practice.id(), "Übung", 1),
                        new EventGroupSetDraft(test.id(), "Prüfung", 5)));
        assertEquals(List.of(1, 5), sets.stream().map(EventGroupSet::minimum).toList());
    }

    @Test
    void aSetThatAskedForAllItsPartsAsksForAllItStillHolds() {
        var eventGroup = jugendflamme();
        var practice = set(eventGroup, "Übung");
        var test = set(eventGroup, "Prüfung");
        var saturday = oneTime(41);
        var sunday = oneTime(42);
        var monday = oneTime(43);
        service.addPart(station.id(), eventGroup.id(), saturday.id(), practice.id());
        service.addPart(station.id(), eventGroup.id(), sunday.id(), practice.id());
        service.addPart(station.id(), eventGroup.id(), monday.id(), practice.id());

        service.removePart(station.id(), eventGroup.id(), monday.id());
        assertEquals(2, set(eventGroup, "Übung").minimum());

        service.movePart(station.id(), eventGroup.id(), sunday.id(), test.id());
        assertEquals(1, set(eventGroup, "Übung").minimum());

        newEventServices(new DomainEventBus(Set.of())).crud().delete(saturday.id());
        assertEquals(1, set(eventGroup, "Übung").minimum());
        assertEquals(1, set(eventGroup, "Prüfung").minimum());
    }

    @Test
    void aSetStillFillingUpKeepsItsMinimumWhenItLosesOrKeepsAPart() {
        var eventGroup = jugendflamme();
        var practice = set(eventGroup, "Übung");
        service.replaceSets(
                station.id(),
                eventGroup.id(),
                List.of(
                        new EventGroupSetDraft(practice.id(), "Übung", 3),
                        new EventGroupSetDraft(set(eventGroup, "Prüfung").id(), "Prüfung", 1)));
        var saturday = oneTime(44);
        var sunday = oneTime(45);
        service.addPart(station.id(), eventGroup.id(), saturday.id(), practice.id());
        service.addPart(station.id(), eventGroup.id(), sunday.id(), practice.id());

        service.movePart(station.id(), eventGroup.id(), sunday.id(), practice.id());
        assertEquals(3, set(eventGroup, "Übung").minimum());

        service.removePart(station.id(), eventGroup.id(), sunday.id());
        assertEquals(3, set(eventGroup, "Übung").minimum());
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
    void aNewPartNeedsANameAStartAndAnEnd() {
        var eventGroup = jugendflamme();
        var factory = new EventGroupPartFactory(
                newEventServices(mock(DomainEventBus.class)).crud(), service);
        int setId = set(eventGroup, "Übung").id();
        var start = LocalDate.now().plusDays(37).atTime(9, 0).toInstant(ZoneOffset.UTC);

        assertRefused(
                EventRefusal.EVENT_NEEDS_A_NAME,
                () -> factory.create(
                        station.id(), eventGroup.id(), setId, " ", null, start, start.plusSeconds(3600), null));
        assertRefused(
                EventRefusal.EVENT_GROUP_PART_NEEDS_TIMES,
                () -> factory.create(station.id(), eventGroup.id(), setId, "Samstag", null, start, null, null));
        var part = factory.create(
                station.id(), eventGroup.id(), setId, "  Samstag ", null, start, start.plusSeconds(3600), null);
        assertEquals("Samstag", eventRepo.findById(part.eventId()).orElseThrow().name());
    }

    @Test
    void onlyEventGroupsStillOpenWithAPartToComeAwaitAnswers() {
        var open = service.create(
                otherStation.id(),
                new EventGroupDetails("Offen", null, null, Instant.now().plus(Duration.ofDays(3)), false, null),
                List.of(new EventGroupSetDraft(null, "Teile", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var empty = service.create(
                otherStation.id(),
                new EventGroupDetails("Leer", null, null, Instant.now().plus(Duration.ofDays(3)), false, null),
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
    void theTallyCountsPartsAndTheSignUpsHoldingAPlace() {
        var eventGroup = jugendflamme();
        var empty = create(details("Leer", false), List.of(new EventGroupSetDraft(null, "Teile", 1)));
        service.addPart(
                station.id(),
                eventGroup.id(),
                oneTime(61).id(),
                set(eventGroup, "Übung").id());
        service.addPart(
                station.id(),
                eventGroup.id(),
                oneTime(62).id(),
                set(eventGroup, "Prüfung").id());
        var registrations = new EventGroupRegistrationRepository();
        registrations.signUp(eventGroup.id(), anna.id(), RegistrationStatus.PENDING, null);

        var tallies = service.tallies(station.id());

        assertEquals(new EventGroupTally(2, 1), tallies.get(eventGroup.id()));
        assertEquals(EventGroupTally.NONE, tallies.get(empty.id()));
        assertEquals(new EventGroupTally(2, 1), service.tally(eventGroup.id()));
        assertEquals(EventGroupTally.NONE, service.tally(-1));
        assertEquals(
                Set.of(anna.id()),
                registrations.findAnsweredBy(List.of(anna.id())).get(eventGroup.id()));
        assertEquals(Map.of(), registrations.findAnsweredBy(List.of()));
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
                eventGroupRepo.findById(eventGroup.id()).orElseThrow().details().name());
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
                eventGroupRepo.findById(eventGroup.id()).orElseThrow().details().name());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.viewAudience(eventGroup.id()).userTypes());
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

    @Test
    void markingAPartRequiredRaisesItsSetsMinimumAndUnmarkingLeavesIt() {
        var eventGroup =
                create(details("Jugendflamme Pflicht", false), List.of(new EventGroupSetDraft(null, "Übung", 0)));
        var practice = set(eventGroup, "Übung");
        var saturday = oneTime(30);
        var sunday = oneTime(31);
        service.addPart(station.id(), eventGroup.id(), saturday.id(), practice.id());
        service.addPart(station.id(), eventGroup.id(), sunday.id(), practice.id());

        assertTrue(service.setRequired(station.id(), eventGroup.id(), saturday.id(), true)
                .required());
        assertEquals(1, set(eventGroup, "Übung").minimum());
        service.setRequired(station.id(), eventGroup.id(), sunday.id(), true);
        assertEquals(2, set(eventGroup, "Übung").minimum());

        service.setRequired(station.id(), eventGroup.id(), sunday.id(), false);
        assertEquals(2, set(eventGroup, "Übung").minimum(), "taking the mark off leaves the minimum");
        var belowRequired = assertThrows(
                RefusalResponse.class,
                () -> service.replaceSets(
                        station.id(), eventGroup.id(), List.of(new EventGroupSetDraft(practice.id(), "Übung", 0))));
        assertEquals(EventRefusal.EVENT_GROUP_SET_MINIMUM_OUT_OF_RANGE, belowRequired.refusal());
        assertEquals(RefusalDetail.range(1, 2L), belowRequired.detail(), "one part is required, two are held");
        service.replaceSets(station.id(), eventGroup.id(), List.of(new EventGroupSetDraft(practice.id(), "Übung", 1)));
        assertEquals(1, set(eventGroup, "Übung").minimum());
    }

    @Test
    void aRequiredPartKeepsItsMarkWhenMovedAndLosesItWhenRemoved() {
        var eventGroup = create(
                details("Jugendflamme Verschieben", false),
                List.of(new EventGroupSetDraft(null, "Übung", 0), new EventGroupSetDraft(null, "Prüfung", 0)));
        var practice = set(eventGroup, "Übung");
        var test = set(eventGroup, "Prüfung");
        var saturday = oneTime(32);
        service.addPart(station.id(), eventGroup.id(), saturday.id(), practice.id());
        service.setRequired(station.id(), eventGroup.id(), saturday.id(), true);

        var moved = service.movePart(station.id(), eventGroup.id(), saturday.id(), test.id());
        assertTrue(moved.required());
        assertEquals(1, set(eventGroup, "Prüfung").minimum(), "the set it moves into asks for it");

        service.removePart(station.id(), eventGroup.id(), saturday.id());
        assertTrue(eventGroupRepo.findPart(saturday.id()).isEmpty());
        service.addPart(station.id(), eventGroup.id(), saturday.id(), practice.id());
        assertFalse(eventGroupRepo.findPart(saturday.id()).orElseThrow().required());
    }

    @Test
    void onlyAPartOfTheEventGroupCanBeRequired() {
        var eventGroup = jugendflamme();
        var loose = oneTime(33);

        assertEquals(
                EventRefusal.EVENT_GROUP_PART_NOT_IN_EVENT_GROUP,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.setRequired(station.id(), eventGroup.id(), loose.id(), true))
                        .refusal());
    }
}
