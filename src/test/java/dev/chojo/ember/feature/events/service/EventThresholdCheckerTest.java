/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check for too few registrations, one date at a time: it counts the accepted registrations of
 * each date inside the days before it, calls off the dates that fall short and no other, and never
 * calls off a date again once a manager brought it back.
 */
class EventThresholdCheckerTest extends RepositoryTestBase {
    private static Station station;
    private static final List<StationMember> MEMBERS = new ArrayList<>();

    private EventThresholdChecker checker;
    private EventCancellationService cancellation;
    private StationEvent event;
    private LocalDate today;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("ThresholdChecker Station");
        for (int i = 0; i < 4; i++) {
            Account account = accountRepo.create("threshold" + i + "@test.com", "Threshold", "Checker");
            MEMBERS.add(stationMemberRepo.create(station.id(), account.id()));
        }
    }

    @BeforeEach
    void wire() {
        var services = newEventServices(new DomainEventBus(Set.of()));
        cancellation = services.cancellation();
        checker = new EventThresholdChecker(
                eventRepo,
                eventRegistrationRepo,
                cancellation,
                services.calendar(),
                new StationReadOnlyGuard(stationRepo));
        var calendar = occurrenceCalendar.forStation(station.id());
        today = calendar.today();
        zone = calendar.zone();
    }

    @AfterEach
    void removeEvent() {
        if (event != null) eventRepo.delete(event.id());
        event = null;
    }

    /** A weekly series whose next date is tomorrow, needing two accepted registrations eight days out. */
    private StationEvent weekly() {
        LocalDate first = tomorrow().minusWeeks(4);
        return create(StationEvent.EventType.RECURRING, first.getDayOfWeek().getValue(), first, 8);
    }

    private StationEvent create(StationEvent.EventType type, Integer dayOfWeek, LocalDate first, int days) {
        event = eventRepo.create(
                station.id(),
                "Übung",
                "",
                type,
                dayOfWeek,
                first.atTime(18, 0).atZone(zone).toInstant(),
                first.atTime(20, 0).atZone(zone).toInstant(),
                null,
                true,
                null,
                false,
                null,
                null,
                2,
                days,
                null);
        return event;
    }

    private LocalDate tomorrow() {
        return today.plusDays(1);
    }

    private void accept(int count, LocalDate date) {
        for (int i = 0; i < count; i++) {
            eventRegistrationRepo.create(event.id(), MEMBERS.get(i).id(), date, RegistrationStatus.ACCEPTED, null);
        }
    }

    private boolean isCancelled(LocalDate date) {
        return eventDateCancellationRepo.isCancelled(event.id(), date);
    }

    /**
     * Only the date inside the stretch that falls short is called off: the registrations of the next
     * week do not count for tomorrow, and a date beyond the stretch is not asked yet.
     */
    @Test
    void onlyTheUnderSubscribedDateIsCancelled() {
        weekly();
        accept(1, tomorrow());
        accept(3, tomorrow().plusWeeks(1));

        checker.check();

        assertTrue(isCancelled(tomorrow()));
        assertFalse(isCancelled(tomorrow().plusWeeks(1)), "a date with enough registrations takes place");
        assertFalse(isCancelled(tomorrow().plusWeeks(2)), "a date beyond the stretch is not checked yet");
        assertFalse(eventRepo.findById(event.id()).orElseThrow().cancelled(), "the series goes on");
        var row = eventDateCancellationRepo.find(event.id(), tomorrow()).orElseThrow();
        assertEquals(CancellationCause.THRESHOLD, row.cause());
    }

    /** A date a manager brought back stays, however few have registered for it. */
    @Test
    void aRestoredDateIsNotCancelledAgain() {
        weekly();
        accept(3, tomorrow().plusWeeks(1));
        checker.check();
        assertTrue(isCancelled(tomorrow()));

        cancellation.restoreDate(event, tomorrow());
        checker.check();

        assertFalse(isCancelled(tomorrow()));
    }

    /** A one-time appointment has its one date checked the same way. */
    @Test
    void aOneOffInsideItsStretchIsCancelled() {
        create(StationEvent.EventType.ONE_TIME, null, today.plusDays(2), 3);

        checker.check();

        assertTrue(isCancelled(today.plusDays(2)));
        assertTrue(occurrenceCalendar.forStation(station.id()).cancelledAltogether(event));
    }

    @Test
    void aOneOffStillFarAwayIsLeftAlone() {
        create(StationEvent.EventType.ONE_TIME, null, today.plusDays(10), 3);

        checker.check();

        assertFalse(isCancelled(today.plusDays(10)));
    }

    @Test
    void checkWithNoEligibleEvents() {
        assertDoesNotThrow(() -> checker.check());
    }
}
