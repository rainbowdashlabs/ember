/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Persists feed render observability:
 *
 * <ul>
 *   <li>{@code feed_metric_daily} - one row per {@code (day, type, status)} with the request
 *       count, total duration, total entries rendered, and a fixed-bucket duration histogram.
 *       Upserted on every feed request.</li>
 *   <li>{@code feed_user_agent_stat} - global aggregate of feed reader User-Agents. No
 *       per-token attribution by design (see {@code patch_12.sql}).</li>
 * </ul>
 *
 * <p>Both tables are pruned by configurable retention via the metrics conf block.
 */
@Singleton
public class FeedMetricsRepository {

    /**
     * Cap on the verbatim UA we store. Stops a misbehaving client from filling the column.
     */
    private static final int UA_MAX_LENGTH = 512;

    // -- daily histogram --

    private static String shortHash(String ua) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(ua.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Upserts a single feed render into the daily aggregate. {@code durationMs} is bucketed
     * into the fixed histogram bins on the table.
     */
    public void recordRender(String type, int status, long durationMs, long entries) {
        // SADU named parameters reject digits, so we use word-only names.
        query("""
                INSERT INTO feed_metric_daily(day, type, status, count, total_duration_ms, total_entries,
                                              bucket_lt_50, bucket_lt_200, bucket_lt_1000, bucket_lt_5000, bucket_gte_5000)
                VALUES (current_date, :type, :status, 1, :duration_ms, :entries,
                        :bucket_a, :bucket_b, :bucket_c, :bucket_d, :bucket_e)
                ON CONFLICT (day, type, status) DO UPDATE
                SET count             = feed_metric_daily.count + 1,
                    total_duration_ms = feed_metric_daily.total_duration_ms + excluded.total_duration_ms,
                    total_entries     = feed_metric_daily.total_entries + excluded.total_entries,
                    bucket_lt_50      = feed_metric_daily.bucket_lt_50 + excluded.bucket_lt_50,
                    bucket_lt_200     = feed_metric_daily.bucket_lt_200 + excluded.bucket_lt_200,
                    bucket_lt_1000    = feed_metric_daily.bucket_lt_1000 + excluded.bucket_lt_1000,
                    bucket_lt_5000    = feed_metric_daily.bucket_lt_5000 + excluded.bucket_lt_5000,
                    bucket_gte_5000   = feed_metric_daily.bucket_gte_5000 + excluded.bucket_gte_5000;
                """)
                .single(call().bind("type", type)
                        .bind("status", status)
                        .bind("duration_ms", durationMs)
                        .bind("entries", entries)
                        .bind("bucket_a", durationMs < 50 ? 1 : 0)
                        .bind("bucket_b", durationMs >= 50 && durationMs < 200 ? 1 : 0)
                        .bind("bucket_c", durationMs >= 200 && durationMs < 1000 ? 1 : 0)
                        .bind("bucket_d", durationMs >= 1000 && durationMs < 5000 ? 1 : 0)
                        .bind("bucket_e", durationMs >= 5000 ? 1 : 0))
                .insert();
    }

    /**
     * Returns daily metric rows ordered by day descending. Caller supplies the inclusive
     * lower-bound day; rows older than that are skipped.
     */
    public List<FeedMetricDaily> findDailyMetrics(LocalDate sinceInclusive) {
        return query("""
                SELECT day, type, status, count, total_duration_ms, total_entries,
                       bucket_lt_50, bucket_lt_200, bucket_lt_1000, bucket_lt_5000, bucket_gte_5000
                FROM feed_metric_daily
                WHERE day >= :since
                ORDER BY day DESC, type, status;
                """)
                .single(call().bind("since", sinceInclusive))
                .map(row -> new FeedMetricDaily(
                        row.getObject("day", LocalDate.class),
                        row.getString("type"),
                        row.getInt("status"),
                        row.getLong("count"),
                        row.getLong("total_duration_ms"),
                        row.getLong("total_entries"),
                        row.getLong("bucket_lt_50"),
                        row.getLong("bucket_lt_200"),
                        row.getLong("bucket_lt_1000"),
                        row.getLong("bucket_lt_5000"),
                        row.getLong("bucket_gte_5000")))
                .all();
    }

    // -- user-agent aggregate --

    /**
     * Drops daily aggregates older than the configured retention.
     */
    public int pruneDailyMetrics(int retentionDays) {
        return query("DELETE FROM feed_metric_daily WHERE day < current_date - make_interval(days := :days);")
                .single(call().bind("days", Math.max(1, retentionDays)))
                .delete()
                .rows();
    }

    /**
     * Bumps the request count for the given {@code User-Agent}. Best-effort: callers should
     * treat exceptions as non-fatal because feed rendering must never fail because of a
     * telemetry write.
     */
    public void recordRequest(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return;
        String truncated = userAgent.length() > UA_MAX_LENGTH ? userAgent.substring(0, UA_MAX_LENGTH) : userAgent;
        String hash = shortHash(truncated);
        query("""
                INSERT INTO feed_user_agent_stat(ua_hash, ua_string, request_count, first_seen, last_seen)
                VALUES (:ua_hash, :ua_string, 1, now(), now())
                ON CONFLICT (ua_hash) DO UPDATE
                SET request_count = feed_user_agent_stat.request_count + 1,
                    last_seen = now(),
                    ua_string = excluded.ua_string;
                """)
                .single(call().bind("ua_hash", hash).bind("ua_string", truncated))
                .insert();
    }

    /**
     * Returns the most active reader user-agents, ordered by request count descending.
     *
     * @param limit maximum number of rows to return
     */
    public List<FeedUserAgentStat> findTopUserAgents(int limit) {
        return query(
                        "SELECT ua_hash, ua_string, request_count, first_seen, last_seen FROM feed_user_agent_stat ORDER BY request_count DESC LIMIT :limit;")
                .single(call().bind("limit", limit))
                .map(row -> new FeedUserAgentStat(
                        row.getString("ua_hash"),
                        row.getString("ua_string"),
                        row.getLong("request_count"),
                        row.get("first_seen", INSTANT_TIMESTAMP),
                        row.get("last_seen", INSTANT_TIMESTAMP)))
                .all();
    }

    /**
     * Returns the total number of recorded feed requests across all user-agents.
     */
    public long countRequests() {
        return query("SELECT coalesce(sum(request_count), 0) AS total FROM feed_user_agent_stat;")
                .single(call())
                .map(row -> row.getLong("total"))
                .first()
                .orElse(0L);
    }

    /**
     * Drops user-agent aggregates whose {@code last_seen} is older than the configured
     * retention. Keeps the table from accumulating zombie entries for readers that have moved
     * to another deployment.
     */
    public int pruneInactiveUserAgents(int retentionDays) {
        return query("DELETE FROM feed_user_agent_stat WHERE last_seen < now() - make_interval(days := :days);")
                .single(call().bind("days", Math.max(1, retentionDays)))
                .delete()
                .rows();
    }

    /**
     * Aggregated per-user-agent statistics row.
     */
    public record FeedUserAgentStat(
            String uaHash, String uaString, long requestCount, Instant firstSeen, Instant lastSeen) {}

    /**
     * Daily feed render histogram and totals per {@code (type, status)}.
     */
    public record FeedMetricDaily(
            LocalDate day,
            String type,
            int status,
            long count,
            long totalDurationMs,
            long totalEntries,
            long bucketLt50,
            long bucketLt200,
            long bucketLt1000,
            long bucketLt5000,
            long bucketGte5000) {}
}
