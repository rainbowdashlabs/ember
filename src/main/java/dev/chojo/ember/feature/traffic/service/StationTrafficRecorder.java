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
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.ShutdownFlush;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.HourlyCounters;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory accumulator for per-station traffic counters. Records ingress and egress byte counts plus
 * request counts in hourly buckets, keyed by {@code (hour, stationId, auth)}, and writes them in deltas
 * into {@code station_traffic_hourly} via {@link StationTrafficRepository}.
 *
 * <p>The recorder is intentionally non-blocking on the request path: {@link #record} only touches the
 * {@link HourlyCounters}. Every flush writes the current hour's delta too, so the dashboard sees
 * near-real-time data, and the shutdown flush writes whatever arrived since the last one.
 *
 * <p>Retention is enforced by a second task: every six hours it deletes buckets older than
 * {@link Metrics#trafficRetentionDays()}.
 */
@Singleton
public class StationTrafficRecorder implements ShutdownFlush, TaskSource {
    private static final Logger log = LoggerFactory.getLogger(StationTrafficRecorder.class);
    private static final Duration PRUNE_INTERVAL = Duration.ofHours(6);
    /** PostgreSQL SQLSTATE for {@code foreign_key_violation}. */
    private static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";

    private final HourlyCounters<StationKey> counters;
    /**
     * Station ids whose {@code INSERT} into {@code station_traffic_hourly} has been rejected by the
     * foreign-key check at least once during this JVM run - the row no longer exists in
     * {@code station} so further attempts to charge that id would just re-trigger the same FK
     * violation. {@link #record} routes hits for these ids straight to the instance-global bucket.
     * The set is intentionally not bounded: a deleted station never reappears with the same internal
     * id (a fresh insert receives a new {@code SERIAL}), so the membership cost is one entry per
     * historical deletion.
     */
    private final Set<Integer> knownMissingStations = ConcurrentHashMap.newKeySet();

    private final StationTrafficRepository repository;
    private final Metrics metrics;
    private final Clock clock;

    @Inject
    public StationTrafficRecorder(StationTrafficRepository repository, Metrics metrics) {
        this(repository, metrics, Clock.systemUTC());
    }

    StationTrafficRecorder(StationTrafficRepository repository, Metrics metrics, Clock clock) {
        this.repository = repository;
        this.metrics = metrics;
        this.clock = clock;
        this.counters = new HourlyCounters<>("station traffic", 3, clock);
    }

    /**
     * Adds one request's ingress and egress byte counts to the appropriate bucket.
     * Non-blocking - safe to call from the after-handler.
     *
     * @param stationId    internal station id, or {@code null} for instance-global traffic
     * @param auth         classification of the request
     * @param ingressBytes inbound byte estimate (request headers + body)
     * @param egressBytes  outbound byte estimate (response headers + body)
     */
    public void record(Integer stationId, AuthBucket auth, long ingressBytes, long egressBytes) {
        if (!metrics.trafficEnabled()) return;
        Integer chargeStation = stationId != null && knownMissingStations.contains(stationId) ? null : stationId;
        counters.add(new StationKey(chargeStation, auth), Math.max(0, ingressBytes), Math.max(0, egressBytes), 1);
    }

    /**
     * Writes the delta of every bucket, the current hour included. A bucket whose station has been
     * deleted in the meantime is folded into the instance-global bucket instead.
     */
    public void flush() {
        counters.flush(true, this::write);
    }

    @Override
    public String name() {
        return "station traffic";
    }

    @Override
    public void flushAll() {
        flush();
    }

    private void write(Instant hour, StationKey key, long[] delta) {
        try {
            repository.upsert(new TrafficBucket(hour, key.stationId(), key.auth(), delta[0], delta[1], delta[2]));
        } catch (RuntimeException e) {
            if (!isMissingStationFk(e)) throw e;
            log.warn(
                    "Dropping traffic of station {} at {} - the station no longer exists; folding it into the instance-global bucket",
                    key.stationId(),
                    hour);
            knownMissingStations.add(key.stationId());
            repository.upsert(new TrafficBucket(hour, null, key.auth(), delta[0], delta[1], delta[2]));
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
        return counters.size();
    }

    /**
     * Returns the in-memory snapshot of every bucket currently buffered, with everything counted in it
     * so far. Used by tests; the list is a defensive snapshot, not a view.
     */
    public List<TrafficBucket> snapshot() {
        return counters.snapshot().stream()
                .map(bucket -> new TrafficBucket(
                        bucket.hour(),
                        bucket.key().stationId(),
                        bucket.key().auth(),
                        bucket.totals()[0],
                        bucket.totals()[1],
                        bucket.totals()[2]))
                .toList();
    }

    void prune() {
        try {
            int days = Math.max(1, metrics.trafficRetentionDays());
            Instant cutoff = clock.instant().minus(Duration.ofDays(days));
            int removed = repository.pruneBefore(cutoff);
            if (removed > 0) {
                log.info("Pruned {} expired traffic buckets older than {} days", removed, days);
            }
        } catch (Exception e) {
            log.warn("Failed to prune expired traffic buckets", e);
        }
    }

    private record StationKey(Integer stationId, AuthBucket auth) {}

    @Override
    public List<ScheduledTask> scheduledTasks() {
        var flushInterval = Duration.ofSeconds(Math.max(1, metrics.trafficFlushIntervalSeconds()));
        return List.of(
                new ScheduledTask("station-traffic-flush", Schedule.fixedRate(flushInterval, flushInterval), () -> {
                    if (metrics.trafficEnabled()) flush();
                }),
                new ScheduledTask(
                        "station-traffic-prune", Schedule.fixedRate(Duration.ofHours(1), PRUNE_INTERVAL), () -> {
                            if (metrics.trafficEnabled()) prune();
                        }));
    }
}
