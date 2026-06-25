/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Captures API request timings and status codes asynchronously.
 * Batches inserts to avoid slowing down request handling.
 * Retention is governed by {@link Metrics#requestStatsRetentionDays()}.
 */
@Singleton
public class ApiRequestLogger {
    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogger.class);
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 5000;
    private static final long PRUNE_INTERVAL_HOURS = 6;

    private final ConcurrentLinkedQueue<RequestEntry> buffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "api-request-logger");
        t.setDaemon(true);
        return t;
    });
    private final Metrics metrics;

    @Inject
    public ApiRequestLogger(Metrics metrics) {
        this.metrics = metrics;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::prune, 1, PRUNE_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Records a request. Called from the API after-handler. Non-blocking.
     */
    public void record(String method, String path, int statusCode, long durationMs) {
        buffer.add(new RequestEntry(method, normalizePath(path), statusCode, (int) durationMs));
        if (buffer.size() >= BATCH_SIZE) {
            executor.execute(this::flush);
        }
    }

    public List<EndpointStats> getSlowestEndpoints(int limit) {
        return query("""
                SELECT
                    method,
                    path,
                    count(*)                                                              AS cnt,
                    avg(duration_ms)                                                      AS avg_ms,
                    min(duration_ms)                                                      AS min_ms,
                    max(duration_ms)                                                      AS max_ms,
                    sum(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END)::FLOAT / count(*) AS error_rate
                FROM
                    api_request_log
                WHERE created_at > now() - INTERVAL '3 days'
                GROUP BY method, path
                ORDER BY avg_ms DESC
                LIMIT :limit;""")
                .single(call().bind("limit", limit))
                .map(row -> new EndpointStats(
                        row.getString("method"),
                        row.getString("path"),
                        row.getLong("cnt"),
                        row.getDouble("avg_ms"),
                        row.getInt("min_ms"),
                        row.getInt("max_ms"),
                        row.getDouble("error_rate")))
                .all();
    }

    public List<EndpointStats> getFastestEndpoints(int limit) {
        return query("""
                SELECT
                    method,
                    path,
                    count(*)                                                              AS cnt,
                    avg(duration_ms)                                                      AS avg_ms,
                    min(duration_ms)                                                      AS min_ms,
                    max(duration_ms)                                                      AS max_ms,
                    sum(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END)::FLOAT / count(*) AS error_rate
                FROM
                    api_request_log
                WHERE created_at > now() - INTERVAL '3 days'
                GROUP BY method, path
                HAVING count(*) > 5
                ORDER BY avg_ms ASC
                LIMIT :limit;""")
                .single(call().bind("limit", limit))
                .map(row -> new EndpointStats(
                        row.getString("method"),
                        row.getString("path"),
                        row.getLong("cnt"),
                        row.getDouble("avg_ms"),
                        row.getInt("min_ms"),
                        row.getInt("max_ms"),
                        row.getDouble("error_rate")))
                .all();
    }

    public List<EndpointStats> getMostFailingEndpoints(int limit) {
        return query("""
                SELECT
                    method,
                    path,
                    count(*)                                                              AS cnt,
                    avg(duration_ms)                                                      AS avg_ms,
                    min(duration_ms)                                                      AS min_ms,
                    max(duration_ms)                                                      AS max_ms,
                    sum(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END)::FLOAT / count(*) AS error_rate
                FROM
                    api_request_log
                WHERE created_at > now() - INTERVAL '3 days'
                GROUP BY method, path
                HAVING sum(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END) > 0
                ORDER BY error_rate DESC, cnt DESC
                LIMIT :limit;""")
                .single(call().bind("limit", limit))
                .map(row -> new EndpointStats(
                        row.getString("method"),
                        row.getString("path"),
                        row.getLong("cnt"),
                        row.getDouble("avg_ms"),
                        row.getInt("min_ms"),
                        row.getInt("max_ms"),
                        row.getDouble("error_rate")))
                .all();
    }

    // -- Query methods for admin API --

    public List<StatusBreakdown> getStatusBreakdown() {
        return query("""
                SELECT
                    method,
                    path,
                    status_code,
                    count(*) AS cnt
                FROM
                    api_request_log
                WHERE created_at > now() - INTERVAL '3 days'
                GROUP BY method, path, status_code
                ORDER BY cnt DESC
                LIMIT 200;""")
                .single()
                .map(row -> new StatusBreakdown(
                        row.getString("method"), row.getString("path"),
                        row.getInt("status_code"), row.getLong("cnt")))
                .all();
    }

    public List<HourlyStats> getHourlyStats() {
        return query("""
                SELECT
                    to_char(created_at, 'YYYY-MM-DD HH24:00')           AS hour,
                    count(*)                                            AS cnt,
                    avg(duration_ms)                                    AS avg_ms,
                    sum(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END) AS errors
                FROM
                    api_request_log
                WHERE created_at > now() - INTERVAL '3 days'
                GROUP BY hour
                ORDER BY hour;""")
                .single()
                .map(row -> new HourlyStats(
                        row.getString("hour"), row.getLong("cnt"),
                        row.getDouble("avg_ms"), row.getLong("errors")))
                .all();
    }

    private void flush() {
        var batch = new ArrayList<RequestEntry>();
        RequestEntry entry;
        while ((entry = buffer.poll()) != null && batch.size() < BATCH_SIZE * 2) {
            batch.add(entry);
        }
        if (batch.isEmpty()) return;

        try {
            query("""
                    INSERT INTO api_request_log(method, path, status_code, duration_ms)
                    VALUES(:method, :path, :status_code, :duration_ms);""")
                    .batch(batch.stream()
                            .map(e -> call().bind("method", e.method)
                                    .bind("path", e.path)
                                    .bind("status_code", e.statusCode)
                                    .bind("duration_ms", e.durationMs))
                            .toList())
                    .insert();
        } catch (Exception e) {
            log.warn("Failed to flush API request log batch ({} entries)", batch.size(), e);
        }
    }

    private void prune() {
        try {
            // Retention is configurable via metrics.requestStatsRetentionDays; we compute the
            // cutoff in Java rather than passing days to SQL because SADU bind() doesn't expand
            // values into INTERVAL literals.
            int days = Math.max(1, metrics.requestStatsRetentionDays());
            query("DELETE FROM api_request_log WHERE created_at < now() - make_interval(days := :days);")
                    .single(call().bind("days", days))
                    .delete();
        } catch (Exception e) {
            log.warn("Failed to prune old API request log entries", e);
        }
    }

    /**
     * Normalizes paths by replacing numeric IDs with {id} placeholders
     * so endpoints with path params get aggregated together.
     */
    private String normalizePath(String path) {
        return path.replaceAll("/\\d+", "/{id}");
    }

    public record EndpointStats(
            String method,
            String path,
            long requestCount,
            double avgDurationMs,
            int minDurationMs,
            int maxDurationMs,
            double errorRate) {}

    public record StatusBreakdown(String method, String path, int statusCode, long count) {}

    public record HourlyStats(String hour, long requestCount, double avgDurationMs, long errorCount) {}

    private record RequestEntry(String method, String path, int statusCode, int durationMs) {}
}
