/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.feed.repository.FeedMetricsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Recording front for the feed observability tables.
 *
 * <p>Persistence happens on a dedicated single-thread executor so a slow DB never blocks
 * the request thread. Every write is best-effort: a logged warning is preferable to a
 * failed feed render.
 *
 * <p>Prune runs once a day; the retention windows come from the {@link Metrics} conf so
 * operators can tune them without code changes.
 */
@Singleton
public class FeedMetricsService {
    private static final Logger log = LoggerFactory.getLogger(FeedMetricsService.class);

    private final FeedMetricsRepository repository;
    private final Metrics metrics;
    private final ExecutorService writer;

    @Inject
    public FeedMetricsService(FeedMetricsRepository repository, Metrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
        this.writer = Executors.newSingleThreadExecutor(daemon("feed-metrics-writer"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemon("feed-metrics-prune"));
        // Bound as an eager singleton so the prune schedule kicks in at boot. First sweep
        // runs after 1h so a long-running deployment trims yesterday's tail every day.
        scheduler.scheduleAtFixedRate(this::prune, 1, 24, TimeUnit.HOURS);
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            var t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * Asynchronously records a finished feed render. Safe to call from the request thread —
     * the actual DB work happens off-thread.
     */
    public void recordRender(String type, int status, long durationMs, int entryCount, String userAgent) {
        writer.execute(() -> {
            try {
                repository.recordRender(type, status, durationMs, entryCount);
                repository.recordRequest(userAgent);
            } catch (Exception e) {
                // Telemetry never tanks the request: a warning is enough.
                log.warn("Failed to record feed metric (type={}, status={})", type, status, e);
            }
        });
    }

    public List<FeedMetricsRepository.FeedMetricDaily> recentDailyMetrics(int days) {
        return repository.findDailyMetrics(LocalDate.now().minusDays(Math.max(0, days)));
    }

    public List<FeedMetricsRepository.FeedUserAgentStat> topUserAgents(int limit) {
        return repository.findTopUserAgents(Math.max(1, limit));
    }

    public long totalRequests() {
        return repository.countRequests();
    }

    private void prune() {
        try {
            int retention = metrics.feedStatsRetentionDays();
            int dailyDropped = repository.pruneDailyMetrics(retention);
            int uaDropped = repository.pruneInactiveUserAgents(retention);
            if (dailyDropped > 0 || uaDropped > 0) {
                log.info(
                        "Pruned feed metrics: {} daily rows, {} inactive user-agents (retention={}d)",
                        dailyDropped,
                        uaDropped,
                        retention);
            }
        } catch (Exception e) {
            log.warn("Failed to prune feed metrics", e);
        }
    }
}
