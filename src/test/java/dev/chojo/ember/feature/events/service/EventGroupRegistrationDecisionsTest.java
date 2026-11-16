/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Deciding about event group sign-ups: confirming and turning them down, withdrawing, what still waits and
 * what is announced.
 */
class EventGroupRegistrationDecisionsTest extends EventGroupRegistrationTestBase {
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
    void signingUpAgainAfterBeingTurnedDownBringsBackOnlyTheNewChoice() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.DENIED);

        signUp(anna, sun1, sat2, test);
        service.decide(station.id(), eventGroup.id(), anna.id(), RegistrationStatus.ACCEPTED);

        assertEquals(Set.of(sun1, sat2, test), standingOf(anna).counted());
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

        assertEquals(Set.of(sat1, sun1, test), standingOf(anna).counted());
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(test));
        assertEquals(RegistrationStatus.DENIED, partsOf(anna).get(sat1));
    }

    @Test
    void aPartAddedToASignUpStillWaitingWaitsToo() {
        eventGroup = jugendflamme(null, true, null);
        signUp(anna, sat1, sun1, test);

        service.changeParts(station.id(), eventGroup.id(), anna.id(), Set.of(sat1, sun1, sat2, test), null, false);

        assertEquals(RegistrationStatus.PENDING, partsOf(anna).get(sat2));
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
}
