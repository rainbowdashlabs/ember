/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.entity.TrafficBucket;
import dev.chojo.ember.feature.traffic.repository.StationTrafficRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory accumulator and async flusher for per-station traffic counters. Records ingress
 * and egress byte counts plus request counts in hourly buckets, keyed by
 * {@code (hour, stationId, auth)}, and flushes them on a fixed cadence into
 * {@code station_traffic_hourly} via {@link StationTrafficRepository}.
 *
 * <p>The recorder is intentionally non-blocking on the request path: {@link #record} only
 * touches a {@link ConcurrentHashMap} and three {@link AtomicLong} counters. The DB upsert
 * happens on a dedicated single-threaded scheduler so request latency is never affected.
 *
 * <p>Retention is enforced by the same scheduler: every six hours it deletes buckets older
 * than {@link Metrics#trafficRetentionDays()}.
 */
@Singleton
public class StationTrafficRecorder {
    private static final Logger log = LoggerFactory.getLogger(StationTrafficRecorder.class);
    private static final long PRUNE_INTERVAL_HOURS = 6;
    /** PostgreSQL SQLSTATE for {@code foreign_key_violation}. */
    private static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";

    private final ConcurrentHashMap<BucketKey, TrafficAccumulator> buckets = new ConcurrentHashMap<>();
    /**
     * Station ids whose {@code INSERT} into {@code station_traffic_hourly} has been rejected by the
     * foreign-key check at least once during this JVM run — the row no longer exists in
     * {@code station} so further attempts to charge that id would just re-trigger the same FK
     * violation. {@link #record} routes hits for these ids straight to the instance-global bucket.
     * The set is intentionally not bounded: a deleted station never reappears with the same internal
     * id (a fresh insert receives a new {@code SERIAL}), so the membership cost is one entry per
     * historical deletion.
     */
    private final Set<Integer> knownMissingStations = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "station-traffic-recorder");
        t.setDaemon(true);
        return t;
    });
    private final StationTrafficRepository repository;
    private final Metrics metrics;

    @Inject
    public StationTrafficRecorder(StationTrafficRepository repository, Metrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    /**
     * Schedules the flush and prune tasks. Idempotent — call once at boot from
     * {@code ApiServer}.
     */
    public void start() {
        if (!metrics.trafficEnabled()) {
            log.info("Station traffic recording disabled by config; recorder will accept calls but never flush.");
            return;
        }
        long flushSeconds = Math.max(1, metrics.trafficFlushIntervalSeconds());
        executor.scheduleAtFixedRate(this::flush, flushSeconds, flushSeconds, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(this::prune, 1, PRUNE_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Adds one request's ingress and egress byte counts to the appropriate bucket.
     * Non-blocking — safe to call from the after-handler.
     *
     * @param stationId    internal station id, or {@code null} for instance-global traffic
     * @param auth         classification of the request
     * @param ingressBytes inbound byte estimate (request headers + body)
     * @param egressBytes  outbound byte estimate (response headers + body)
     */
    public void record(Integer stationId, AuthBucket auth, long ingressBytes, long egressBytes) {
        if (!metrics.trafficEnabled()) return;
        Integer chargeStation = stationId != null && knownMissingStations.contains(stationId) ? null : stationId;
        var key = new BucketKey(currentHour(), chargeStation, auth);
        var acc = buckets.computeIfAbsent(key, k -> new TrafficAccumulator());
        acc.ingress.addAndGet(Math.max(0, ingressBytes));
        acc.egress.addAndGet(Math.max(0, egressBytes));
        acc.requests.incrementAndGet();
    }

    /**
     * Persists deltas for every bucket on every tick, so the dashboard sees near-real-time
     * data instead of waiting for the hour to roll over. Each {@link TrafficAccumulator}
     * remembers what was already upserted; only the {@code current - lastFlushed} delta is
     * written each cycle, so the same hits are never counted twice no matter how often the
     * flusher runs.
     *
     * <p>Past-hour buckets are removed from the in-memory map after their final delta lands;
     * current-hour buckets stay so further requests in the same hour keep aggregating without
     * an UPSERT per request.
     *
     * <p>Visible for tests / forced flush from {@code stop()}.
     */
    public void flush() {
        Instant currentHour = currentHour();
        for (var entry : buckets.entrySet()) {
            BucketKey key = entry.getKey();
            TrafficAccumulator acc = entry.getValue();
            long ingress = acc.ingress.get();
            long egress = acc.egress.get();
            long requests = acc.requests.get();
            long deltaIngress = ingress - acc.lastFlushedIngress;
            long deltaEgress = egress - acc.lastFlushedEgress;
            long deltaRequests = requests - acc.lastFlushedRequests;
            boolean pastHour = !key.hour.equals(currentHour);
            if (deltaIngress == 0 && deltaEgress == 0 && deltaRequests == 0) {
                if (pastHour) buckets.remove(key, acc);
                continue;
            }
            try {
                repository.upsert(
                        new TrafficBucket(key.hour, key.stationId, key.auth, deltaIngress, deltaEgress, deltaRequests));
                acc.lastFlushedIngress = ingress;
                acc.lastFlushedEgress = egress;
                acc.lastFlushedRequests = requests;
                if (pastHour) buckets.remove(key, acc);
            } catch (Exception e) {
                if (isMissingStationFk(e)) {
                    log.warn(
                            "Dropping traffic bucket {} — referenced station no longer exists; folding delta into the instance-global bucket",
                            key);
                    knownMissingStations.add(key.stationId);
                    buckets.remove(key, acc);
                    try {
                        repository.upsert(
                                new TrafficBucket(key.hour, null, key.auth, deltaIngress, deltaEgress, deltaRequests));
                    } catch (Exception inner) {
                        log.warn("Failed to fold dropped bucket {} into the global bucket", key, inner);
                    }
                } else {
                    log.warn("Failed to flush traffic bucket {} — will retry on next tick", key, e);
                }
            }
        }
    }

    private static boolean isMissingStationFk(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof PSQLException psql && SQLSTATE_FOREIGN_KEY_VIOLATION.equals(psql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Returns the number of in-memory accumulators currently buffered. Useful for tests
     * and operational metrics.
     */
    public int bufferedBucketCount() {
        return buckets.size();
    }

    /**
     * Returns the in-memory snapshot of every bucket currently buffered. Buckets whose
     * hour matches the current one are still being filled and are not yet on disk. Used by
     * tests and a forthcoming live-debug endpoint; the map is a defensive snapshot, not a
     * view.
     */
    public List<TrafficBucket> snapshot() {
        var out = new ArrayList<TrafficBucket>(buckets.size());
        for (var entry : buckets.entrySet()) {
            BucketKey key = entry.getKey();
            TrafficAccumulator acc = entry.getValue();
            out.add(new TrafficBucket(
                    key.hour, key.stationId, key.auth, acc.ingress.get(), acc.egress.get(), acc.requests.get()));
        }
        return out;
    }

    private void prune() {
        try {
            int days = Math.max(1, metrics.trafficRetentionDays());
            Instant cutoff = Instant.now().minus(Duration.ofDays(days));
            int removed = repository.pruneBefore(cutoff);
            if (removed > 0) {
                log.info("Pruned {} expired traffic buckets older than {} days", removed, days);
            }
        } catch (Exception e) {
            log.warn("Failed to prune expired traffic buckets", e);
        }
    }

    private Instant currentHour() {
        return Instant.now().truncatedTo(ChronoUnit.HOURS);
    }

    private record BucketKey(Instant hour, Integer stationId, AuthBucket auth) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BucketKey(Instant hour1, Integer id, AuthBucket auth1))) return false;
            return hour.equals(hour1) && Objects.equals(stationId, id) && auth == auth1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hour, stationId, auth);
        }
    }

    private static final class TrafficAccumulator {
        final AtomicLong ingress = new AtomicLong();
        final AtomicLong egress = new AtomicLong();
        final AtomicLong requests = new AtomicLong();

        /**
         * Cumulative values already written to the DB by the most recent flush. Mutated only
         * from the single-threaded flusher, so no atomicity is required.
         */
        long lastFlushedIngress = 0;

        long lastFlushedEgress = 0;
        long lastFlushedRequests = 0;
    }
}
