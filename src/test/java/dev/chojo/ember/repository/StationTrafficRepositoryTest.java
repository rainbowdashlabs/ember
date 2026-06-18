/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.entity.TrafficBucket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationTrafficRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static final Instant HOUR = Instant.parse("2026-06-18T10:00:00Z").truncatedTo(ChronoUnit.HOURS);
    private static final Instant HOUR_LATER = HOUR.plus(1, ChronoUnit.HOURS);

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("TrafficStation");
    }

    @AfterAll
    static void cleanupClass() {
        stationTrafficRepo.pruneBefore(Instant.now().plus(1, ChronoUnit.DAYS));
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void upsertStationBucketAccumulates() {
        stationTrafficRepo.upsert(new TrafficBucket(HOUR, station.id(), AuthBucket.AUTHENTICATED, 100, 200, 1));
        stationTrafficRepo.upsert(new TrafficBucket(HOUR, station.id(), AuthBucket.AUTHENTICATED, 50, 75, 1));

        var rows = stationTrafficRepo.findHourly(HOUR, HOUR, station.id(), AuthBucket.AUTHENTICATED);
        assertEquals(1, rows.size());
        var only = rows.getFirst();
        assertEquals(150, only.ingressBytes());
        assertEquals(275, only.egressBytes());
        assertEquals(2, only.requests());
        assertEquals(station.id(), only.stationId());
    }

    @Test
    @Order(2)
    void upsertGlobalBucketAccumulates() {
        stationTrafficRepo.upsert(new TrafficBucket(HOUR, null, AuthBucket.UNAUTHENTICATED, 10, 30, 1));
        stationTrafficRepo.upsert(new TrafficBucket(HOUR, null, AuthBucket.UNAUTHENTICATED, 5, 25, 1));

        var rows = stationTrafficRepo.findGlobal(HOUR, HOUR, AuthBucket.UNAUTHENTICATED);
        assertEquals(1, rows.size());
        var only = rows.getFirst();
        assertEquals(15, only.ingressBytes());
        assertEquals(55, only.egressBytes());
        assertEquals(2, only.requests());
        assertNull(only.stationId());
    }

    @Test
    @Order(3)
    void findHourlyWithoutFiltersReturnsBothStationsAndAuthBuckets() {
        stationTrafficRepo.upsert(new TrafficBucket(HOUR, station.id(), AuthBucket.FEDERATION, 1, 1, 1));

        var rows = stationTrafficRepo.findHourly(HOUR, HOUR, null, null);
        assertTrue(rows.stream().anyMatch(r -> r.auth() == AuthBucket.AUTHENTICATED));
        assertTrue(rows.stream().anyMatch(r -> r.auth() == AuthBucket.FEDERATION));
    }

    @Test
    @Order(4)
    void findHourlyFiltersByStation() {
        var other = stationRepo.create("OtherTrafficStation");
        try {
            stationTrafficRepo.upsert(new TrafficBucket(HOUR, other.id(), AuthBucket.AUTHENTICATED, 999, 999, 1));
            var stationOnly = stationTrafficRepo.findHourly(HOUR, HOUR, station.id(), null);
            assertTrue(stationOnly.stream().allMatch(r -> r.stationId() != null && r.stationId() == station.id()));
            var otherOnly = stationTrafficRepo.findHourly(HOUR, HOUR, other.id(), AuthBucket.AUTHENTICATED);
            assertEquals(1, otherOnly.size());
            assertEquals(999, otherOnly.getFirst().ingressBytes());
        } finally {
            stationRepo.delete(other.id());
        }
    }

    @Test
    @Order(5)
    void findGlobalRespectsAuthFilter() {
        stationTrafficRepo.upsert(new TrafficBucket(HOUR, null, AuthBucket.FEDERATION, 7, 9, 1));
        var feds = stationTrafficRepo.findGlobal(HOUR, HOUR, AuthBucket.FEDERATION);
        assertEquals(1, feds.size());
        assertEquals(7, feds.getFirst().ingressBytes());

        var all = stationTrafficRepo.findGlobal(HOUR, HOUR, null);
        assertTrue(all.stream().anyMatch(r -> r.auth() == AuthBucket.UNAUTHENTICATED));
        assertTrue(all.stream().anyMatch(r -> r.auth() == AuthBucket.FEDERATION));
        assertTrue(all.stream().allMatch(r -> r.stationId() == null));
    }

    @Test
    @Order(6)
    void findHourlyOrdersByHour() {
        stationTrafficRepo.upsert(new TrafficBucket(HOUR_LATER, station.id(), AuthBucket.AUTHENTICATED, 1, 1, 1));
        var rows = stationTrafficRepo.findHourly(HOUR, HOUR_LATER, station.id(), AuthBucket.AUTHENTICATED);
        assertTrue(rows.size() >= 2);
        assertTrue(rows.get(0).hour().isBefore(rows.get(1).hour())
                || rows.get(0).hour().equals(rows.get(1).hour()));
    }

    @Test
    @Order(7)
    void findHourlyWindowExcludesOutOfRange() {
        var rows = stationTrafficRepo.findHourly(
                HOUR_LATER.plus(1, ChronoUnit.HOURS), HOUR_LATER.plus(2, ChronoUnit.HOURS), station.id(), null);
        assertTrue(rows.isEmpty());
    }

    @Test
    @Order(8)
    void pruneRemovesOldRows() {
        Instant ancient = HOUR.minus(60, ChronoUnit.DAYS);
        stationTrafficRepo.upsert(new TrafficBucket(ancient, station.id(), AuthBucket.AUTHENTICATED, 1, 1, 1));
        assertEquals(
                1,
                stationTrafficRepo
                        .findHourly(ancient, ancient, station.id(), null)
                        .size());

        int removed = stationTrafficRepo.pruneBefore(ancient.plus(1, ChronoUnit.HOURS));
        assertTrue(removed >= 1);
        assertTrue(stationTrafficRepo
                .findHourly(ancient, ancient, station.id(), null)
                .isEmpty());
    }
}
