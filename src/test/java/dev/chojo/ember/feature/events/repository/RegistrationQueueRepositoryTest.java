/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.Places;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationQueueRepositoryTest extends RepositoryTestBase {
    private static final RegistrationQueueRepository queue = new RegistrationQueueRepository();
    private static final EventGroupRepository eventGroups = new EventGroupRepository();
    private static final LocalDate DATE = LocalDate.of(2028, 9, 4);

    private static Station station;
    private static final List<Account> accounts = new ArrayList<>();
    private static final List<StationMember> members = new ArrayList<>();

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Places Station");
        for (int i = 0; i < 5; i++) {
            var account = accountRepo.create("places-" + i + "@test.com", "Places", "Member " + i);
            accounts.add(account);
            members.add(stationMemberRepo.create(station.id(), account.id()));
        }
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    private static StationEvent event(@Nullable Integer limit) {
        var start = Instant.parse("2028-09-04T16:00:00Z");
        return eventRepo.create(
                station.id(),
                "Lehrgang",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                null,
                false,
                null,
                limit,
                null,
                null,
                null);
    }

    private static void signUp(StationEvent event, int member, RegistrationStatus status) {
        eventRegistrationRepo.create(event.id(), members.get(member).id(), DATE, status, null);
    }

    @Test
    void onlyAcceptedSignUpsTakeAPlace() {
        var event = event(2);
        try {
            signUp(event, 0, RegistrationStatus.ACCEPTED);
            signUp(event, 1, RegistrationStatus.PENDING);
            signUp(event, 2, RegistrationStatus.QUEUED);
            signUp(event, 3, RegistrationStatus.DECLINED);
            signUp(event, 4, RegistrationStatus.WITHDRAWN);

            var places = queue.places(event.id(), DATE);

            assertEquals(new Places(2, 1, 0), places);
            assertFalse(places.full());
            assertEquals(1, places.free());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aDateIsFullOnceTheAcceptedReachTheLimitAndNeverHasLessThanNoPlace() {
        var event = event(1);
        try {
            signUp(event, 0, RegistrationStatus.ACCEPTED);
            signUp(event, 1, RegistrationStatus.ACCEPTED);

            var places = queue.places(event.id(), DATE);

            assertTrue(places.full());
            assertEquals(0, places.free());
            assertEquals(2, places.accepted());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void anotherDateOfTheSeriesKeepsItsOwnPlaces() {
        var event = event(1);
        try {
            signUp(event, 0, RegistrationStatus.ACCEPTED);

            assertFalse(queue.places(event.id(), DATE.plusWeeks(1)).full());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void anAppointmentWithoutLimitIsNeverFull() {
        var event = event(null);
        try {
            signUp(event, 0, RegistrationStatus.ACCEPTED);

            var places = queue.places(event.id(), DATE);

            assertNull(places.limit());
            assertNull(places.free());
            assertFalse(places.full());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void anAppointmentThatDoesNotExistHasNoLimit() {
        assertEquals(Places.UNLIMITED, queue.places(Integer.MAX_VALUE, DATE));
    }

    private static void waitFor(StationEvent event, int member, int position) {
        var registration = eventRegistrationRepo.create(
                event.id(), members.get(member).id(), DATE, RegistrationStatus.QUEUED, null);
        query("""
                INSERT INTO registration_queue(event_id, event_date, registration_id, position)
                VALUES (:event_id, :event_date, :registration_id, :position);""")
                .single(call().bind("event_id", event.id())
                        .bind("event_date", DATE)
                        .bind("registration_id", registration.id())
                        .bind("position", position))
                .insert();
    }

    private static void partnerAccepted(StationEvent event) {
        var partnerStation = stationRepo.create("Places Partner " + UUID.randomUUID());
        int partnerId = query("""
                        INSERT INTO federation_partner (station_id, partner_station_id, status, remote_host, cluster_managed)
                        VALUES (:station, :partner::UUID, 'ACTIVE', NULL, FALSE)
                        RETURNING id;""")
                .single(call().bind("station", station.id())
                        .bind("partner", partnerStation.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query("""
                INSERT INTO event_federation_registration(event_id, partner_id, remote_member_id, event_date, status)
                VALUES (:event_id, :partner_id, gen_random_uuid(), :event_date, 'ACCEPTED');""")
                .single(call().bind("event_id", event.id())
                        .bind("partner_id", partnerId)
                        .bind("event_date", DATE))
                .insert();
    }

    @Test
    void whoeverWaitsIsCountedAndTakesNoPlace() {
        var event = event(1);
        try {
            waitFor(event, 0, 1);
            waitFor(event, 1, 5);

            assertEquals(new Places(1, 0, 2), queue.places(event.id(), DATE));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aPartnersAcceptedMemberTakesAPlace() {
        var event = event(2);
        try {
            signUp(event, 0, RegistrationStatus.ACCEPTED);
            partnerAccepted(event);

            assertTrue(queue.places(event.id(), DATE).full());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aPartOfAnEventGroupTakesThePlacesPerPartUnlessItHasItsOwn() {
        var group = eventGroups.create(
                station.id(), new EventGroupDetails("Grundlehrgang", null, 30, null, false, null, null, null));
        var set = eventGroups.createSet(group.id(), "Übung", 1, 0);
        var inheriting = event(null);
        var own = event(null);
        var neither = event(null);
        try {
            eventGroups.claimPart(inheriting.id(), set.id());
            eventGroups.claimPart(own.id(), set.id());
            query("UPDATE station_event SET registration_limit = 4 WHERE id = :id;")
                    .single(call().bind("id", own.id()))
                    .update();
            query("UPDATE event_group SET part_registration_limit = 10 WHERE id = :id;")
                    .single(call().bind("id", group.id()))
                    .update();

            assertEquals(10, queue.places(inheriting.id(), DATE).limit());
            assertEquals(10, eventRepo.findById(inheriting.id()).orElseThrow().registrationLimit());
            assertEquals(4, queue.places(own.id(), DATE).limit());
            assertEquals(4, eventRepo.findById(own.id()).orElseThrow().registrationLimit());
            assertNull(
                    queue.places(neither.id(), DATE).limit(), "the people limit of the event group is no place limit");
        } finally {
            eventRepo.delete(inheriting.id());
            eventRepo.delete(own.id());
            eventRepo.delete(neither.id());
            eventGroups.delete(group.id());
        }
    }

    @Test
    void savingAPartKeepsItsOwnLimitAndPinsNothing() {
        var group = eventGroups.create(
                station.id(), new EventGroupDetails("Funklehrgang", null, null, null, false, null, null, null));
        var set = eventGroups.createSet(group.id(), "Teil", 1, 0);
        var part = event(null);
        try {
            eventGroups.claimPart(part.id(), set.id());
            query("UPDATE event_group SET part_registration_limit = 8 WHERE id = :id;")
                    .single(call().bind("id", group.id()))
                    .update();
            var read = eventRepo.findById(part.id()).orElseThrow();

            eventRepo.update(
                    part.id(),
                    read.name(),
                    read.description(),
                    read.eventType(),
                    read.dayOfWeek(),
                    read.startTime(),
                    read.endTime(),
                    null,
                    true,
                    null,
                    false,
                    null,
                    null,
                    read.registrationLimit(),
                    null,
                    null,
                    null);
            query("UPDATE event_group SET part_registration_limit = 12 WHERE id = :id;")
                    .single(call().bind("id", group.id()))
                    .update();

            assertEquals(12, eventRepo.findById(part.id()).orElseThrow().registrationLimit());
        } finally {
            eventRepo.delete(part.id());
            eventGroups.delete(group.id());
        }
    }

    @Test
    void aPartnersSignUpWaitsUnderItsOwnColumn() {
        var event = event(1);
        var list = RegistrationQueueRepository.ofDate(event.id(), DATE);
        try {
            var local = eventRegistrationRepo.create(
                    event.id(), members.get(0).id(), DATE, RegistrationStatus.QUEUED, null);
            var owner = RegistrationQueueRepository.QueueOwner.local(local.id());
            queue.insertAt(list, owner, queue.endOf(list), 50.0);
            var partnerOwner = RegistrationQueueRepository.QueueOwner.ofPartner(local.id());

            assertTrue(queue.entryOf(list, owner).isPresent());
            assertTrue(queue.entryOf(list, partnerOwner).isEmpty(), "the same id of a partner sign-up is another one");
            assertFalse(queue.remove(list, partnerOwner));
            assertEquals(50.0, queue.entries(list).getFirst().score());
        } finally {
            eventRepo.delete(event.id());
        }
    }
}
