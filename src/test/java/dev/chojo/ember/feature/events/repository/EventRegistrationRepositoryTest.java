/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRegistrationRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Registration Station");
        account = accountRepo.create("event-registration@test.com", "Event", "Registrant");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static StationEvent event(String name, Instant start) {
        return eventRepo.create(
                station.id(),
                name,
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
                null,
                null,
                null,
                null);
    }

    /**
     * Saying nothing is having no answer on record, or having taken one back. Declining is an answer and
     * is not asked again; withdrawing leaves the event unanswered, which is what a reminder is for.
     */
    @Test
    void whoStillOwesAnAnswer() {
        var created = event("Antwortpflicht", Instant.parse("2028-06-01T09:00:00Z"));
        LocalDate date = LocalDate.of(2028, 6, 1);
        try {
            assertTrue(
                    eventRegistrationRepo
                            .findUnansweredMemberIds(created.id(), station.id())
                            .contains(member.id()),
                    "somebody who has not answered owes one");

            var reg = eventRegistrationRepo.create(created.id(), member.id(), date, RegistrationStatus.DECLINED, null);
            assertFalse(
                    eventRegistrationRepo
                            .findUnansweredMemberIds(created.id(), station.id())
                            .contains(member.id()),
                    "declining is an answer");

            eventRegistrationRepo.updateStatus(reg.id(), RegistrationStatus.WITHDRAWN);
            assertTrue(
                    eventRegistrationRepo
                            .findUnansweredMemberIds(created.id(), station.id())
                            .contains(member.id()),
                    "taking an answer back leaves none");
        } finally {
            eventRepo.delete(created.id());
        }
    }

    /**
     * What the dashboard reads: events still open for answers that this household has not answered, one
     * row per event and member. A closed one is not listed, because the answer is no longer theirs to
     * give, and an answered one is not either.
     */
    @Test
    void whatIsStillWaitingOnAnAnswer() {
        var open = eventRepo.create(
                station.id(),
                "Offen",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(java.time.Duration.ofDays(9)),
                Instant.now().plus(java.time.Duration.ofDays(9)).plusSeconds(3600),
                null,
                true,
                Instant.now().plus(java.time.Duration.ofDays(2)),
                false,
                null,
                null,
                null,
                null,
                null);
        var closed = eventRepo.create(
                station.id(),
                "Zu",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(java.time.Duration.ofDays(9)),
                Instant.now().plus(java.time.Duration.ofDays(9)).plusSeconds(3600),
                null,
                true,
                Instant.now().minus(java.time.Duration.ofDays(1)),
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            var waiting = eventRegistrationRepo.findAwaitingAnswer(List.of(member.id()));
            assertTrue(waiting.stream().anyMatch(a -> a.eventId() == open.id()), "an open one waits");
            assertTrue(waiting.stream().noneMatch(a -> a.eventId() == closed.id()), "a closed one does not");

            var entry = waiting.stream()
                    .filter(a -> a.eventId() == open.id())
                    .findFirst()
                    .orElseThrow();
            assertEquals(member.id(), entry.memberId());
            assertEquals("Offen", entry.name());

            eventRegistrationRepo.create(open.id(), member.id(), LocalDate.now(), RegistrationStatus.DECLINED, null);
            assertTrue(
                    eventRegistrationRepo.findAwaitingAnswer(List.of(member.id())).stream()
                            .noneMatch(a -> a.eventId() == open.id()),
                    "an answer settles it");

            assertTrue(eventRegistrationRepo.findAwaitingAnswer(List.of()).isEmpty(), "nobody owes nothing");
        } finally {
            eventRepo.delete(open.id());
            eventRepo.delete(closed.id());
        }
    }

    @Test
    void createReadUpdateDelete() {
        var created = event("Registration Lifecycle", Instant.parse("2027-05-15T09:00:00Z"));
        LocalDate date = LocalDate.of(2027, 5, 15);
        try {
            var registration = eventRegistrationRepo.create(created.id(), member.id(), date);
            assertEquals(created.id(), registration.eventId());
            assertEquals(member.id(), registration.memberId());
            assertEquals(RegistrationStatus.PENDING, registration.status());

            assertTrue(eventRegistrationRepo.findById(registration.id()).isPresent());
            assertEquals(
                    1,
                    eventRegistrationRepo.findByEventAndDate(created.id(), date).size());
            assertFalse(eventRegistrationRepo.findByEvent(created.id()).isEmpty());
            assertFalse(eventRegistrationRepo.findByMember(member.id()).isEmpty());
            assertFalse(eventRegistrationRepo.findPendingByEvent(created.id()).isEmpty());
            assertEquals(
                    1,
                    eventRegistrationRepo
                            .findPendingByEventAndDate(created.id(), date)
                            .size());

            assertTrue(eventRegistrationRepo.updateStatus(registration.id(), RegistrationStatus.ACCEPTED));
            assertEquals(
                    RegistrationStatus.ACCEPTED,
                    eventRegistrationRepo
                            .findById(registration.id())
                            .orElseThrow()
                            .status());
            assertEquals(1, eventRegistrationRepo.countAccepted(created.id()));
            assertTrue(
                    eventRegistrationRepo.findRegisteredMemberIds(created.id()).contains(member.id()));

            assertTrue(eventRegistrationRepo.delete(registration.id()));
            assertTrue(eventRegistrationRepo.findById(registration.id()).isEmpty());
        } finally {
            eventRepo.delete(created.id());
        }
    }

    @Test
    void upsertReusesTheExistingRow() {
        var created = event("Upsert Event", Instant.parse("2027-07-15T09:00:00Z"));
        LocalDate date = LocalDate.of(2027, 7, 15);
        try {
            var first = eventRegistrationRepo.create(created.id(), member.id(), date, RegistrationStatus.PENDING, null);
            assertEquals(RegistrationStatus.PENDING, first.status());

            var second = eventRegistrationRepo.create(
                    created.id(), member.id(), date, RegistrationStatus.ACCEPTED, member.id());
            assertEquals(RegistrationStatus.ACCEPTED, second.status());
            assertEquals(first.id(), second.id());
            assertEquals(member.id(), second.createdBy());
        } finally {
            eventRepo.delete(created.id());
        }
    }

    @Test
    void pendingAndCountsAreScopedToTheStation() {
        var created = event("Pending Counts", Instant.parse("2028-01-15T09:00:00Z"));
        LocalDate date = LocalDate.of(2028, 1, 15);
        try {
            assertEquals(0, eventRegistrationRepo.countPendingByStation(station.id()));
            var registration =
                    eventRegistrationRepo.create(created.id(), member.id(), date, RegistrationStatus.PENDING, null);

            var pending = eventRegistrationRepo.findPendingByStation(station.id());
            assertEquals(1, pending.size());
            assertEquals(RegistrationStatus.PENDING, pending.getFirst().status());
            assertTrue(eventRegistrationRepo.countPendingByStation(station.id()) >= 1);

            assertFalse(eventRegistrationRepo.findCountsByStation(station.id()).isEmpty());

            eventRegistrationRepo.updateStatus(registration.id(), RegistrationStatus.ACCEPTED);
            assertTrue(eventRegistrationRepo.findPendingByStation(station.id()).isEmpty());
        } finally {
            eventRepo.delete(created.id());
        }
    }

    @Test
    void findDeclinedMemberIds() {
        var created = event("Declined Event", Instant.parse("2026-11-01T09:00:00Z"));
        LocalDate date = LocalDate.of(2026, 11, 1);
        try {
            eventRegistrationRepo.create(created.id(), member.id(), date, RegistrationStatus.DECLINED, null);
            assertTrue(eventRegistrationRepo
                    .findDeclinedMemberIds(created.id(), date)
                    .contains(member.id()));
        } finally {
            eventRepo.delete(created.id());
        }
    }

    @Test
    void findStatsByEvent() {
        var created = event("Stats Event", Instant.parse("2026-10-01T09:00:00Z"));
        try {
            eventRegistrationRepo.create(
                    created.id(), member.id(), LocalDate.now().plusDays(2), RegistrationStatus.ACCEPTED, null);
            var stats = eventRegistrationRepo.findStatsByEvent(created.id(), null, 12);
            assertNotNull(stats);
            assertTrue(stats.stream().anyMatch(s -> s.memberId() == member.id()));
        } finally {
            eventRepo.delete(created.id());
        }
    }

    @Test
    void findByMembersAggregatesAcrossMembers() {
        assertTrue(eventRegistrationRepo.findByMembers(List.of()).isEmpty());
        assertTrue(eventRegistrationRepo.findByMembers(List.of(99999, 99998)).isEmpty());

        var account2 = accountRepo.create("evt-bulk-2@test.com", "Bulk", "Two");
        var member2 = stationMemberRepo.create(station.id(), account2.id());
        var created = event("Bulk Registrations", Instant.parse("2027-09-15T09:00:00Z"));
        try {
            var date = LocalDate.of(2027, 9, 15);
            eventRegistrationRepo.create(created.id(), member.id(), date, RegistrationStatus.ACCEPTED, null);
            eventRegistrationRepo.create(created.id(), member2.id(), date, RegistrationStatus.PENDING, null);

            var both = eventRegistrationRepo.findByMembers(List.of(member.id(), member2.id()));
            assertEquals(2, both.size());
            var owner = eventRegistrationRepo.findByMembers(List.of(member.id()));
            assertEquals(1, owner.size());
            assertEquals(member.id(), owner.getFirst().memberId());
        } finally {
            eventRepo.delete(created.id());
            stationMemberRepo.delete(member2.id());
            accountRepo.delete(account2.id());
        }
    }

    @Test
    void findMaxCreatedAtAdvancesWithRegistrations() {
        assertEquals(Instant.EPOCH, eventRegistrationRepo.findMaxCreatedAt(List.of()));
        assertEquals(Instant.EPOCH, eventRegistrationRepo.findMaxCreatedAt(List.of(99999)));

        var freshStation = stationRepo.create("Fresh Registration Tracking");
        var freshAccount = accountRepo.create("reg-track@test.com", "Reg", "Track");
        var freshMember = stationMemberRepo.create(freshStation.id(), freshAccount.id());
        var created = eventRepo.create(
                freshStation.id(),
                "Tracked",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-10-10T09:00:00Z"),
                Instant.parse("2027-10-10T11:00:00Z"),
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
            assertEquals(Instant.EPOCH, eventRegistrationRepo.findMaxCreatedAt(List.of(freshMember.id())));
            eventRegistrationRepo.create(
                    created.id(), freshMember.id(), LocalDate.of(2027, 10, 10), RegistrationStatus.PENDING, null);
            assertTrue(
                    eventRegistrationRepo
                            .findMaxCreatedAt(List.of(freshMember.id()))
                            .isAfter(Instant.EPOCH),
                    "registration insert should bump the stamp");
        } finally {
            eventRepo.delete(created.id());
            stationMemberRepo.delete(freshMember.id());
            accountRepo.delete(freshAccount.id());
            stationRepo.delete(freshStation.id());
        }
    }

    @Test
    void unknownRegistrationsAreReportedAsAbsent() {
        assertTrue(eventRegistrationRepo.findById(99999).isEmpty());
        assertFalse(eventRegistrationRepo.updateStatus(99999, RegistrationStatus.ACCEPTED));
        assertFalse(eventRegistrationRepo.delete(99999));
        assertTrue(
                eventRegistrationRepo.findByEventAndDate(99999, LocalDate.now()).isEmpty());
        assertTrue(eventRegistrationRepo.findByEvent(99999).isEmpty());
        assertTrue(eventRegistrationRepo.findPendingByEvent(99999).isEmpty());
        assertTrue(eventRegistrationRepo.findByMember(99999).isEmpty());
        assertTrue(eventRegistrationRepo
                .findDeclinedMemberIds(99999, LocalDate.now())
                .isEmpty());
        assertTrue(eventRegistrationRepo.findRegisteredMemberIds(99999).isEmpty());
        assertEquals(0, eventRegistrationRepo.countAccepted(99999));
    }
}
