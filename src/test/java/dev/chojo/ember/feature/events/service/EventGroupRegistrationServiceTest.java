/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signing up for an event group: choosing parts under the sets' rules, the limit and the deadline,
 * changing the parts chosen, and which parts a sign-up reaches.
 */
class EventGroupRegistrationServiceTest extends EventGroupRegistrationTestBase {
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
        assertEquals(Set.of(sat1, sun1, test), standingOf(anna).counted());
    }

    @Test
    void aChoiceBreakingTheRulesIsRefusedUnlessWhoeverRunsEventsSignsUp() {
        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_SHORT, () -> signUp(anna, sat1, test));
        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_SHORT, () -> signUp(anna, sat1, sun1));

        service.register(station.id(), eventGroup.id(), anna.id(), Set.of(sat1), Map.of(), leader.id(), true);

        assertEquals(Map.of(sat1, RegistrationStatus.ACCEPTED), partsOf(anna));
        var standing = standingOf(anna).sets();
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
        assertEquals(Set.of(sun1, sat2, test), standingOf(anna).counted());
        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(anna).get(sat1));
    }

    @Test
    void partsAreAddedAndDroppedWhileTheRulesStillHold() {
        signUp(anna, sat1, sun1, test);

        service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sun1, sat2, test), null, false);

        assertEquals(Set.of(sun1, sat2, test), standingOf(anna).counted());
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
        assertEquals(Set.of(sat1, sun1, test), standingOf(anna).counted());

        assertRefused(EventRefusal.EVENT_GROUP_CHOICE_CALLED_OFF, () -> signUp(ben, sat1, sun1, test));
        signUp(ben, sat1, sat2, test);
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
        int partRegistration = registrationsOf(anna, List.of(sat1)).getFirst().id();
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP,
                () -> registrations.updateStatus(partRegistration, RegistrationStatus.DENIED));
        assertRefused(EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, () -> registrations.withdraw(partRegistration));
        assertRefused(EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, () -> registrations.refuse(partRegistration));
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP, () -> registrations.undoWithdrawal(partRegistration));
    }

    @Test
    void aPartThatTookPlaceStaysAsItIs() {
        var sets = eventGroups.findSets(eventGroup.id()).stream()
                .collect(Collectors.toMap(EventGroupSet::name, EventGroupSet::id));
        int yesterday = part(eventGroup, sets.get("Übung"), -1).id();
        service.register(
                station.id(), eventGroup.id(), anna.id(), Set.of(yesterday, sat1, test), Map.of(), leader.id(), true);

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_TOOK_PLACE,
                () -> service.changeParts(
                        station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, test), null, false));
        assertRefused(EventRefusal.EVENT_GROUP_PART_TOOK_PLACE, () -> signUp(ben, yesterday, sat1, test));

        service.withdraw(station.id(), eventGroup.id(), anna.id());

        var rows = registrationsOf(anna, List.of(yesterday, sat1));
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
        assertEquals(Set.of(sun1, sat2, test), standingOf(anna).counted());
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
        int later = part(eventGroup, sets.get("Übung"), 29).id();

        assertEquals(Set.of(sun1, test), standingOf(anna).counted());
        assertTrue(!standingOf(anna).counted().contains(later));
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
}
