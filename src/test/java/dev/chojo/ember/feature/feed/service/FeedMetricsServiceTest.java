/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.feed.repository.FeedMetricsRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedMetricsServiceTest {

    private final TaskScheduler scheduler = new TaskScheduler();

    @AfterEach
    void stop() {
        scheduler.stop(Duration.ofSeconds(1));
    }

    private FeedMetricsRepository repo() {
        return mock(FeedMetricsRepository.class);
    }

    private Metrics metrics(int retentionDays) {
        var m = mock(Metrics.class);
        when(m.feedStatsRetentionDays()).thenReturn(retentionDays);
        return m;
    }

    @Test
    void recordRenderForwardsToRepoOffThread() {
        var repo = repo();
        var service = new FeedMetricsService(repo, metrics(90), scheduler);

        service.recordRender("ics", 200, 42L, 5, "Thunderbird/115");

        verify(repo, timeout(2_000)).recordRender("ics", 200, 42L, 5);
        verify(repo, timeout(2_000)).recordRequest("Thunderbird/115");
    }

    @Test
    void recordRenderSwallowsRepoFailures() {
        var repo = repo();
        doThrow(new RuntimeException("db down")).when(repo).recordRender(anyString(), anyInt(), anyLong(), anyLong());
        var service = new FeedMetricsService(repo, metrics(90), scheduler);

        assertDoesNotThrow(() -> service.recordRender("rss", 500, 10L, 0, "x"));

        verify(repo, timeout(2_000)).recordRender(eq("rss"), eq(500), eq(10L), eq(0L));
    }

    @Test
    void theShutdownFlushWritesWhatIsStillQueued() {
        var repo = repo();
        var stopped = new TaskScheduler();
        stopped.stop(Duration.ZERO);
        var service = new FeedMetricsService(repo, metrics(90), stopped);

        service.recordRender("atom", 200, 7L, 1, "Feeder");
        verifyNoInteractions(repo);

        service.flushAll();

        verify(repo).recordRender("atom", 200, 7L, 1);
        assertEquals("feed metrics", service.name());
    }

    @Test
    void recentDailyMetricsDelegatesWithComputedSince() {
        var repo = repo();
        when(repo.findDailyMetrics(any())).thenReturn(List.of());
        var service = new FeedMetricsService(repo, metrics(90), scheduler);

        service.recentDailyMetrics(7);

        verify(repo).findDailyMetrics(LocalDate.now().minusDays(7));
    }

    @Test
    void topUserAgentsAndTotalDelegate() {
        var repo = repo();
        when(repo.findTopUserAgents(anyInt())).thenReturn(List.of());
        when(repo.countRequests()).thenReturn(42L);
        var service = new FeedMetricsService(repo, metrics(90), scheduler);

        service.topUserAgents(0);
        assertEquals(42L, service.totalRequests());

        verify(repo).findTopUserAgents(1);
        verify(repo).countRequests();
    }

    @Test
    void pruneInvokesBothTablesWithConfiguredRetentionAndSwallowsErrors() {
        var repo = repo();
        when(repo.pruneDailyMetrics(30)).thenReturn(2);
        when(repo.pruneInactiveUserAgents(30)).thenReturn(1);
        var service = new FeedMetricsService(repo, metrics(30), scheduler);
        var task = new FeedMetricsService.PruneTask(service);

        task.run();
        verify(repo).pruneDailyMetrics(30);
        verify(repo).pruneInactiveUserAgents(30);
        assertEquals(Schedule.fixedRate(Duration.ofHours(1), Duration.ofHours(24)), task.schedule());

        reset(repo);
        when(repo.pruneDailyMetrics(anyInt())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(service::prune);
    }
}
