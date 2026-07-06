/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory accumulator and async flusher for per-public-page hit counters. Records one
 * hit per request to a public page route, dimensioned by {@code (hour, pageId, country,
 * refererDomain, isBot)}, and flushes to {@code page_hit_hourly} on a fixed cadence.
 *
 * <p>Mirrors the design of {@code StationTrafficRecorder}: non-blocking on the request
 * path (only a {@link ConcurrentHashMap} lookup and an {@link AtomicLong} increment), with
 * the DB upsert on a single-threaded scheduler so request latency is never affected.
 *
 * <p>Long-tail referer collapse: at flush time, before upserting, the
 * recorder checks how often the bucket's referer domain has appeared for the page in the
 * last week; domains that haven't crossed the threshold collapse to {@code other} so the
 * referer dimension stays bounded.
 */
@Singleton
public class PageHitRecorder {

    /**
     * Context attribute key carrying the resolved {@code station_page.id} for a successful
     * public-page request. Set by the public page route handlers after the page row is
     * found; the {@code ApiServer} after-handler reads it and, when present, records a hit
     * against this page. The contract means non-page public requests (file serves, partner
     * lookups) never accidentally count as page views.
     */
    public static final String ATTR_PAGE_HIT_PAGE_ID = "pageHitPageId";

    private static final Logger log = LoggerFactory.getLogger(PageHitRecorder.class);
    private static final long PRUNE_INTERVAL_HOURS = 6;
    private static final String OTHER = "other";
    private static final long LONG_TAIL_WINDOW_DAYS = 7;
    private static final long LONG_TAIL_MIN_HITS = 5;

    private final ConcurrentHashMap<BucketKey, AtomicLong> buckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "page-hit-recorder");
        t.setDaemon(true);
        return t;
    });
    private final PageHitRepository repository;
    private final Metrics metrics;

    @Inject
    public PageHitRecorder(PageHitRepository repository, Metrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    private static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) return "XX";
        String trimmed = country.trim();
        if (trimmed.length() != 2) return "XX";
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * Schedules the flush and prune tasks. Idempotent — call once at boot from
     * {@code ApiServer}.
     */
    public void start() {
        if (!metrics.webStatsEnabled()) {
            log.info("Web stats recording disabled by config; recorder will accept calls but never flush.");
            return;
        }
        long flushSeconds = Math.max(1, metrics.webStatsFlushIntervalSeconds());
        executor.scheduleAtFixedRate(this::flush, flushSeconds, flushSeconds, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(this::prune, 1, PRUNE_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Adds one hit to the appropriate bucket. Non-blocking — safe to call from the
     * after-handler. {@code country} is normalised to upper-case ASCII; {@code null} or
     * blank values fall back to {@code XX}. {@code refererDomain} is taken verbatim — the
     * caller is expected to have reduced it via {@code RefererDomainExtractor}.
     */
    public void record(int pageId, String country, String refererDomain, boolean isBot) {
        if (!metrics.webStatsEnabled()) return;
        String normalizedCountry = normalizeCountry(country);
        String normalizedReferer = refererDomain == null || refererDomain.isBlank() ? "direct" : refererDomain;
        var key = new BucketKey(currentHour(), pageId, normalizedCountry, normalizedReferer, isBot);
        buckets.computeIfAbsent(key, _ -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Persists every bucket whose hour is strictly older than the current one, then drops
     * those entries from the in-memory map. Current-hour buckets stay in memory so further
     * hits in the same hour keep aggregating without an UPSERT per call.
     *
     * <p>Visible for tests / forced flush.
     */
    public void flush() {
        Instant currentHour = currentHour();
        var toFlush = new ArrayList<Map.Entry<BucketKey, AtomicLong>>();
        for (var entry : buckets.entrySet()) {
            if (!entry.getKey().hour.equals(currentHour)) {
                toFlush.add(entry);
            }
        }
        Instant longTailSince = Instant.now().minus(LONG_TAIL_WINDOW_DAYS, ChronoUnit.DAYS);
        for (var entry : toFlush) {
            BucketKey key = entry.getKey();
            long hits = entry.getValue().get();
            String referer = collapseLongTail(key.pageId, key.refererDomain, longTailSince);
            try {
                repository.upsert(new PageHitBucket(key.hour, key.pageId, key.country, referer, key.isBot, hits));
                buckets.remove(key, entry.getValue());
            } catch (Exception e) {
                log.warn("Failed to flush page-hit bucket {} — will retry on next tick", key, e);
            }
        }
    }

    /**
     * Returns the number of in-memory accumulators currently buffered. Useful for tests
     * and operational metrics.
     */
    public int bufferedBucketCount() {
        return buckets.size();
    }

    /**
     * Returns a defensive snapshot of every bucket currently buffered.
     */
    public List<PageHitBucket> snapshot() {
        var out = new ArrayList<PageHitBucket>(buckets.size());
        for (var entry : buckets.entrySet()) {
            BucketKey key = entry.getKey();
            out.add(new PageHitBucket(
                    key.hour,
                    key.pageId,
                    key.country,
                    key.refererDomain,
                    key.isBot,
                    entry.getValue().get()));
        }
        return out;
    }

    private String collapseLongTail(int pageId, String refererDomain, Instant since) {
        if (refererDomain.equals("direct") || refererDomain.equals("internal") || refererDomain.equals(OTHER)) {
            return refererDomain;
        }
        try {
            long historical = repository.recentRefererCount(pageId, refererDomain, since);
            if (historical < LONG_TAIL_MIN_HITS) return OTHER;
        } catch (Exception e) {
            log.debug("Long-tail lookup failed for referer={} page={}; keeping raw value", refererDomain, pageId, e);
        }
        return refererDomain;
    }

    private void prune() {
        try {
            int days = Math.max(1, metrics.webStatsRetentionDays());
            Instant cutoff = Instant.now().minus(Duration.ofDays(days));
            int removed = repository.pruneBefore(cutoff);
            if (removed > 0) {
                log.info("Pruned {} expired page-hit buckets older than {} days", removed, days);
            }
        } catch (Exception e) {
            log.warn("Failed to prune expired page-hit buckets", e);
        }
    }

    private Instant currentHour() {
        return Instant.now().truncatedTo(ChronoUnit.HOURS);
    }

    private record BucketKey(Instant hour, int pageId, String country, String refererDomain, boolean isBot) {}
}
