/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.service.StationTrafficRecorder;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
        Mockito.when(metrics.trafficEnabled()).thenReturn(true);
        var fresh = new StationTrafficRecorder(stationTrafficRepo, metrics);
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
        disabled.start();
        assertEquals(0, disabled.bufferedBucketCount());
    }

    @Test
    void startSchedulesFlushAndPruneWhenEnabled() {
        var enabledMetrics = Mockito.mock(Metrics.class);
        Mockito.when(enabledMetrics.trafficEnabled()).thenReturn(true);
        Mockito.when(enabledMetrics.trafficRetentionDays()).thenReturn(7);
        Mockito.when(enabledMetrics.trafficFlushIntervalSeconds()).thenReturn(3600);
        var rec = new StationTrafficRecorder(stationTrafficRepo, enabledMetrics);
        assertDoesNotThrow(rec::start);
    }

    @Test
    void flushSwallowsRepositoryExceptionsAndKeepsBucket() {
        var failingRepo = Mockito.mock(dev.chojo.ember.feature.traffic.repository.StationTrafficRepository.class);
        Mockito.doThrow(new RuntimeException("simulated db outage"))
                .when(failingRepo)
                .upsert(Mockito.any());
        var rec = new StationTrafficRecorder(failingRepo, metrics);
        seedAgedBucket(rec, Instant.parse("2026-06-17T05:00:00Z"));
        assertDoesNotThrow(rec::flush);
        assertEquals(1, rec.bufferedBucketCount(), "Failed bucket should be retained for retry on next flush");
    }

    @Test
    void pruneDelegatesToRepositoryWithRetentionCutoff() throws Exception {
        var rec = new StationTrafficRecorder(stationTrafficRepo, metrics);
        Instant ancient = Instant.now().minus(120, ChronoUnit.DAYS);
        stationTrafficRepo.upsert(new dev.chojo.ember.feature.traffic.entity.TrafficBucket(
                ancient.truncatedTo(ChronoUnit.HOURS), station.id(), AuthBucket.AUTHENTICATED, 1, 1, 1));
        invokePrune(rec);
        var rows = stationTrafficRepo.findHourly(ancient.minus(1, ChronoUnit.HOURS), ancient, station.id(), null);
        assertTrue(rows.isEmpty(), "120-day-old row should have been pruned with default 90-day retention");
    }

    @Test
    void pruneSwallowsRepositoryExceptions() throws Exception {
        var failingRepo = Mockito.mock(dev.chojo.ember.feature.traffic.repository.StationTrafficRepository.class);
        Mockito.when(failingRepo.pruneBefore(Mockito.any())).thenThrow(new RuntimeException("simulated db outage"));
        var rec = new StationTrafficRecorder(failingRepo, metrics);
        assertDoesNotThrow(() -> invokePrune(rec));
    }

    private static void seedAgedBucket(StationTrafficRecorder rec, Instant agedHour) {
        try {
            var bucketsField = StationTrafficRecorder.class.getDeclaredField("buckets");
            bucketsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.concurrent.ConcurrentHashMap<Object, Object>) bucketsField.get(rec);

            var accClass =
                    Class.forName("dev.chojo.ember.feature.traffic.service.StationTrafficRecorder$TrafficAccumulator");
            var keyClass = Class.forName("dev.chojo.ember.feature.traffic.service.StationTrafficRecorder$BucketKey");
            var accCtor = accClass.getDeclaredConstructor();
            accCtor.setAccessible(true);
            var keyCtor = keyClass.getDeclaredConstructor(Instant.class, Integer.class, AuthBucket.class);
            keyCtor.setAccessible(true);

            map.put(keyCtor.newInstance(agedHour, 1, AuthBucket.AUTHENTICATED), accCtor.newInstance());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void invokePrune(StationTrafficRecorder rec) throws Exception {
        var m = StationTrafficRecorder.class.getDeclaredMethod("prune");
        m.setAccessible(true);
        m.invoke(rec);
    }

    @Test
    void flushKeepsCurrentHourBucketsAndPersistsOldOnes() {
        var rec = new StationTrafficRecorder(stationTrafficRepo, metrics);
        rec.record(station.id(), AuthBucket.AUTHENTICATED, 42, 42);
        rec.flush();
        assertEquals(1, rec.bufferedBucketCount(), "Current-hour bucket should still be in memory after flush");
    }

    @Test
    void flushPersistsAgedBuckets() {
        var rec = new StationTrafficRecorder(stationTrafficRepo, metrics);
        Instant pastHour = Instant.parse("2026-06-17T05:00:00Z");
        var keyField = privateBucketsField(rec);
        try {
            @SuppressWarnings("unchecked")
            var map = (java.util.concurrent.ConcurrentHashMap<Object, Object>) keyField.get(rec);
            map.clear();
            rec.record(station.id(), AuthBucket.AUTHENTICATED, 1, 1);
            var current = rec.snapshot().getFirst();
            rec.record(station.id(), AuthBucket.AUTHENTICATED, 5, 7);
            assertNotNull(current);

            Object accClass =
                    Class.forName("dev.chojo.ember.feature.traffic.service.StationTrafficRecorder$TrafficAccumulator");
            Object keyClass = Class.forName("dev.chojo.ember.feature.traffic.service.StationTrafficRecorder$BucketKey");
            var accCtor = ((Class<?>) accClass).getDeclaredConstructor();
            accCtor.setAccessible(true);
            var keyCtor = ((Class<?>) keyClass).getDeclaredConstructor(Instant.class, Integer.class, AuthBucket.class);
            keyCtor.setAccessible(true);

            Object acc = accCtor.newInstance();
            var ingress = acc.getClass().getDeclaredField("ingress");
            ingress.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) ingress.get(acc)).set(11);
            var egress = acc.getClass().getDeclaredField("egress");
            egress.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) egress.get(acc)).set(13);
            var requests = acc.getClass().getDeclaredField("requests");
            requests.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) requests.get(acc)).set(3);

            Object key = keyCtor.newInstance(pastHour, station.id(), AuthBucket.AUTHENTICATED);
            map.put(key, acc);

            rec.flush();

            var persisted = stationTrafficRepo.findHourly(pastHour, pastHour, station.id(), AuthBucket.AUTHENTICATED);
            assertEquals(1, persisted.size());
            assertEquals(11, persisted.getFirst().ingressBytes());
            assertEquals(13, persisted.getFirst().egressBytes());
            assertEquals(3, persisted.getFirst().requests());
            assertFalse(map.containsKey(key), "Aged bucket should be removed from the in-memory map after flush");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Reflection setup failed", e);
        }
    }

    private static java.lang.reflect.Field privateBucketsField(StationTrafficRecorder rec) {
        try {
            var field = StationTrafficRecorder.class.getDeclaredField("buckets");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }
}
