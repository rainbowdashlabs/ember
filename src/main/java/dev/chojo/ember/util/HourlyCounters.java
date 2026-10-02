/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Additive counters in buckets of one hour and one key, filled without blocking and flushed as deltas.
 *
 * <p>A flush hands the writer only what was added since that bucket's last successful write, so an adding
 * upsert never counts twice; a failed write keeps its delta for the next flush. Past hours leave memory once
 * written, the current hour stays so counts keep aggregating.
 *
 * @param <K> what a bucket is counted for within its hour
 */
public final class HourlyCounters<K> {
    private static final Logger log = LoggerFactory.getLogger(HourlyCounters.class);

    private final String name;
    private final int width;
    private final Clock clock;
    private final ConcurrentHashMap<Bucket<K>, Counters> buckets = new ConcurrentHashMap<>();

    /**
     * Creates empty counters.
     *
     * @param name  what the counters are called in the log
     * @param width how many counters every bucket holds
     * @param clock where the current hour is read from
     */
    public HourlyCounters(String name, int width, Clock clock) {
        if (width < 1) throw new IllegalArgumentException("A bucket needs at least one counter");
        this.name = name;
        this.width = width;
        this.clock = clock;
    }

    /** Adds one amount per counter to the key's bucket in the current hour. */
    public void add(K key, long... amounts) {
        if (amounts.length != width) {
            throw new IllegalArgumentException("Expected %d amounts, got %d".formatted(width, amounts.length));
        }
        var counters = buckets.computeIfAbsent(new Bucket<>(currentHour(), key), _ -> new Counters(width));
        for (int i = 0; i < width; i++) {
            counters.totals.addAndGet(i, amounts[i]);
        }
    }

    /**
     * Hands every unwritten delta to the writer and forgets past hours that are fully written.
     *
     * @param includeCurrentHour whether the hour still being counted is written too
     */
    public synchronized void flush(boolean includeCurrentHour, Writer<K> writer) {
        Instant currentHour = currentHour();
        for (var entry : buckets.entrySet()) {
            Bucket<K> bucket = entry.getKey();
            Counters counters = entry.getValue();
            boolean pastHour = !bucket.hour().equals(currentHour);
            if (!pastHour && !includeCurrentHour) continue;
            long[] totals = counters.snapshot();
            long[] delta = counters.deltaTo(totals);
            if (isZero(delta)) {
                if (pastHour) buckets.remove(bucket, counters);
                continue;
            }
            try {
                writer.write(bucket.hour(), bucket.key(), delta);
                counters.written = totals;
                if (pastHour) buckets.remove(bucket, counters);
            } catch (Exception e) {
                log.warn(
                        "Failed to write the {} of {} at {}, keeping it for the next flush",
                        name,
                        bucket.key(),
                        bucket.hour(),
                        e);
            }
        }
    }

    /** How many buckets are held in memory. */
    public int size() {
        return buckets.size();
    }

    /** A copy of every bucket held, with its totals so far, in no particular order. */
    public List<Snapshot<K>> snapshot() {
        var out = new ArrayList<Snapshot<K>>(buckets.size());
        buckets.forEach(
                (bucket, counters) -> out.add(new Snapshot<>(bucket.hour(), bucket.key(), counters.snapshot())));
        return out;
    }

    private Instant currentHour() {
        return clock.instant().truncatedTo(ChronoUnit.HOURS);
    }

    private static boolean isZero(long[] values) {
        for (long value : values) {
            if (value != 0) return false;
        }
        return true;
    }

    /** Adds one bucket's delta to the stored bucket; throwing keeps the delta for the next flush. */
    @FunctionalInterface
    public interface Writer<K> {
        void write(Instant hour, K key, long[] delta) throws Exception;
    }

    /** One bucket with everything counted in it so far, one total per counter. */
    public record Snapshot<K>(Instant hour, K key, long[] totals) {}

    private record Bucket<K>(Instant hour, K key) {}

    private static final class Counters {
        private final AtomicLongArray totals;
        private long[] written;

        private Counters(int width) {
            totals = new AtomicLongArray(width);
            written = new long[width];
        }

        private long[] snapshot() {
            long[] values = new long[totals.length()];
            for (int i = 0; i < values.length; i++) {
                values[i] = totals.get(i);
            }
            return values;
        }

        private long[] deltaTo(long[] current) {
            long[] delta = new long[current.length];
            for (int i = 0; i < current.length; i++) {
                delta[i] = current[i] - written[i];
            }
            return delta;
        }
    }
}
