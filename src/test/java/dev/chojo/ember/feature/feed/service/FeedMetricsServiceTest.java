/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.feed.repository.FeedMetricsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedMetricsServiceTest {

    private FeedMetricsRepository repo() {
        return mock(FeedMetricsRepository.class);
    }

    private Metrics metrics(int retentionDays) {
        var m = mock(Metrics.class);
        when(m.feedStatsRetentionDays()).thenReturn(retentionDays);
        return m;
    }

    @Test
    void recordRenderForwardsToRepoOffThread() throws Exception {
        var repo = repo();
        var service = new FeedMetricsService(repo, metrics(90));

        service.recordRender("ics", 200, 42L, 5, "Thunderbird/115");
        // Writes happen on a dedicated executor; wait briefly for the queued task.
        waitForExecutor(service);

        verify(repo).recordRender("ics", 200, 42L, 5);
        verify(repo).recordRequest("Thunderbird/115");
    }

    @Test
    void recordRenderSwallowsRepoFailures() throws Exception {
        var repo = repo();
        doThrow(new RuntimeException("db down")).when(repo).recordRender(anyString(), anyInt(), anyLong(), anyLong());
        var service = new FeedMetricsService(repo, metrics(90));

        assertDoesNotThrow(() -> service.recordRender("rss", 500, 10L, 0, "x"));
        waitForExecutor(service);
        // Repo was called; the exception did not propagate.
        verify(repo).recordRender(eq("rss"), eq(500), eq(10L), eq(0L));
    }

    @Test
    void recentDailyMetricsDelegatesWithComputedSince() {
        var repo = repo();
        when(repo.findDailyMetrics(any())).thenReturn(List.of());
        var service = new FeedMetricsService(repo, metrics(90));

        service.recentDailyMetrics(7);

        verify(repo).findDailyMetrics(LocalDate.now().minusDays(7));
    }

    @Test
    void topUserAgentsAndTotalDelegate() {
        var repo = repo();
        when(repo.findTopUserAgents(anyInt())).thenReturn(List.of());
        when(repo.countRequests()).thenReturn(42L);
        var service = new FeedMetricsService(repo, metrics(90));

        service.topUserAgents(0); // service clamps to min 1
        assertEquals(42L, service.totalRequests());

        verify(repo).findTopUserAgents(1);
        verify(repo).countRequests();
    }

    @Test
    void pruneInvokesBothTablesWithConfiguredRetentionAndSwallowsErrors() throws Exception {
        var repo = repo();
        when(repo.pruneDailyMetrics(30)).thenReturn(2);
        when(repo.pruneInactiveUserAgents(30)).thenReturn(1);
        var service = new FeedMetricsService(repo, metrics(30));

        invokePrune(service);
        verify(repo).pruneDailyMetrics(30);
        verify(repo).pruneInactiveUserAgents(30);

        // Repo throws — service must not propagate.
        reset(repo);
        when(repo.pruneDailyMetrics(anyInt())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> invokePrune(service));
    }

    private static void invokePrune(FeedMetricsService service) throws Exception {
        var m = FeedMetricsService.class.getDeclaredMethod("prune");
        m.setAccessible(true);
        m.invoke(service);
    }

    private static void waitForExecutor(FeedMetricsService service) throws Exception {
        // Submit a tracer task; once it runs, all previously-submitted work has too.
        var marker = new CompletableFuture<Void>();
        getWriter(service).execute(() -> marker.complete(null));
        marker.get(5, TimeUnit.SECONDS);
    }

    private static ExecutorService getWriter(FeedMetricsService service) throws Exception {
        var field = FeedMetricsService.class.getDeclaredField("writer");
        field.setAccessible(true);
        return (ExecutorService) field.get(service);
    }
}
