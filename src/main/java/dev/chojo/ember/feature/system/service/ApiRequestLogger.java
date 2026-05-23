/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Captures API request timings and status codes asynchronously.
 * Batches inserts to avoid slowing down request handling.
 * Auto-prunes entries older than 3 days.
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

    private void flush() {
        var batch = new ArrayList<RequestEntry>();
        RequestEntry entry;
        while ((entry = buffer.poll()) != null && batch.size() < BATCH_SIZE * 2) {
            batch.add(entry);
        }
        if (batch.isEmpty()) return;

        try {
            Query.query("""
                            INSERT INTO api_request_log(method, path, status_code, duration_ms)
                            VALUES(:method, :path, :status_code, :duration_ms);""")
                    .batch(batch.stream()
                            .map(e -> Call.of()
                                    .bind("method", e.method)
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
            Query.query("DELETE FROM api_request_log WHERE created_at < now() - INTERVAL '3 days';")
                    .single()
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

    // -- Query methods for admin API --

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

    public List<EndpointStats> getSlowestEndpoints(int limit) {
        return Query.query("""
                        SELECT method, path, COUNT(*) as cnt, AVG(duration_ms) as avg_ms,
                               MIN(duration_ms) as min_ms, MAX(duration_ms) as max_ms,
                               SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END)::float / COUNT(*) as error_rate
                        FROM api_request_log WHERE created_at > now() - INTERVAL '3 days'
                        GROUP BY method, path ORDER BY avg_ms DESC LIMIT :limit;""")
                .single(Call.of().bind("limit", limit))
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
        return Query.query("""
                        SELECT method, path, COUNT(*) as cnt, AVG(duration_ms) as avg_ms,
                               MIN(duration_ms) as min_ms, MAX(duration_ms) as max_ms,
                               SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END)::float / COUNT(*) as error_rate
                        FROM api_request_log WHERE created_at > now() - INTERVAL '3 days'
                        GROUP BY method, path HAVING COUNT(*) > 5 ORDER BY avg_ms ASC LIMIT :limit;""")
                .single(Call.of().bind("limit", limit))
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
        return Query.query("""
                        SELECT method, path, COUNT(*) as cnt, AVG(duration_ms) as avg_ms,
                               MIN(duration_ms) as min_ms, MAX(duration_ms) as max_ms,
                               SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END)::float / COUNT(*) as error_rate
                        FROM api_request_log WHERE created_at > now() - INTERVAL '3 days'
                        GROUP BY method, path
                        HAVING SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END) > 0
                        ORDER BY error_rate DESC, cnt DESC LIMIT :limit;""")
                .single(Call.of().bind("limit", limit))
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

    public List<StatusBreakdown> getStatusBreakdown() {
        return Query.query("""
                        SELECT method, path, status_code, COUNT(*) as cnt
                        FROM api_request_log WHERE created_at > now() - INTERVAL '3 days'
                        GROUP BY method, path, status_code ORDER BY cnt DESC LIMIT 200;""")
                .single()
                .map(row -> new StatusBreakdown(
                        row.getString("method"), row.getString("path"),
                        row.getInt("status_code"), row.getLong("cnt")))
                .all();
    }

    public List<HourlyStats> getHourlyStats() {
        return Query.query("""
                        SELECT to_char(created_at, 'YYYY-MM-DD HH24:00') as hour,
                               COUNT(*) as cnt, AVG(duration_ms) as avg_ms,
                               SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END) as errors
                        FROM api_request_log WHERE created_at > now() - INTERVAL '3 days'
                        GROUP BY hour ORDER BY hour;""")
                .single()
                .map(row -> new HourlyStats(
                        row.getString("hour"), row.getLong("cnt"),
                        row.getDouble("avg_ms"), row.getLong("errors")))
                .all();
    }

    private record RequestEntry(String method, String path, int statusCode, int durationMs) {}
}
