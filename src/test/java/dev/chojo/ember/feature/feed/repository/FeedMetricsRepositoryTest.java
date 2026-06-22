/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.*;

class FeedMetricsRepositoryTest extends RepositoryTestBase {

    @AfterEach
    void cleanup() {
        query("DELETE FROM feed_metric_daily;").single().delete();
        query("DELETE FROM feed_user_agent_stat;").single().delete();
    }

    @Test
    void firstRenderInsertsRowAndSecondAggregatesCount() {
        feedMetricsRepo.recordRender("ics", 200, 42L, 5L);
        feedMetricsRepo.recordRender("ics", 200, 80L, 3L);

        var rows = feedMetricsRepo.findDailyMetrics(LocalDate.now());
        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals("ics", row.type());
        assertEquals(200, row.status());
        assertEquals(2L, row.count());
        assertEquals(122L, row.totalDurationMs());
        assertEquals(8L, row.totalEntries());
    }

    @Test
    void durationLandsInTheExpectedHistogramBucket() {
        feedMetricsRepo.recordRender("rss", 200, 10L, 1L); // <50
        feedMetricsRepo.recordRender("rss", 200, 60L, 1L); // <200
        feedMetricsRepo.recordRender("rss", 200, 600L, 1L); // <1000
        feedMetricsRepo.recordRender("rss", 200, 1500L, 1L); // <5000
        feedMetricsRepo.recordRender("rss", 200, 6000L, 1L); // gte 5000

        var row = feedMetricsRepo.findDailyMetrics(LocalDate.now()).getFirst();
        assertEquals(1L, row.bucketLt50());
        assertEquals(1L, row.bucketLt200());
        assertEquals(1L, row.bucketLt1000());
        assertEquals(1L, row.bucketLt5000());
        assertEquals(1L, row.bucketGte5000());
    }

    @Test
    void differentTypesAndStatusesAreSeparateRows() {
        feedMetricsRepo.recordRender("ics", 200, 10L, 1L);
        feedMetricsRepo.recordRender("rss", 200, 10L, 1L);
        feedMetricsRepo.recordRender("ics", 304, 5L, 0L);

        var rows = feedMetricsRepo.findDailyMetrics(LocalDate.now());
        assertEquals(3, rows.size());
    }

    @Test
    void userAgentUpsertCountsBothRequestsAndReturnsTotal() {
        feedMetricsRepo.recordRequest("Thunderbird/115");
        feedMetricsRepo.recordRequest("Thunderbird/115");
        feedMetricsRepo.recordRequest("NetNewsWire/6");

        assertEquals(3L, feedMetricsRepo.countRequests());
        var top = feedMetricsRepo.findTopUserAgents(10);
        assertEquals(2, top.size());
        assertEquals("Thunderbird/115", top.getFirst().uaString());
        assertEquals(2L, top.getFirst().requestCount());
    }

    @Test
    void blankUserAgentIsIgnored() {
        feedMetricsRepo.recordRequest("");
        feedMetricsRepo.recordRequest(null);
        assertEquals(0L, feedMetricsRepo.countRequests());
    }

    @Test
    void pruneRespectsRetentionWindow() {
        // Insert a stale row directly so we can prune it.
        query(
                        "INSERT INTO feed_metric_daily(day, type, status, count, total_duration_ms, total_entries) VALUES (current_date - interval '100 days', 'ics', 200, 1, 10, 1);")
                .single()
                .insert();
        feedMetricsRepo.recordRender("ics", 200, 10L, 1L);

        int dropped = feedMetricsRepo.pruneDailyMetrics(30);
        assertEquals(1, dropped);
        var rows = feedMetricsRepo.findDailyMetrics(LocalDate.now().minusDays(365));
        assertEquals(1, rows.size());
    }

    @Test
    void pruneUserAgentsRemovesStaleEntries() {
        query(
                        "INSERT INTO feed_user_agent_stat(ua_hash, ua_string, request_count, first_seen, last_seen) VALUES ('deadbeefdeadbeef', 'old', 5, now() - interval '120 days', now() - interval '120 days');")
                .single()
                .insert();
        feedMetricsRepo.recordRequest("Thunderbird/115");

        int dropped = feedMetricsRepo.pruneInactiveUserAgents(30);
        assertEquals(1, dropped);
        var remaining = feedMetricsRepo.findTopUserAgents(10);
        assertEquals(1, remaining.size());
        assertEquals("Thunderbird/115", remaining.getFirst().uaString());
    }
}
