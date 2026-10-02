/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.entity.TrafficBucket;
import dev.chojo.ember.feature.traffic.repository.StationTrafficRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class StationTrafficRecorderTest extends RepositoryTestBase {

    private static Station station;
    private static Metrics metrics;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("RecorderStation");
        metrics = Mockito.mock(Metrics.class);
        Mockito.when(metrics.trafficEnabled()).thenReturn(true);
        Mockito.when(metrics.trafficRetentionDays()).thenReturn(90);
        Mockito.when(metrics.trafficFlushIntervalSeconds()).thenReturn(30);
    }

    private static StationTrafficRecorder newRecorder() {
        return new StationTrafficRecorder(stationTrafficRepo, metrics);
    }

    private static MovableClock clockAt(Instant hour) {
        return new MovableClock(hour.plus(10, ChronoUnit.MINUTES));
    }

    @AfterAll
    static void cleanupClass() {
        stationTrafficRepo.pruneBefore(Instant.now().plus(1, ChronoUnit.DAYS));
        stationRepo.delete(station.id());
    }

    @Test
    void recordAccumulatesInMemoryWithoutFlush() {
        var recorder = newRecorder();
        recorder.record(station.id(), AuthBucket.AUTHENTICATED, 100, 200);
        recorder.record(station.id(), AuthBucket.AUTHENTICATED, 50, 100);
        recorder.record(station.id(), AuthBucket.UNAUTHENTICATED, 10, 20);

        var snapshot = recorder.snapshot();
        assertEquals(2, snapshot.size());
        var auth = snapshot.stream()
                .filter(b -> b.auth() == AuthBucket.AUTHENTICATED)
                .findFirst()
                .orElseThrow();
        assertEquals(150, auth.ingressBytes());
        assertEquals(300, auth.egressBytes());
        assertEquals(2, auth.requests());

        var unauth = snapshot.stream()
                .filter(b -> b.auth() == AuthBucket.UNAUTHENTICATED)
                .findFirst()
                .orElseThrow();
        assertEquals(10, unauth.ingressBytes());
        assertEquals(20, unauth.egressBytes());
        assertEquals(1, unauth.requests());
    }

    @Test
    void recordWithNullStationGoesToInstanceBucket() {
        var recorder = newRecorder();
        recorder.record(null, AuthBucket.FEDERATION, 5, 5);
        assertTrue(
                recorder.snapshot().stream().anyMatch(b -> b.stationId() == null && b.auth() == AuthBucket.FEDERATION));
    }

    @Test
    void negativeBytesAreClampedToZero() {
        var fresh = newRecorder();
        fresh.record(station.id(), AuthBucket.AUTHENTICATED, -10, -20);
        var snapshot = fresh.snapshot();
        assertEquals(1, snapshot.size());
        assertEquals(0, snapshot.getFirst().ingressBytes());
        assertEquals(0, snapshot.getFirst().egressBytes());
        assertEquals(1, snapshot.getFirst().requests());
    }

    @Test
    void recordIsNoOpWhenDisabled() {
        var disabledMetrics = Mockito.mock(Metrics.class);
        Mockito.when(disabledMetrics.trafficEnabled()).thenReturn(false);
        var disabled = new StationTrafficRecorder(stationTrafficRepo, disabledMetrics);
        disabled.record(station.id(), AuthBucket.AUTHENTICATED, 100, 100);
        assertEquals(0, disabled.bufferedBucketCount());
    }

    @Test
    void tasksRunOnlyWhileEnabled() {
        var switchable = Mockito.mock(Metrics.class);
        Mockito.when(switchable.trafficFlushIntervalSeconds()).thenReturn(45);
        var repository = Mockito.mock(StationTrafficRepository.class);
        var rec = new StationTrafficRecorder(repository, switchable);
        var flush = rec.scheduledTasks().get(0);
        var prune = rec.scheduledTasks().get(1);

        flush.work().run();
        prune.work().run();
        Mockito.verifyNoInteractions(repository);

        Mockito.when(switchable.trafficEnabled()).thenReturn(true);
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 1, 1);
        flush.work().run();
        prune.work().run();
        Mockito.verify(repository).upsert(Mockito.any());
        Mockito.verify(repository).pruneBefore(Mockito.any());
        assertEquals(Duration.ofSeconds(45), flush.schedule().period());
    }

    @Test
    void flushSwallowsRepositoryExceptionsAndKeepsBucket() {
        var failingRepo = Mockito.mock(StationTrafficRepository.class);
        Mockito.doThrow(new RuntimeException("simulated db outage"))
                .when(failingRepo)
                .upsert(Mockito.any());
        var clock = clockAt(Instant.parse("2026-06-17T05:00:00Z"));
        var rec = new StationTrafficRecorder(failingRepo, metrics, clock);
        rec.record(1, AuthBucket.AUTHENTICATED, 1, 0);
        clock.advance(Duration.ofHours(1));
        assertDoesNotThrow(rec::flush);
        assertEquals(1, rec.bufferedBucketCount(), "Failed bucket should be retained for retry on next flush");
    }

    @Test
    void pruneDelegatesToRepositoryWithRetentionCutoff() {
        var rec = newRecorder();
        Instant ancient = Instant.now().minus(120, ChronoUnit.DAYS);
        stationTrafficRepo.upsert(new TrafficBucket(
                ancient.truncatedTo(ChronoUnit.HOURS), station.id(), AuthBucket.AUTHENTICATED, 1, 1, 1));
        rec.prune();
        var rows = stationTrafficRepo.findHourly(ancient.minus(1, ChronoUnit.HOURS), ancient, station.id(), null);
        assertTrue(rows.isEmpty(), "120-day-old row should have been pruned with default 90-day retention");
    }

    @Test
    void pruneSwallowsRepositoryExceptions() {
        var failingRepo = Mockito.mock(StationTrafficRepository.class);
        Mockito.when(failingRepo.pruneBefore(Mockito.any())).thenThrow(new RuntimeException("simulated db outage"));
        var rec = new StationTrafficRecorder(failingRepo, metrics);
        assertDoesNotThrow(rec::prune);
    }

    @Test
    void flushKeepsCurrentHourBucketsAndPersistsOldOnes() {
        var rec = newRecorder();
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 42, 42);
        rec.flush();
        assertEquals(1, rec.bufferedBucketCount(), "Current-hour bucket should still be in memory after flush");
    }

    @Test
    void flushUpsertsCurrentHourDeltaAndAvoidsDoubleCounting() {
        var repository = Mockito.mock(StationTrafficRepository.class);
        var rec = new StationTrafficRecorder(repository, metrics);
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 100, 200);
        rec.flush();
        Mockito.verify(repository, Mockito.times(1))
                .upsert(ArgumentMatchers.argThat(b -> b.ingressBytes() == 100 && b.egressBytes() == 200));

        rec.flushAll();
        Mockito.verifyNoMoreInteractions(repository);

        rec.record(station.id(), AuthBucket.AUTHENTICATED, 50, 75);
        rec.flushAll();
        Mockito.verify(repository, Mockito.times(1))
                .upsert(ArgumentMatchers.argThat(b -> b.ingressBytes() == 50 && b.egressBytes() == 75));
        assertEquals("station traffic", rec.name());
    }

    @Test
    void flushFailureLeavesLastFlushedUnchangedForRetry() {
        var failingRepo = Mockito.mock(StationTrafficRepository.class);
        Mockito.doThrow(new RuntimeException("simulated outage"))
                .doNothing()
                .when(failingRepo)
                .upsert(Mockito.any());

        var rec = new StationTrafficRecorder(failingRepo, metrics);
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 100, 200);
        assertDoesNotThrow(rec::flush);
        rec.flush();
        Mockito.verify(failingRepo, Mockito.times(2))
                .upsert(ArgumentMatchers.argThat(b -> b.ingressBytes() == 100 && b.egressBytes() == 200));
    }

    @Test
    void flushPersistsAgedBuckets() {
        Instant pastHour = Instant.parse("2026-06-17T05:00:00Z");
        var clock = clockAt(pastHour);
        var rec = new StationTrafficRecorder(stationTrafficRepo, metrics, clock);
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 5, 6);
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 6, 7);
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 0, 0);
        clock.advance(Duration.ofHours(1));

        rec.flush();

        var persisted = stationTrafficRepo.findHourly(pastHour, pastHour, station.id(), AuthBucket.AUTHENTICATED);
        assertEquals(1, persisted.size());
        assertEquals(11, persisted.getFirst().ingressBytes());
        assertEquals(13, persisted.getFirst().egressBytes());
        assertEquals(3, persisted.getFirst().requests());
        assertEquals(0, rec.bufferedBucketCount(), "Aged bucket should be removed from the in-memory map after flush");
    }

    @Test
    void trafficOfADeletedStationIsFoldedIntoTheInstanceBucket() {
        var doomed = stationRepo.create("DoomedTrafficStation");
        Instant hour = Instant.parse("2026-06-18T07:00:00Z");
        var rec = new StationTrafficRecorder(stationTrafficRepo, metrics, clockAt(hour));
        rec.record(doomed.id(), AuthBucket.UNAUTHENTICATED, 7, 9);
        stationRepo.delete(doomed.id());

        rec.flush();
        rec.record(doomed.id(), AuthBucket.UNAUTHENTICATED, 1, 1);

        var global = stationTrafficRepo.findGlobal(hour, hour, AuthBucket.UNAUTHENTICATED);
        assertEquals(1, global.size());
        assertEquals(7, global.getFirst().ingressBytes());
        assertTrue(
                rec.snapshot().stream().anyMatch(b -> b.stationId() == null && b.ingressBytes() == 1),
                "later traffic of the deleted station goes straight to the instance bucket");
    }
}
