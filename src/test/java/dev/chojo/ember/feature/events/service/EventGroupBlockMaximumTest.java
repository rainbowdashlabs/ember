/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A block of an event group that allows at most a number of its parts, and places per part. */
class EventGroupBlockMaximumTest extends EventGroupRegistrationTestBase {

    private EventGroupSet set(String name) {
        return eventGroups.findSets(eventGroup.id()).stream()
                .filter(set -> set.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void allow(int practiceMinimum, Integer practiceMaximum) {
        eventGroups.replaceSets(
                station.id(),
                eventGroup.id(),
                List.of(
                        new EventGroupSetDraft(set("Übung").id(), "Übung", practiceMinimum, practiceMaximum),
                        new EventGroupSetDraft(set("Prüfung").id(), "Prüfung", 1, 1)));
    }

    @Test
    void aSetStoresItsMaximum() {
        allow(1, 1);

        assertEquals(1, set("Übung").maximum());
        assertEquals(1, set("Prüfung").maximum());
    }

    @Test
    void aMaximumBelowTheMinimumOrBelowOneIsRefused() {
        var belowMinimum = assertThrows(RefusalResponse.class, () -> allow(2, 1));
        assertEquals(EventRefusal.EVENT_GROUP_SET_MAXIMUM_OUT_OF_RANGE, belowMinimum.refusal());
        assertEquals(RefusalDetail.range(2, null), belowMinimum.detail());

        var none = assertThrows(RefusalResponse.class, () -> allow(0, 0));
        assertEquals(EventRefusal.EVENT_GROUP_SET_MAXIMUM_OUT_OF_RANGE, none.refusal());
        assertNull(set("Übung").maximum(), "nothing was saved");
    }

    @Test
    void aChoiceBeyondTheMaximumIsRefusedNamingTheSet() {
        allow(1, 1);

        var refusal = assertThrows(RefusalResponse.class, () -> signUp(anna, sat1, sun1, test));

        assertEquals(EventRefusal.EVENT_GROUP_SET_TOO_MANY, refusal.refusal());
        assertEquals(RefusalDetail.text("Übung"), refusal.detail());
        signUp(anna, sat1, test);
        assertEquals(RegistrationStatus.ACCEPTED, partsOf(anna).get(sat1));
    }

    @Test
    void whoeverRunsEventsMayGoBeyondTheMaximum() {
        allow(1, 1);

        service.register(station.id(), eventGroup.id(), ben.id(), Set.of(sat1, sun1, test), Map.of(), null, true);

        assertEquals(3, partsOf(ben).size());
    }

    @Test
    void aCalledOffPartDoesNotCountTowardTheMaximum() {
        allow(1, 1);
        signUp(carla, sat1, test);
        callOff(sat1);

        service.changeParts(station.id(), eventGroup.id(), carla.id(), Set.of(sat1, sun1, test), null, false);

        assertEquals(RegistrationStatus.ACCEPTED, partsOf(carla).get(sun1));
    }

    @Test
    void requiringPartsRaisesTheMaximumWithTheMinimum() {
        allow(1, 1);

        eventGroups.setRequired(station.id(), eventGroup.id(), sat1, true);
        eventGroups.setRequired(station.id(), eventGroup.id(), sun1, true);

        assertEquals(2, set("Übung").minimum());
        assertEquals(2, set("Übung").maximum());
    }

    @Test
    void thePartsShowTheirPlacesAndWhoHoldsOne() {
        eventGroups.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails("Jugendflamme 1", null, null, null, false, null, null, null, 8, null));
        signUp(anna, sat1, sun1, test);

        var states = service.partStates(eventGroup.id());

        var first = states.stream()
                .filter(state -> state.eventId() == sat1)
                .findFirst()
                .orElseThrow();
        assertEquals(8, first.places());
        assertEquals(1, first.accepted());
        assertEquals(
                0,
                states.stream()
                        .filter(state -> state.eventId() == sat2)
                        .mapToInt(EventGroupPartState::accepted)
                        .sum());
    }

    @Test
    void placesPerPartAreAtLeastOne() {
        var refusal = assertThrows(
                RefusalResponse.class,
                () -> eventGroups.update(
                        station.id(),
                        eventGroup.id(),
                        new EventGroupDetails("Jugendflamme 1", null, null, null, false, null, null, null, 0, null)));

        assertEquals(EventRefusal.EVENT_GROUP_LIMIT_NOT_POSITIVE, refusal.refusal());
    }

    @Test
    void anAppointmentJoiningTakesThePlacesPerPartInsteadOfItsOldLimit() {
        var joining = eventRepo.create(
                station.id(),
                "Nachzügler",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                eventRepo.findById(sat1).orElseThrow().startTime().plusSeconds(86_400L * 10),
                eventRepo.findById(sat1).orElseThrow().endTime().plusSeconds(86_400L * 10),
                null,
                true,
                null,
                false,
                null,
                25,
                null,
                null,
                null);
        try {
            eventGroups.addPart(
                    station.id(), eventGroup.id(), joining.id(), set("Übung").id());

            assertNull(eventRepo.findById(joining.id()).orElseThrow().registrationLimit());
        } finally {
            eventGroupRepo.setPartSet(joining.id(), null);
            eventRepo.delete(joining.id());
        }
    }

    @Test
    void aNewEventGroupRefusesAMaximumBelowItsMinimum() {
        var refusal = assertThrows(
                RefusalResponse.class,
                () -> eventGroups.create(
                        station.id(),
                        new EventGroupDetails("Zu eng", null, null, null, false, null),
                        List.of(new EventGroupSetDraft(null, "Übung", 2, 1)),
                        RestrictionAudience.empty(),
                        RestrictionAudience.empty(),
                        null));

        assertEquals(EventRefusal.EVENT_GROUP_SET_MAXIMUM_OUT_OF_RANGE, refusal.refusal());
    }

    @Test
    void aMaximumHasRoomForEveryRequiredPartEvenOneCalledOff() {
        allow(1, null);
        eventGroups.setRequired(station.id(), eventGroup.id(), sat1, true);
        eventGroups.setRequired(station.id(), eventGroup.id(), sun1, true);
        callOff(sat1);

        var refusal = assertThrows(RefusalResponse.class, () -> allow(1, 1));

        assertEquals(EventRefusal.EVENT_GROUP_SET_MAXIMUM_OUT_OF_RANGE, refusal.refusal());
    }

    @Test
    void aPartLeavingTheEventGroupKeepsItsPlaces() {
        eventGroups.update(
                station.id(),
                eventGroup.id(),
                new EventGroupDetails("Jugendflamme 1", null, null, null, false, null, null, null, 10, null));

        eventGroups.removePart(station.id(), eventGroup.id(), sat2);

        assertEquals(10, eventRepo.findById(sat2).orElseThrow().registrationLimit());
    }
}
