/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventDateCancellation;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dates of appointments that were called off, and brought back. */
class EventDateCancellationRepositoryTest extends RepositoryTestBase {
    private static final LocalDate DATE = LocalDate.parse("2027-05-05");

    private static Station station;
    private static StationMember manager;
    private StationEvent event;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Cancelled Dates Station");
        Account account = accountRepo.create("cancelled-dates@test.com", "Cancel", "Dates");
        manager = stationMemberRepo.create(station.id(), account.id());
    }

    @BeforeEach
    void createEvent() {
        event = eventRepo.create(
                station.id(),
                "Weekly",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                Instant.parse("2027-01-06T18:00:00Z"),
                Instant.parse("2027-01-06T20:00:00Z"),
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

    @AfterEach
    void deleteEvent() {
        eventRepo.delete(event.id());
    }

    @Test
    void aDateIsCancelledOnceAndReadBack() {
        assertTrue(eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.MANUAL, "Sturm", manager.id()));
        assertFalse(
                eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.MANUAL, "Noch mal", null),
                "a date that is off is not called off twice");

        var row = eventDateCancellationRepo.find(event.id(), DATE).orElseThrow();
        assertEquals(CancellationCause.MANUAL, row.cause());
        assertEquals("Sturm", row.reason());
        assertEquals(manager.id(), row.cancelledBy());
        assertNotNull(row.cancelledAt());
        assertTrue(row.isActive());
        assertEquals(DATE, row.notice().date());
        assertTrue(eventDateCancellationRepo.isCancelled(event.id(), DATE));
        assertFalse(eventDateCancellationRepo.isCancelled(event.id(), DATE.plusWeeks(1)));
        assertTrue(eventDateCancellationRepo.find(event.id(), DATE.plusWeeks(1)).isEmpty());
    }

    @Test
    void aRestoredDateIsKeptAndCanBeCancelledAgain() {
        eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.THRESHOLD, null, null);

        assertTrue(eventDateCancellationRepo.restore(event.id(), DATE));
        assertFalse(eventDateCancellationRepo.restore(event.id(), DATE), "a restored date is not restored twice");
        var restored = eventDateCancellationRepo.find(event.id(), DATE).orElseThrow();
        assertFalse(restored.isActive());
        assertFalse(eventDateCancellationRepo.isCancelled(event.id(), DATE));

        assertTrue(eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.MANUAL, "Doch nicht", null));
        var again = eventDateCancellationRepo.find(event.id(), DATE).orElseThrow();
        assertTrue(again.isActive());
        assertEquals(CancellationCause.MANUAL, again.cause());
        assertEquals("Doch nicht", again.reason());
    }

    /** The check never calls off a date that was ever called off, a restored one least of all. */
    @Test
    void theCheckLeavesEveryDateWithARowAlone() {
        assertTrue(eventDateCancellationRepo.cancelForTooFewRegistrations(event.id(), DATE));
        var row = eventDateCancellationRepo.find(event.id(), DATE).orElseThrow();
        assertEquals(CancellationCause.THRESHOLD, row.cause());
        assertNull(row.reason());
        assertNull(row.cancelledBy());

        eventDateCancellationRepo.restore(event.id(), DATE);
        assertFalse(eventDateCancellationRepo.cancelForTooFewRegistrations(event.id(), DATE));
        assertFalse(eventDateCancellationRepo.isCancelled(event.id(), DATE));
    }

    @Test
    void theDatesThatAreOffAreListedPerEventAndPerStation() {
        eventDateCancellationRepo.cancel(event.id(), DATE.plusWeeks(1), CancellationCause.MANUAL, null, null);
        eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.MANUAL, null, null);
        eventDateCancellationRepo.cancel(event.id(), DATE.plusWeeks(2), CancellationCause.MANUAL, null, null);
        eventDateCancellationRepo.restore(event.id(), DATE.plusWeeks(2));

        assertEquals(
                List.of(DATE, DATE.plusWeeks(1)),
                eventDateCancellationRepo.findActiveByEvent(event.id()).stream()
                        .map(EventDateCancellation::eventDate)
                        .toList());
        assertEquals(
                2,
                eventDateCancellationRepo.findActiveByStation(station.id()).stream()
                        .filter(row -> row.eventId() == event.id())
                        .count());
        assertTrue(eventDateCancellationRepo.findActiveByStation(-1).isEmpty());
    }

    @Test
    void aMovedDateTakesItsCancellationAlong() {
        eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.MANUAL, "Sturm", null);

        eventDateCancellationRepo.moveDate(event.id(), DATE, DATE.plusDays(1));

        assertFalse(eventDateCancellationRepo.isCancelled(event.id(), DATE));
        assertTrue(eventDateCancellationRepo.isCancelled(event.id(), DATE.plusDays(1)));
    }

    @Test
    void theRowsOfDatesGoneAreForgotten() {
        eventDateCancellationRepo.cancel(event.id(), DATE, CancellationCause.MANUAL, null, null);
        eventDateCancellationRepo.cancel(event.id(), DATE.plusWeeks(1), CancellationCause.MANUAL, null, null);

        eventDateCancellationRepo.deleteOn(event.id(), List.of());
        eventDateCancellationRepo.deleteOn(event.id(), List.of(DATE));

        assertTrue(eventDateCancellationRepo.find(event.id(), DATE).isEmpty());
        assertTrue(eventDateCancellationRepo.isCancelled(event.id(), DATE.plusWeeks(1)));
    }
}
