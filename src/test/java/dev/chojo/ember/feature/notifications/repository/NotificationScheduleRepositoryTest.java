/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.notifications.entity.DigestGroup;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        var schedule = stationGroup();

        assertTrue(schedule.sendTimes().isEmpty());
        assertNull(schedule.lastSent());
        assertEquals("Europe/Berlin", schedule.zone().getId());
        assertEquals(station.name(), schedule.name());
        assertEquals(station.uid(), schedule.stationUid());
    }

    @Test
    void theTimesAStationAsksForComeBackAsItAskedForThem() {
        repository.setStationSendTimes(station.id(), List.of(LocalTime.of(14, 0), LocalTime.of(7, 0)));

        var schedule = stationGroup();

        assertEquals(List.of(LocalTime.of(7, 0), LocalTime.of(14, 0)), schedule.sendTimes());
    }

    /** Asking for nothing again puts a station back where it started, on the operator's number. */
    @Test
    void theTimesCanBeGivenBack() {
        repository.setStationSendTimes(station.id(), List.of(LocalTime.of(9, 0)));
        repository.setStationSendTimes(station.id(), List.of());

        assertTrue(stationGroup().sendTimes().isEmpty());
    }

    @Test
    void whenAStationWasLastWrittenToIsRemembered() {
        var when = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.markStationSent(station.id(), when);

        assertEquals(when, stationGroup().lastSent());
    }

    /** A cluster keeps the same two things the same way, and is read by the same sweep. */
    @Test
    void aClusterKeepsItsOwnTimesAndItsOwnMoment() {
        repository.setClusterSendTimes(cluster.id(), List.of(LocalTime.of(6, 30)));
        var when = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.markClusterSent(cluster.id(), when);

        var schedule =
                repository.findDigestGroups(List.of(), List.of(cluster.id())).getFirst();

        assertEquals(List.of(LocalTime.of(6, 30)), schedule.sendTimes());
        assertEquals(when, schedule.lastSent());
        assertEquals(new DigestGroup.Key(DigestGroup.Kind.CLUSTER, cluster.id()), schedule.key());
        assertNull(schedule.stationUid(), "a cluster's links name no station");
    }

    /**
     * A cluster reads its times on its home station's clock and writes in its language, so asking for
     * seven in the morning in Berlin is seven in Berlin, summer and winter.
     */
    @Test
    void aClusterKeepsTheClockAndLanguageOfItsHomeStation() {
        var home = stationRepo.create("Kreisverband Zuhause");
        stationRepo.updateTimezone(home.id(), "Europe/Berlin");
        stationRepo.updateLocale(home.id(), "de-DE");
        var berlin = clusterRepo.create("Kreisverband Berlin", "keeps its home's clock", home.id());
        repository.setClusterSendTimes(berlin.id(), List.of(LocalTime.of(7, 0)));
        try {
            var group =
                    repository.findDigestGroups(List.of(), List.of(berlin.id())).getFirst();

            assertEquals("Europe/Berlin", group.zone().getId());
            assertEquals("de-DE", group.locale());
            var july = Instant.parse("2026-07-01T05:00:00Z");
            var january = Instant.parse("2026-01-15T06:00:00Z");
            var floor = java.time.Duration.ZERO;
            assertTrue(group.isDue(july.minusSeconds(3600), floor, july));
            assertFalse(group.isDue(july.minusSeconds(3600), floor, july.minusSeconds(60)));
            assertTrue(group.isDue(january.minusSeconds(3600), floor, january));
            assertFalse(group.isDue(january.minusSeconds(3600), floor, january.minusSeconds(60)));
        } finally {
            clusterRepo.delete(berlin.id());
            stationRepo.delete(home.id());
        }
    }

    /** Handing back nothing at all means the same as handing back an empty list. */
    @Test
    void handingBackNothingIsTheSameAsHandingBackNoTimes() {
        repository.setStationSendTimes(station.id(), List.of(LocalTime.of(5, 0)));
        repository.setStationSendTimes(station.id(), null);

        assertTrue(stationGroup().sendTimes().isEmpty());
    }

    /** The same time asked for twice is one time, and the order asked in does not matter. */
    @Test
    void theTimesAreTidiedOnTheWayIn() {
        repository.setStationSendTimes(
                station.id(), List.of(LocalTime.of(14, 0), LocalTime.of(7, 0), LocalTime.of(14, 0)));

        assertEquals(
                List.of(LocalTime.of(7, 0), LocalTime.of(14, 0)), stationGroup().sendTimes());
    }

    @Test
    void somethingThatIsNotThereHasNoSchedule() {
        assertTrue(repository.findDigestGroups(List.of(999999), List.of(999999)).isEmpty());
    }

    private static DigestGroup stationGroup() {
        return repository.findDigestGroups(List.of(station.id()), List.of()).getFirst();
    }
}
