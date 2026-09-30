/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.feed.repository.FeedMetricsRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.SerialLane;
import dev.chojo.ember.lifecycle.ShutdownFlush;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Recording front for the feed observability tables.
 *
 * <p>Renders are queued and written on a lane, one batch at a time, so a slow DB never blocks the
 * request thread. Every write is best-effort: a logged warning is preferable to a failed feed render,
 * and telemetry never fails a request. What is still queued when the instance stops is written by the
 * shutdown flush.
 *
 * <p>Prune runs once a day; the retention windows come from the {@link Metrics} conf so
 * operators can tune them without code changes.
 */
@Singleton
public class FeedMetricsService implements ShutdownFlush, TaskSource {
    private static final Logger log = LoggerFactory.getLogger(FeedMetricsService.class);

    private final FeedMetricsRepository repository;
    private final Metrics metrics;
    private final Queue<Render> pending = new ConcurrentLinkedQueue<>();
    private final SerialLane writer;

    @Inject
    public FeedMetricsService(FeedMetricsRepository repository, Metrics metrics, TaskScheduler scheduler) {
        this.repository = repository;
        this.metrics = metrics;
        this.writer = scheduler.lane("feed-metrics-writer");
    }

    /**
     * Asynchronously records a finished feed render. Safe to call from the request thread -
     * the actual DB work happens off-thread.
     */
    public void recordRender(String type, int status, long durationMs, int entryCount, String userAgent) {
        pending.add(new Render(type, status, durationMs, entryCount, userAgent));
        writer.submit(this::writePending);
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

    @Override
    public String name() {
        return "feed metrics";
    }

    /**
     * Writes every render still queued.
     */
    @Override
    public void flushAll() {
        writePending();
    }

    void writePending() {
        Render render;
        while ((render = pending.poll()) != null) {
            write(render);
        }
    }

    private void write(Render render) {
        try {
            repository.recordRender(render.type(), render.status(), render.durationMs(), render.entryCount());
            repository.recordRequest(render.userAgent());
        } catch (Exception e) {
            log.warn("Failed to record feed metric (type={}, status={})", render.type(), render.status(), e);
        }
    }

    void prune() {
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

    private record Render(String type, int status, long durationMs, int entryCount, String userAgent) {}

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "feed-metrics-prune", Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)), this::prune));
    }
}
