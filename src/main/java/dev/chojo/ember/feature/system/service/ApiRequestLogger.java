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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

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

    /**
     * Everything the detail page of one endpoint shows: how much traffic it saw, how it answered, and the
     * last requests to it.
     *
     * <p>The path is matched exactly as it was recorded, so the caller asks for the reduced form with its
     * placeholders rather than for a path somebody actually requested. That is what the list links to, and
     * therefore the only form anybody arrives here holding.
     *
     * @param method the HTTP method
     * @param path   the endpoint, as {@link #normalizePath(String)} leaves it
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
     * Replaces the segments of a path that are an identifier with {@code {id}}, so that every request to
     * one endpoint is counted as that endpoint rather than as one endpoint per identifier.
     *
     * <p>A segment counts where the whole of it is an identifier: all digits, or a uuid. The whole of it
     * matters. Matching digits anywhere rewrote the inside of any segment that merely began with one, which
     * is not rare: a feed token beginning with a digit became {@code {id}hgEV4EC3...} and a uuid became
     * {@code {id}-0000-4000-a000-000000000003}. Neither is a path anybody could ask for, so the detail page
     * of either was permanently empty.
     *
     * @param path the path as it was requested
     * @return the path with its identifier segments replaced
     */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return path;
        var out = new StringBuilder(path.length());
        int from = 0;
        while (from <= path.length()) {
            int slash = path.indexOf('/', from);
            int end = slash < 0 ? path.length() : slash;
            if (isIdentifier(path, from, end)) {
                out.append("{id}");
            } else {
                out.append(path, from, end);
            }
            if (slash < 0) break;
            out.append('/');
            from = slash + 1;
        }
        return out.toString();
    }

    /**
     * Whether this segment names one particular thing rather than a part of the route.
     *
     * <p>A number and a uuid both do, and both have to collapse or the page lists one endpoint per row of
     * the database. A slug does not: it names a station and the requests to one station's page are worth
     * seeing as their own line.
     */
    private static boolean isIdentifier(String path, int from, int end) {
        return allDigits(path, from, end) || isUuid(path, from, end) || isSecret(path, from, end);
    }

    /**
     * Whether this segment is a token somebody was handed rather than a name somebody chose.
     *
     * <p>A feed address carries one, and it is the whole of the credential: left as it was requested, the
     * statistics grow a row per subscriber and write down the token that opens the feed. It is not a uuid
     * and not a number, so neither of the other two rules reaches it.
     *
     * <p>Told apart from a slug by what random bytes look like rather than by length alone. A station's
     * name can be longer than a token ("freiwillige-feuerwehr-musterstadt-nord" is), but it is words, so
     * it carries neither a capital nor a digit. Thirty-two characters of base64url holding both is not a
     * name anybody typed.
     */
    private static boolean isSecret(String path, int from, int end) {
        if (end - from < 32) return false;
        boolean capital = false;
        boolean digit = false;
        for (int i = from; i < end; i++) {
            char c = path.charAt(i);
            if (Character.isUpperCase(c)) capital = true;
            else if (Character.isDigit(c)) digit = true;
            else if (!Character.isLowerCase(c) && c != '-' && c != '_') return false;
        }
        return capital && digit;
    }

    /** Whether everything between these two points is a digit, and there is at least one of them. */
    private static boolean allDigits(String path, int from, int end) {
        if (end <= from) return false;
        for (int i = from; i < end; i++) {
            if (!Character.isDigit(path.charAt(i))) return false;
        }
        return true;
    }

    /** The 8-4-4-4-12 shape, checked by hand so no expression has to be compiled on every request. */
    private static boolean isUuid(String path, int from, int end) {
        if (end - from != 36) return false;
        for (int i = 0; i < 36; i++) {
            char c = path.charAt(from + i);
            boolean dash = i == 8 || i == 13 || i == 18 || i == 23;
            if (dash != (c == '-')) return false;
            if (!dash && Character.digit(c, 16) < 0) return false;
        }
        return true;
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

    private record RequestEntry(String method, String path, int statusCode, int durationMs) {}
}
