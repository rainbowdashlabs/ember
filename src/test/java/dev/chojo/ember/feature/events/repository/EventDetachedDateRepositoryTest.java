/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventDetachedDate;
import dev.chojo.ember.feature.events.entity.StationEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dates a series gave away to appointments of their own. */
class EventDetachedDateRepositoryTest extends RepositoryTestBase {
    private static final LocalDate FIRST = LocalDate.parse("2027-05-05");
    private static final LocalDate SECOND = LocalDate.parse("2027-05-12");

    private static Station station;
    private static Station otherStation;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Detached Dates Station");
        otherStation = stationRepo.create("Other Detached Dates Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
    }

    private static StationEvent weekly(Station owner) {
        return eventRepo.create(
                owner.id(),
                "Weekly",
                null,
                StationEvent.EventType.RECURRING,
                3,
                Instant.parse("2027-01-06T18:00:00Z"),
                Instant.parse("2027-01-06T20:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void aDateIsTakenOutOnceAndReadBackPerEventAndStation() {
        var event = weekly(station);
        var elsewhere = weekly(otherStation);

        assertTrue(eventDetachedDateRepo.detach(event.id(), SECOND));
        assertTrue(eventDetachedDateRepo.detach(event.id(), FIRST));
        assertFalse(eventDetachedDateRepo.detach(event.id(), FIRST), "a date taken out stays taken out once");
        eventDetachedDateRepo.detach(elsewhere.id(), FIRST);

        assertEquals(
                List.of(new EventDetachedDate(event.id(), FIRST), new EventDetachedDate(event.id(), SECOND)),
                eventDetachedDateRepo.findByEvent(event.id()));
        assertEquals(2, eventDetachedDateRepo.findByStation(station.id()).size());
        assertEquals(
                List.of(new EventDetachedDate(elsewhere.id(), FIRST)),
                eventDetachedDateRepo.findByStation(otherStation.id()));
    }

    @Test
    void theDatesGoWithTheSeries() {
        var event = weekly(station);
        eventDetachedDateRepo.detach(event.id(), FIRST);

        eventRepo.delete(event.id());

        assertTrue(eventDetachedDateRepo.findByEvent(event.id()).isEmpty());
    }
}
