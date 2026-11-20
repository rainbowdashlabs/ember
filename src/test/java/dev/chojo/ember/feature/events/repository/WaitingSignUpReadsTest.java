/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
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
import java.util.Map;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the reads of registrations treat a sign-up waiting on the waiting list. */
class WaitingSignUpReadsTest extends RepositoryTestBase {
    private static final LocalDate DATE = LocalDate.of(2028, 10, 2);

    private static Station station;
    private static Account holderAccount;
    private static Account waiterAccount;
    private static StationMember holder;
    private static StationMember waiter;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Waiting Reads Station");
        holderAccount = accountRepo.create("waiting-holder@test.com", "Hold", "Er");
        waiterAccount = accountRepo.create("waiting-waiter@test.com", "Wait", "Er");
        holder = stationMemberRepo.create(station.id(), holderAccount.id());
        waiter = stationMemberRepo.create(station.id(), waiterAccount.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(holderAccount.id());
        accountRepo.delete(waiterAccount.id());
    }

    private static StationEvent event() {
        var start = Instant.parse("2028-10-02T16:00:00Z");
        return eventRepo.create(
                station.id(),
                "Atemschutz",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                1,
                null,
                null);
    }

    private static EventRegistration waiting(StationEvent event, LocalDate date, int position) {
        return waiting(event, waiter, date, position);
    }

    private static EventRegistration waiting(StationEvent event, StationMember member, LocalDate date, int position) {
        var registration = eventRegistrationRepo.create(event.id(), member.id(), date, RegistrationStatus.QUEUED, null);
        query("""
                INSERT INTO registration_queue(event_id, event_date, registration_id, position)
                VALUES (:event_id, :event_date, :registration_id, :position);""")
                .single(call().bind("event_id", event.id())
                        .bind("event_date", date)
                        .bind("registration_id", registration.id())
                        .bind("position", position))
                .insert();
        return registration;
    }

    private static Map<Integer, Integer> positionsOn(int eventId, LocalDate date) {
        return query("""
                        SELECT registration_id, position FROM registration_queue
                        WHERE event_id = :event_id AND event_date = :event_date;""")
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .map(row -> Map.entry(row.getInt("registration_id"), row.getInt("position")))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<Integer, LocalDate> queuedDates(int eventId) {
        return Map.copyOf(query("""
                        SELECT registration_id, event_date FROM registration_queue WHERE event_id = :event_id;""")
                .single(call().bind("event_id", eventId))
                .map(row -> Map.entry(row.getInt("registration_id"), row.getObject("event_date", LocalDate.class)))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Test
    void whoeverWaitsHoldsNoPlaceButIsToldOfChanges() {
        var event = event();
        try {
            eventRegistrationRepo.create(event.id(), holder.id(), DATE, RegistrationStatus.ACCEPTED, null);
            waiting(event, DATE, 1);

            assertEquals(List.of(holder.id()), eventRegistrationRepo.findRegisteredMemberIds(event.id(), DATE));
            assertEquals(List.of(holder.id()), eventRegistrationRepo.findRegisteredMemberIdsFrom(event.id(), DATE));
            assertEquals(
                    List.of(holder.id(), waiter.id()).stream().sorted().toList(),
                    eventRegistrationRepo.findConcernedMemberIds(event.id(), DATE).stream()
                            .sorted()
                            .toList());
            assertEquals(
                    List.of(holder.id(), waiter.id()).stream().sorted().toList(),
                    eventRegistrationRepo.findConcernedMemberIdsFrom(event.id(), DATE).stream()
                            .sorted()
                            .toList());
            assertTrue(eventRegistrationRepo
                    .findFirstHeldDatesFrom(event.id(), DATE)
                    .containsKey(waiter.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void leavingTheWaitingListIsAnAnswer() {
        var event = event();
        try {
            var registration = waiting(event, DATE, 1);
            eventRegistrationRepo.recordAnswer(registration.id(), RegistrationStatus.WITHDRAWN);

            assertTrue(eventRegistrationRepo
                    .findById(registration.id())
                    .orElseThrow()
                    .gaveUpPlace());
            assertFalse(
                    eventRegistrationRepo
                            .findUnansweredMemberIds(event.id(), station.id(), DATE)
                            .contains(waiter.id()),
                    "whoever left the waiting list is not asked again");
            assertTrue(queuedDates(event.id()).isEmpty(), "leaving the status leaves the list");
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aDateThatGoesTakesItsWaitingListAlong() {
        var event = event();
        try {
            var registration = waiting(event, DATE, 1);

            eventRegistrationRepo.withdrawOnDates(event.id(), List.of(DATE));

            assertEquals(
                    RegistrationStatus.WITHDRAWN,
                    eventRegistrationRepo
                            .findById(registration.id())
                            .orElseThrow()
                            .status());
            assertTrue(queuedDates(event.id()).isEmpty());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aMovedAppointmentKeepsItsWaitingListBehindThoseAlreadyWaitingThere() {
        var event = event();
        var moved = DATE.plusDays(3);
        try {
            var registration = waiting(event, DATE, 1);

            assertEquals(1, eventRegistrationRepo.moveDate(event.id(), DATE, moved));

            assertEquals(Map.of(registration.id(), moved), queuedDates(event.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aMovedAppointmentPutsItsWaitingListBehindTheOneOnTheNewDay() {
        var event = event();
        var moved = DATE.plusDays(3);
        try {
            var alreadyThere = waiting(event, holder, moved, 4);
            var first = waiting(event, waiter, DATE, 1);

            eventRegistrationRepo.moveDate(event.id(), DATE, moved);

            var positions = positionsOn(event.id(), moved);
            assertEquals(4, positions.get(alreadyThere.id()));
            assertTrue(positions.get(first.id()) > 4, "whoever comes along waits behind those already waiting");
        } finally {
            eventRepo.delete(event.id());
        }
    }
}
