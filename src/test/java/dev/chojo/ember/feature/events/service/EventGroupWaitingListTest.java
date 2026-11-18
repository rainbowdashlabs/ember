/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.repository.EventGroupSettingsRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/** The waiting list of an event group: who waits, in which order, and who moves up. */
class EventGroupWaitingListTest extends EventGroupRegistrationTestBase {

    private EventGroupSet set(String name) {
        return eventGroups.findSets(eventGroup.id()).stream()
                .filter(set -> set.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** Practice becomes "one of these", and every part gets one place except the test, which has room. */
    private void alternativesWithOnePlaceEach() {
        eventGroups.replaceSets(
                station.id(),
                eventGroup.id(),
                List.of(
                        new EventGroupSetDraft(set("Übung").id(), "Übung", 1, 1),
                        new EventGroupSetDraft(set("Prüfung").id(), "Prüfung", 1, 1)));
        eventGroups.update(station.id(), eventGroup.id(), details(null, 1));
        new EventGroupSettingsRepository().setOwnLimit(test, 10);
    }

    private EventGroupDetails details(Integer people, Integer perPart) {
        return new EventGroupDetails(
                "Jugendflamme 1",
                null,
                people,
                Instant.now().plus(Duration.ofDays(5)),
                eventGroup.details().requiresConfirmation(),
                null,
                null,
                null,
                perPart,
                null);
    }

    private RegistrationStatus statusOf(StationMember member) {
        return service.find(eventGroup.id(), member.id()).status();
    }

    private Integer placeInLine(StationMember member) {
        return standingOf(member).queuePosition();
    }

    private boolean toldMovedUp(StationMember member) {
        var told = ArgumentCaptor.forClass(DomainEvent.class);
        verify(bus, atLeast(0)).publish(told.capture());
        return told.getAllValues().stream()
                .filter(EventGroupRegistrationStatusChanged.class::isInstance)
                .map(EventGroupRegistrationStatusChanged.class::cast)
                .anyMatch(event -> event.memberId() == member.id() && event.newStatus() == RegistrationStatus.ACCEPTED);
    }

    @Test
    void aFullPartLeavesTheAlternativeOrTheWaitingList() {
        alternativesWithOnePlaceEach();
        signUp(anna, sat1, test);

        signUp(ben, sat1, test);
        signUp(carla, sun1, test);

        assertEquals(RegistrationStatus.QUEUED, statusOf(ben));
        assertEquals(RegistrationStatus.QUEUED, partsOf(ben).get(test), "the whole sign-up waits, never part of it");
        assertEquals(1, placeInLine(ben));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(carla));
        assertNull(placeInLine(carla));
    }

    @Test
    void aFreedPlaceTakesTheFirstInLineThatFitsAndPassesOverWhoWaitsForAnotherPart() {
        alternativesWithOnePlaceEach();
        signUp(anna, sat1, test);
        signUp(carla, sun1, test);
        signUp(ben, sat1, test);
        signUp(leader, sun1, test);

        service.withdraw(station.id(), eventGroup.id(), carla.id());

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(leader));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(leader).get(sun1));
        assertTrue(toldMovedUp(leader));
        assertEquals(RegistrationStatus.QUEUED, statusOf(ben), "the place on Saturday is still taken");
        assertEquals(1, placeInLine(ben));
    }

    @Test
    void aHeldPlaceIsNeverTradedForTheWaitingListByChangingParts() {
        alternativesWithOnePlaceEach();
        signUp(anna, sat1, test);
        signUp(carla, sun1, test);

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_FULL,
                () -> service.changeParts(station.id(), eventGroup.id(), carla.id(), Set.of(sat1, test), null, false));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(carla).get(sun1));
    }

    @Test
    void aWaitingSignUpChangingToAFreeAlternativeGetsInAtOnce() {
        alternativesWithOnePlaceEach();
        signUp(anna, sat1, test);
        signUp(ben, sat1, test);

        service.changeParts(station.id(), eventGroup.id(), ben.id(), Set.of(sat2, test), null, false);

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(ben));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(ben).get(sat2));
    }

    @Test
    void theLimitOnPeopleCountsAcceptedSignUpsAndARaisedLimitMovesUpAsManyAsFit() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        signUp(carla, sat1, sun1, test);
        assertEquals(RegistrationStatus.QUEUED, statusOf(carla));

        eventGroups.update(station.id(), eventGroup.id(), details(2, null));
        service.moveUp(station.id(), eventGroup.id());

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(ben));
        assertEquals(RegistrationStatus.QUEUED, statusOf(carla));
        assertEquals(1, placeInLine(carla));
    }

    @Test
    void whoeverRunsEventsSignsUpBeyondEveryLimit() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);

        service.register(
                station.id(), eventGroup.id(), ben.id(), Set.of(sat1, sun1, test), Map.of(), leader.id(), true);

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(ben));
    }

    @Test
    void whoeverDecidesPutsPendingSignUpsOnTheListAndReordersIt() {
        eventGroup = jugendflamme(1, true, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        signUp(carla, sat1, sun1, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.ACCEPTED);

        service.moveToQueue(station.id(), eventGroup.id(), List.of(carla.id(), ben.id()));
        assertEquals(RegistrationStatus.QUEUED, statusOf(ben));
        assertEquals(RegistrationStatus.QUEUED, partsOf(ben).get(sat1));
        assertEquals(1, placeInLine(ben), "they join in sign-up order");
        assertEquals(2, placeInLine(carla));

        service.moveInQueue(station.id(), eventGroup.id(), carla.id(), 1);
        assertEquals(1, placeInLine(carla));

        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.DENIED);
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(carla), "moving up skips the decision");
        assertEquals(RegistrationStatus.QUEUED, statusOf(ben));
    }

    @Test
    void onlySignUpsWaitingForADecisionGoOntoTheListAndOnlyWaitingOnesHaveAPlaceInLine() {
        eventGroup = jugendflamme(1, true, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.ACCEPTED);

        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_PENDING_FOR_QUEUE,
                () -> service.moveToQueue(station.id(), eventGroup.id(), List.of(anna.id())));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_QUEUED,
                () -> service.moveInQueue(station.id(), eventGroup.id(), anna.id(), 1));
    }

    @Test
    void anEventGroupWithoutAnyLimitHasNoWaitingList() {
        eventGroup = jugendflamme(null, true, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);

        assertRefused(
                EventRefusal.QUEUE_WITHOUT_LIMIT,
                () -> service.moveToQueue(station.id(), eventGroup.id(), List.of(anna.id())));
    }

    @Test
    void leavingTheWaitingListClosesTheLine() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        signUp(carla, sat1, sun1, test);

        service.withdraw(station.id(), eventGroup.id(), ben.id());

        assertEquals(RegistrationStatus.WITHDRAWN, statusOf(ben));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(anna), "leaving the list frees nothing");
        assertEquals(1, placeInLine(carla));
        assertEquals(1, eventGroups.tally(eventGroup.id()).holding());
        assertEquals(1, eventGroups.tally(eventGroup.id()).queued());
    }

    @Test
    void aCalledOffEventGroupMovesNobodyUp() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        eventGroupRepo.cancel(eventGroup.id(), "Unwetter");

        service.moveUp(station.id(), eventGroup.id());

        assertEquals(RegistrationStatus.QUEUED, statusOf(ben));
    }

    @Test
    void whoeverRunsEventsChoosesBetweenTheWaitingListAndGoingBeyondTheLimit() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);

        assertRefused(
                EventRefusal.REGISTRATION_FULL,
                () -> service.register(
                        station.id(),
                        eventGroup.id(),
                        ben.id(),
                        Set.of(sat1, sun1, test),
                        Map.of(),
                        leader.id(),
                        true,
                        null));
        service.register(
                station.id(),
                eventGroup.id(),
                ben.id(),
                Set.of(sat1, sun1, test),
                Map.of(),
                leader.id(),
                true,
                WhenFull.QUEUE);
        service.register(
                station.id(),
                eventGroup.id(),
                carla.id(),
                Set.of(sat1, sun1, test),
                Map.of(),
                leader.id(),
                true,
                WhenFull.OVER_LIMIT);

        assertEquals(RegistrationStatus.QUEUED, statusOf(ben));
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(carla));
    }

    @Test
    void acceptingFromTheWaitingListBeyondTheLimitHasToBeSaid() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);

        assertRefused(
                EventRefusal.REGISTRATION_FULL,
                () -> service.decide(station.id(), eventGroup.id(), ben.id(), RegistrationStatus.ACCEPTED, false));
        assertEquals(RegistrationStatus.QUEUED, statusOf(ben));

        service.decide(station.id(), eventGroup.id(), ben.id(), RegistrationStatus.ACCEPTED, true);
        assertEquals(RegistrationStatus.ACCEPTED, statusOf(ben));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(ben).get(sat1));
    }

    @Test
    void aPartLeavingEndsTheWaitingThatOnlyStoodWithTheEventGroup() {
        alternativesWithOnePlaceEach();
        signUp(anna, sat1, test);
        signUp(ben, sat1, test);

        eventGroups.removePart(station.id(), eventGroup.id(), sat1);

        assertEquals(RegistrationStatus.WITHDRAWN, partsOf(ben).get(sat1));
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(sat1), "a held place stays held");
    }

    @Test
    void anAcceptedSignUpGivingUpAPartLetsWhoeverWaitsForItMoveUp() {
        eventGroups.replaceSets(
                station.id(),
                eventGroup.id(),
                List.of(
                        new EventGroupSetDraft(set("Übung").id(), "Übung", 1, 2),
                        new EventGroupSetDraft(set("Prüfung").id(), "Prüfung", 1, 1)));
        eventGroups.update(station.id(), eventGroup.id(), details(null, 1));
        new EventGroupSettingsRepository().setOwnLimit(test, 10);
        signUp(anna, sat1, sun1, test);
        signUp(ben, sun1, test);

        service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sat1, test), null, false);

        assertEquals(RegistrationStatus.ACCEPTED, statusOf(ben));
    }

    @Test
    void whoeverWaitsForAPartAlreadyPastMovesUpNoMore() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        signUp(anna, sat1, sun1, test);
        signUp(ben, sat1, sun1, test);
        query(
                        "UPDATE station_event SET start_time = start_time - INTERVAL '40 days', end_time = end_time - INTERVAL '40 days' WHERE id = :id;")
                .single(call().bind("id", sat1))
                .update();

        service.withdraw(station.id(), eventGroup.id(), anna.id());

        assertEquals(RegistrationStatus.QUEUED, statusOf(ben));
    }

    @Test
    void anEventGroupKeptByFairnessPutsAHigherScoreFirst() {
        eventGroup = jugendflamme(1, false, Instant.now().plus(Duration.ofDays(5)));
        query("UPDATE event_group SET queue_order = 'FAIRNESS' WHERE id = :id;")
                .single(call().bind("id", eventGroup.id()))
                .update();
        var past = eventRepo.create(
                station.id(),
                "Vorher",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().minus(Duration.ofDays(20)),
                Instant.now().minus(Duration.ofDays(20)).plus(Duration.ofHours(2)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            eventRegistrationRepo.create(
                    past.id(), carla.id(), LocalDate.now().minusDays(20), RegistrationStatus.DENIED, null);
            signUp(anna, sat1, sun1, test);
            signUp(ben, sat1, sun1, test);
            eventGroup = eventGroupRepo.findById(eventGroup.id()).orElseThrow();

            signUp(carla, sat1, sun1, test);

            assertEquals(1, placeInLine(carla));
            assertEquals(2, placeInLine(ben));
        } finally {
            eventRepo.delete(past.id());
        }
    }
}
