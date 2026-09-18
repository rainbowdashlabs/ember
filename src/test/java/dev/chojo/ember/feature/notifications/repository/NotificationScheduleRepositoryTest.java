/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a station and a cluster asked to be written to.
 *
 * <p>Two columns and a moment, kept apart from the station's own record because only the sweep and
 * one settings screen ever read them.
 */
class NotificationScheduleRepositoryTest extends RepositoryTestBase {

    private static NotificationScheduleRepository repository;
    private static Station station;
    private static Cluster cluster;

    @BeforeAll
    static void setup() {
        repository = new NotificationScheduleRepository();
        station = stationRepo.create("ScheduleStation");
        cluster = clusterRepo.create("ScheduleCluster", "for the schedule", station.id());
    }

    /** A station that has asked for nothing says so, rather than saying it asked for no times. */
    @Test
    void aStationThatHasAskedForNothingHasNothing() {
        var schedule = repository.forStation(station.id()).orElseThrow();

        assertTrue(schedule.sendTimes().isEmpty());
        assertNull(schedule.lastSent());
        assertEquals("Europe/Berlin", schedule.timezone());
    }

    @Test
    void theTimesAStationAsksForComeBackAsItAskedForThem() {
        repository.setStationSendTimes(station.id(), List.of(LocalTime.of(14, 0), LocalTime.of(7, 0)));

        var schedule = repository.forStation(station.id()).orElseThrow();

        assertEquals(List.of(LocalTime.of(7, 0), LocalTime.of(14, 0)), schedule.sendTimes());
    }

    /** Asking for nothing again puts a station back where it started, on the operator's number. */
    @Test
    void theTimesCanBeGivenBack() {
        repository.setStationSendTimes(station.id(), List.of(LocalTime.of(9, 0)));
        repository.setStationSendTimes(station.id(), List.of());

        assertTrue(repository.forStation(station.id()).orElseThrow().sendTimes().isEmpty());
    }

    @Test
    void whenAStationWasLastWrittenToIsRemembered() {
        var when = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.markStationSent(station.id(), when);

        assertEquals(when, repository.forStation(station.id()).orElseThrow().lastSent());
    }

    /** A cluster keeps the same two things the same way, and is read by the same sweep. */
    @Test
    void aClusterKeepsItsOwnTimesAndItsOwnMoment() {
        repository.setClusterSendTimes(cluster.id(), List.of(LocalTime.of(6, 30)));
        var when = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.markClusterSent(cluster.id(), when);

        var schedule = repository.forCluster(cluster.id()).orElseThrow();

        assertEquals(List.of(LocalTime.of(6, 30)), schedule.sendTimes());
        assertEquals(when, schedule.lastSent());
    }

    /** Handing back nothing at all means the same as handing back an empty list. */
    @Test
    void handingBackNothingIsTheSameAsHandingBackNoTimes() {
        repository.setStationSendTimes(station.id(), List.of(LocalTime.of(5, 0)));
        repository.setStationSendTimes(station.id(), null);

        assertTrue(repository.forStation(station.id()).orElseThrow().sendTimes().isEmpty());
    }

    /** The same time asked for twice is one time, and the order asked in does not matter. */
    @Test
    void theTimesAreTidiedOnTheWayIn() {
        repository.setStationSendTimes(
                station.id(), List.of(LocalTime.of(14, 0), LocalTime.of(7, 0), LocalTime.of(14, 0)));

        assertEquals(
                List.of(LocalTime.of(7, 0), LocalTime.of(14, 0)),
                repository.forStation(station.id()).orElseThrow().sendTimes());
    }

    @Test
    void somethingThatIsNotThereHasNoSchedule() {
        assertTrue(repository.forStation(999999).isEmpty());
        assertTrue(repository.forCluster(999999).isEmpty());
    }
}
