/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who an appointment concerns on its dates, with and without registration. */
class EventAudienceTest extends RepositoryTestBase {
    private static final LocalDate FIRST = LocalDate.parse("2027-05-05");
    private static final LocalDate SECOND = FIRST.plusWeeks(1);
    private static final LocalDate THIRD = FIRST.plusWeeks(2);

    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static StationMember carla;
    private static final List<Integer> accounts = new ArrayList<>();

    private EventAudience audience;
    private EventRestrictionService restrictions;
    private StationEvent event;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Audience Station");
        anna = memberOf("anna@audience.test", "Anna");
        ben = memberOf("ben@audience.test", "Ben");
        carla = memberOf("carla@audience.test", "Carla");
    }

    private static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Audience");
        accounts.add(account.id());
        return stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        restrictions = new EventRestrictionService(eventRepo, restrictionService);
        audience = new EventAudience(eventRegistrationRepo, stationMemberRepo, restrictions, occurrenceCalendar);
    }

    @AfterEach
    void removeEvent() {
        if (event != null) eventRepo.delete(event.id());
    }

    private StationEvent weekly(boolean registration) {
        event = eventRepo.create(
                station.id(),
                "Dienst",
                null,
                StationEvent.EventType.RECURRING,
                FIRST.getDayOfWeek().getValue(),
                Instant.parse("2027-05-05T16:00:00Z"),
                Instant.parse("2027-05-05T18:00:00Z"),
                null,
                registration,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        return event;
    }

    private void register(StationMember member, LocalDate date, RegistrationStatus status) {
        eventRegistrationRepo.create(event.id(), member.id(), date, status, null);
    }

    @Test
    void withRegistrationItIsWhoeverHoldsAPlaceOnOneOfTheDates() {
        weekly(true);
        register(anna, FIRST, RegistrationStatus.ACCEPTED);
        register(anna, SECOND, RegistrationStatus.PENDING);
        register(ben, SECOND, RegistrationStatus.PENDING);
        register(carla, SECOND, RegistrationStatus.DECLINED);
        register(carla, THIRD, RegistrationStatus.ACCEPTED);

        assertEquals(List.of(anna.id()), audience.onDate(event, FIRST));
        assertEquals(List.of(anna.id(), ben.id()), audience.onDates(event, List.of(SECOND, FIRST)));
        assertEquals(
                List.of(anna.id(), ben.id(), carla.id()),
                audience.fromDate(event, SECOND).stream().sorted().toList());
        assertTrue(audience.onDates(event, List.of()).isEmpty());
    }

    @Test
    void withoutRegistrationItIsEverybodyWhoSeesItAndDidNotDeclineTheFirstDate() {
        weekly(false);
        register(ben, FIRST, RegistrationStatus.DECLINED);
        register(carla, SECOND, RegistrationStatus.DECLINED);

        assertEquals(
                List.of(anna.id(), carla.id()),
                audience.onDates(event, List.of(SECOND, FIRST)).stream()
                        .sorted()
                        .toList());
        assertEquals(
                List.of(anna.id(), ben.id()),
                audience.fromDate(event, SECOND).stream().sorted().toList());
    }

    @Test
    void withoutRegistrationTheDeclineOfTheFirstRealDateCounts() {
        weekly(false);
        register(carla, SECOND, RegistrationStatus.DECLINED);
        LocalDate dayBeforeSecond = SECOND.minusDays(1);

        assertEquals(
                List.of(anna.id(), ben.id()),
                audience.fromDate(event, dayBeforeSecond).stream().sorted().toList());
        assertEquals(
                List.of(anna.id(), ben.id()),
                audience.onDates(event, List.of(dayBeforeSecond, SECOND)).stream()
                        .sorted()
                        .toList());
        assertTrue(audience.onDates(event, List.of(dayBeforeSecond)).isEmpty());

        eventRepo.updateRepeatEnd(event.id(), THIRD, null);
        var ended = eventRepo.findById(event.id()).orElseThrow();
        assertTrue(audience.fromDate(ended, THIRD.plusDays(1)).isEmpty());
    }

    @Test
    void withoutRegistrationNobodyHearsOfAnAppointmentTheyCannotSee() {
        weekly(false);
        restrictions.setViewRestrictions(
                event.id(),
                new RestrictionSelection(List.of(), List.of(), List.of(), List.of(ben.id()), RestrictionMode.AND));

        assertEquals(List.of(ben.id()), audience.onDate(event, FIRST));
    }
}
