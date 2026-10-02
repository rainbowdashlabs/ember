/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.ShutdownFlush;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.HourlyCounters;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * In-memory accumulator for per-public-page hit counters. Records one hit per request to a public
 * page route, dimensioned by {@code (hour, pageId, country, refererDomain, isBot)}, and writes them to
 * {@code page_hit_hourly}.
 *
 * <p>Non-blocking on the request path: {@link #record} only touches the {@link HourlyCounters}. The
 * periodic flush writes the hours that are over, so an hour's referers are collapsed once; the shutdown
 * flush writes the current hour as well, which the adding upsert makes safe, so a restart loses no hits.
 *
 * <p>Long-tail referer collapse: before writing, the recorder checks how often the bucket's referer
 * domain has appeared for the page in the last week; domains that haven't crossed the threshold collapse
 * to {@code other} so the referer dimension stays bounded.
 */
@Singleton
public class PageHitRecorder implements ShutdownFlush, TaskSource {

    /**
     * Context attribute key carrying the resolved {@code station_page.id} for a successful
     * public-page request. Set by the public page route handlers after the page row is
     * found; the {@code ApiServer} after-handler reads it and, when present, records a hit
     * against this page. The contract means non-page public requests (file serves, partner
     * lookups) never accidentally count as page views.
     */
    public static final String ATTR_PAGE_HIT_PAGE_ID = "pageHitPageId";

    private static final Logger log = LoggerFactory.getLogger(PageHitRecorder.class);
    private static final Duration PRUNE_INTERVAL = Duration.ofHours(6);
    private static final String OTHER = "other";
    private static final long LONG_TAIL_WINDOW_DAYS = 7;
    private static final long LONG_TAIL_MIN_HITS = 5;

    private final HourlyCounters<PageKey> counters;
    private final PageHitRepository repository;
    private final Metrics metrics;
    private final Clock clock;

    @Inject
    public PageHitRecorder(PageHitRepository repository, Metrics metrics) {
        this(repository, metrics, Clock.systemUTC());
    }

    PageHitRecorder(PageHitRepository repository, Metrics metrics, Clock clock) {
        this.repository = repository;
        this.metrics = metrics;
        this.clock = clock;
        this.counters = new HourlyCounters<>("page hits", 1, clock);
    }

    private static String normalizeCountry(@Nullable String country) {
        if (country == null || country.isBlank()) return "XX";
        String trimmed = country.trim();
        if (trimmed.length() != 2) return "XX";
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * Adds one hit to the appropriate bucket. Non-blocking - safe to call from the
     * after-handler. {@code country} is normalised to upper-case ASCII; {@code null} or
     * blank values fall back to {@code XX}. {@code refererDomain} is taken verbatim - the
     * caller is expected to have reduced it via {@code RefererDomainExtractor}.
     */
    public void record(int pageId, @Nullable String country, String refererDomain, boolean isBot) {
        if (!metrics.webStatsEnabled()) return;
        String normalizedCountry = normalizeCountry(country);
        String normalizedReferer = refererDomain == null || refererDomain.isBlank() ? "direct" : refererDomain;
        counters.add(new PageKey(pageId, normalizedCountry, normalizedReferer, isBot), 1);
    }

    /**
     * Writes every bucket whose hour is over. Current-hour buckets stay in memory so further hits in
     * the same hour keep aggregating without an upsert per call.
     */
    public void flush() {
        write(false);
    }

    @Override
    public String name() {
        return "page hits";
    }

    /**
     * Writes everything counted, the current hour included.
     */
    @Override
    public void flushAll() {
        write(true);
    }

    /**
     * Returns the number of in-memory accumulators currently buffered. Useful for tests
     * and operational metrics.
     */
    public int bufferedBucketCount() {
        return counters.size();
    }

    /**
     * Returns a defensive snapshot of every bucket currently buffered.
     */
    public List<PageHitBucket> snapshot() {
        return counters.snapshot().stream()
                .map(bucket -> bucket.key().toBucket(bucket.hour(), bucket.totals()[0]))
                .toList();
    }

    private void write(boolean includeCurrentHour) {
        Instant longTailSince = clock.instant().minus(LONG_TAIL_WINDOW_DAYS, ChronoUnit.DAYS);
        counters.flush(includeCurrentHour, (hour, key, delta) -> {
            String referer = collapseLongTail(key.pageId(), key.refererDomain(), longTailSince);
            repository.upsert(new PageHitBucket(hour, key.pageId(), key.country(), referer, key.isBot(), delta[0]));
        });
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

    void prune() {
        try {
            int days = Math.max(1, metrics.webStatsRetentionDays());
            Instant cutoff = clock.instant().minus(Duration.ofDays(days));
            int removed = repository.pruneBefore(cutoff);
            if (removed > 0) {
                log.info("Pruned {} expired page-hit buckets older than {} days", removed, days);
            }
        } catch (Exception e) {
            log.warn("Failed to prune expired page-hit buckets", e);
        }
    }

    private record PageKey(int pageId, String country, String refererDomain, boolean isBot) {
        private PageHitBucket toBucket(Instant hour, long hits) {
            return new PageHitBucket(hour, pageId, country, refererDomain, isBot, hits);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        var flushInterval = Duration.ofSeconds(Math.max(1, metrics.webStatsFlushIntervalSeconds()));
        return List.of(
                new ScheduledTask("page-hit-flush", Schedule.fixedRate(flushInterval, flushInterval), () -> {
                    if (metrics.webStatsEnabled()) flush();
                }),
                new ScheduledTask("page-hit-prune", Schedule.fixedRate(Duration.ofHours(1), PRUNE_INTERVAL), () -> {
                    if (metrics.webStatsEnabled()) prune();
                }));
    }
}
