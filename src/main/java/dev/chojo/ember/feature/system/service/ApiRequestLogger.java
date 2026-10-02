/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.ShutdownFlush;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import io.javalin.http.Context;
import io.javalin.router.Endpoint;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Captures API request timings and status codes asynchronously.
 * Batches inserts to avoid slowing down request handling.
 * Retention is governed by {@link Metrics#requestStatsRetentionDays()}.
 *
 * <p>A request is recorded under the route template it matched, never under the path somebody
 * asked for: a path can carry a token that is the whole of a credential (a feed, a shared board, a
 * transfer), and the template holds only its placeholder. A request that matched no route is
 * recorded under {@link #UNMATCHED} for the same reason.
 *
 * <p>The buffer is bounded. When the database cannot take the entries, new ones are dropped rather
 * than held, so an outage neither grows the heap nor slows the requests that keep arriving; the
 * next flush logs how many were lost.
 */
@Singleton
public class ApiRequestLogger implements ShutdownFlush, TaskSource {
    /** What a request that matched no route is recorded under. */
    public static final String UNMATCHED = "(unmatched)";

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogger.class);
    private static final int BATCH_SIZE = 100;
    private static final int CAPACITY = 10_000;
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(5);
    private static final Duration PRUNE_INTERVAL = Duration.ofHours(6);

    private final LinkedBlockingQueue<RequestEntry> buffer;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private final Metrics metrics;
    private final TaskScheduler scheduler;

    @Inject
    public ApiRequestLogger(Metrics metrics, TaskScheduler scheduler) {
        this(metrics, scheduler, CAPACITY);
    }

    ApiRequestLogger(Metrics metrics, TaskScheduler scheduler, int capacity) {
        this.metrics = metrics;
        this.scheduler = scheduler;
        this.buffer = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * The route template the request matched, or {@link #UNMATCHED} when it matched none.
     *
     * @param ctx the request, read in an after-handler
     * @return what the request is recorded under
     */
    public static String routeTemplate(Context ctx) {
        Endpoint matched = ctx.endpoints().lastHttpEndpoint();
        return matched == null ? UNMATCHED : matched.path;
    }

    /**
     * Records a request. Called from the API after-handler. Never blocks: when the buffer is full
     * the entry is dropped and counted.
     *
     * @param method     the HTTP method
     * @param template   the route template, as {@link #routeTemplate(Context)} reads it
     * @param statusCode the status the request was answered with
     * @param durationMs how long answering took
     */
    public void record(String method, String template, int statusCode, long durationMs) {
        var entry = new RequestEntry(method, template == null ? UNMATCHED : template, statusCode, (int) durationMs);
        if (!buffer.offer(entry)) {
            dropped.incrementAndGet();
            return;
        }
        if (buffer.size() >= BATCH_SIZE && flushQueued.compareAndSet(false, true)) {
            scheduler.background("api-request-log-flush", this::flush);
        }
    }

    /** The entries waiting for the next flush, oldest first. */
    List<RequestEntry> pending() {
        return List.copyOf(buffer);
    }

    /** How many entries were dropped since the last flush because the buffer was full. */
    long droppedSinceFlush() {
        return dropped.get();
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

    /**
     * Everything the detail page of one endpoint shows: how much traffic it saw, how it answered, and the
     * last requests to it.
     *
     * <p>The path is matched exactly as it was recorded, so the caller asks for the route template with its
     * placeholders rather than for a path somebody actually requested. That is what the list links to, and
     * therefore the only form anybody arrives here holding.
     *
     * @param method the HTTP method
     * @param path   the endpoint, as {@link #routeTemplate(Context)} reads it
     * @return what is known about it, with empty lists where nothing was recorded
     */
    public EndpointDetail getEndpointDetail(String method, String path) {
        var totals = query("""
                        SELECT
                            count(*)         AS cnt,
                            coalesce(avg(duration_ms), 0) AS avg_ms
                        FROM
                            api_request_log
                        WHERE method = :method AND path = :path AND created_at > now() - INTERVAL '3 days';""")
                .single(call().bind("method", method).bind("path", path))
                .map(row -> new Totals(row.getLong("cnt"), row.getDouble("avg_ms")))
                .first()
                .orElse(new Totals(0L, 0.0));

        var statusCodes = query("""
                        SELECT
                            status_code,
                            count(*) AS cnt
                        FROM
                            api_request_log
                        WHERE method = :method AND path = :path AND created_at > now() - INTERVAL '3 days'
                        GROUP BY status_code
                        ORDER BY cnt DESC;""")
                .single(call().bind("method", method).bind("path", path))
                .map(row -> new StatusCount(row.getInt("status_code"), row.getLong("cnt")))
                .all();

        var recent = query("""
                        SELECT
                            created_at,
                            method,
                            path,
                            status_code,
                            duration_ms
                        FROM
                            api_request_log
                        WHERE method = :method AND path = :path
                        ORDER BY created_at DESC
                        LIMIT 100;""")
                .single(call().bind("method", method).bind("path", path))
                .map(row -> new LoggedRequest(
                        row.get("created_at", INSTANT_TIMESTAMP),
                        row.getString("method"),
                        row.getString("path"),
                        row.getInt("status_code"),
                        row.getInt("duration_ms")))
                .all();

        return new EndpointDetail(method, path, totals.avgDurationMs(), totals.requestCount(), statusCodes, recent);
    }

    @Override
    public String name() {
        return "API request log";
    }

    /**
     * Writes everything buffered, batch by batch, until the buffer is empty or a batch cannot be
     * written.
     */
    @Override
    public void flushAll() {
        boolean written = true;
        while (written && !buffer.isEmpty()) {
            written = flush();
        }
    }

    /**
     * Writes one batch of up to twice the batch size.
     *
     * @return whether the batch was written; {@code false} when it could not be
     */
    boolean flush() {
        flushQueued.set(false);
        long lost = dropped.getAndSet(0);
        if (lost > 0) {
            log.warn("Dropped {} API request log entries because the buffer was full", lost);
        }
        var batch = new ArrayList<RequestEntry>();
        buffer.drainTo(batch, BATCH_SIZE * 2);
        if (batch.isEmpty()) return true;

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
            return true;
        } catch (Exception e) {
            log.warn("Failed to flush API request log batch ({} entries)", batch.size(), e);
            return false;
        }
    }

    /**
     * Deletes entries older than {@link Metrics#requestStatsRetentionDays()}. The days are bound
     * through {@code make_interval}, because a bound value is not expanded inside an
     * {@code INTERVAL} literal.
     */
    void prune() {
        try {
            int days = Math.max(1, metrics.requestStatsRetentionDays());
            query("DELETE FROM api_request_log WHERE created_at < now() - make_interval(days := :days);")
                    .single(call().bind("days", days))
                    .delete();
        } catch (Exception e) {
            log.warn("Failed to prune old API request log entries", e);
        }
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

    /** How often one endpoint answered with one status. */
    public record StatusCount(int statusCode, long count) {}

    /** One request, as the detail page lists it. */
    public record LoggedRequest(Instant timestamp, String method, String path, int statusCode, int durationMs) {}

    /**
     * One endpoint, as its own page reads it.
     *
     * @param avgDurationMs   how long it took on average, over the window the log keeps
     * @param requestCount    how many requests it saw in that window
     * @param statusCodes     how it answered, commonest first
     * @param recentRequests  the last hundred requests to it, newest first
     */
    public record EndpointDetail(
            String method,
            String path,
            double avgDurationMs,
            long requestCount,
            List<StatusCount> statusCodes,
            List<LoggedRequest> recentRequests) {}

    private record Totals(long requestCount, double avgDurationMs) {}

    public record HourlyStats(String hour, long requestCount, double avgDurationMs, long errorCount) {}

    record RequestEntry(String method, String path, int statusCode, int durationMs) {}

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask(
                        "api-request-log-flush", Schedule.fixedRate(FLUSH_INTERVAL, FLUSH_INTERVAL), this::flush),
                new ScheduledTask(
                        "api-request-log-prune", Schedule.fixedRate(Duration.ofHours(1), PRUNE_INTERVAL), this::prune));
    }
}
