/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.system.service.DatabaseLogAppender.LogLine;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * The application log as rows: written by the batch, read by search, pruned by age.
 */
@Singleton
public class ApplicationLogRepository {

    /**
     * The only severities that may reach the statement. Anything else is dropped rather than
     * written in, which is what makes writing them in safe at all.
     */
    private static final Set<String> KNOWN_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    /**
     * One line as it comes back out.
     *
     * @param id        the row, which the client uses to page further back
     * @param loggedAt  when it was logged
     * @param level     its severity
     * @param logger    which logger emitted it
     * @param thread    the thread it came from
     * @param message   the formatted line
     * @param throwable the stack trace it carried, or null
     */
    public record LogEntry(
            long id, Instant loggedAt, String level, String logger, String thread, String message, String throwable) {}

    private static final RowMapping<LogEntry> LOG_ENTRY = row -> new LogEntry(
            row.getLong("id"),
            row.get("logged_at", INSTANT_TIMESTAMP),
            row.getString("level"),
            row.getString("logger"),
            row.getString("thread"),
            row.getString("message"),
            row.getString("throwable"));

    /**
     * Writes a batch of lines as one batch.
     *
     * <p>A loud instance logs far faster than it can pay for a round trip per line, and the whole
     * point of queueing in the appender is lost if the queue is then emptied one statement at a
     * time. The lines go out on a single prepared statement.
     */
    public void write(List<LogLine> lines) {
        if (lines.isEmpty()) return;
        var calls = lines.stream()
                .map(line -> call().bind("logged_at", line.loggedAt(), INSTANT_TIMESTAMP)
                        .bind("level", line.level())
                        .bind("logger", line.logger())
                        .bind("thread", line.thread() == null ? "" : line.thread())
                        .bind("message", line.message() == null ? "" : line.message())
                        .bind("throwable", line.throwable()))
                .map(Call.class::cast)
                .toList();
        query("""
                INSERT
                INTO
                    application_log(logged_at, level, logger, thread, message, throwable)
                VALUES
                    (:logged_at, :level, :logger, :thread, :message, :throwable);""").batch(calls).insert();
    }

    /**
     * How a thread is named once the numbering is taken out, so a pool is one entry rather than a
     * hundred. Matches the expression the index is built on.
     */
    private static final String THREAD_GROUP = "regexp_replace(thread, '[0-9]+', '#', 'g')";

    /**
     * A value the log can be narrowed to, and how many lines carry it.
     *
     * @param value what to filter by, which is also what is shown
     * @param count how many lines match it under the filter that produced this list
     */
    public record Facet(String value, int count) {}

    private static final RowMapping<Facet> FACET = row -> new Facet(row.getString(1), row.getInt(2));

    /**
     * What the caller narrowed the log to, as a WHERE fragment and the values it binds. Shared so
     * a facet count and the list it sits next to cannot come from different filters.
     */
    private record Filter(String sql, Call call) {}

    private Filter filter(List<String> levels, String search, String logger, String threadGroup, Long before) {
        String term = pattern(search);
        var wanted = levels.stream().filter(KNOWN_LEVELS::contains).toList();
        var sql = new StringBuilder();
        var callable = call();
        if (!wanted.isEmpty()) {
            sql.append(wanted.stream().collect(Collectors.joining("', '", " AND level IN ('", "')")));
        }
        if (term != null) {
            sql.append(" AND (message ILIKE :search OR logger ILIKE :search)");
            callable = callable.bind("search", term);
        }
        if (logger != null && !logger.isBlank()) {
            sql.append(" AND logger = :logger");
            callable = callable.bind("logger", logger);
        }
        if (threadGroup != null && !threadGroup.isBlank()) {
            sql.append(" AND ").append(THREAD_GROUP).append(" = :thread_group");
            callable = callable.bind("thread_group", threadGroup);
        }
        if (before != null) {
            sql.append(" AND id < :before");
            callable = callable.bind("before", before);
        }
        return new Filter(sql.toString(), callable);
    }

    /**
     * Searches the log, newest first.
     *
     * @param levels      only these severities, or empty for all of them
     * @param search      a fragment to look for in the message or the logger name, or null for all
     * @param logger      only lines from this logger, or null for all
     * @param threadGroup only lines from threads with this name once numbered off, or null for all
     * @param before      only lines older than this row, for paging further back; null starts at the top
     * @param limit       how many lines at most
     */
    public List<LogEntry> search(
            List<String> levels, String search, String logger, String threadGroup, Long before, int limit) {
        var filter = filter(levels, search, logger, threadGroup, before);
        return query("""
                        SELECT
                            id, logged_at, level, logger, thread, message, throwable
                        FROM
                            application_log
                        WHERE
                            1 = 1 %s
                        ORDER BY
                            id DESC
                        LIMIT :limit;""", filter.sql())
                .single(filter.call().bind("limit", limit))
                .map(LOG_ENTRY)
                .all();
    }

    /**
     * The loggers present under the current filter, busiest first, counted over everything the
     * filter matches rather than over the page on screen.
     *
     * @param nameSearch a fragment of the logger name, which is how one below the limit is reached
     */
    public List<Facet> loggerFacets(
            List<String> levels, String search, String threadGroup, String nameSearch, int limit) {
        return facets("logger", levels, search, null, threadGroup, nameSearch, limit);
    }

    /**
     * The threads present under the current filter, busiest first, numbered off.
     *
     * @param nameSearch a fragment of the thread name, matched against the numbered-off form
     */
    public List<Facet> threadFacets(List<String> levels, String search, String logger, String nameSearch, int limit) {
        return facets(THREAD_GROUP, levels, search, logger, null, nameSearch, limit);
    }

    private List<Facet> facets(
            String expression,
            List<String> levels,
            String search,
            String logger,
            String threadGroup,
            String nameSearch,
            int limit) {
        var filter = filter(levels, search, logger, threadGroup, null);
        String term = pattern(nameSearch);
        String nameFilter = term == null ? "" : " AND %s ILIKE :facet_search".formatted(expression);
        var callable = filter.call().bind("limit", limit);
        if (term != null) callable = callable.bind("facet_search", term);
        return query("""
                        SELECT
                            %s AS value, count(*)::INT AS cnt
                        FROM
                            application_log
                        WHERE
                            1 = 1 %s %s
                        GROUP BY
                            1
                        ORDER BY
                            cnt DESC, 1
                        LIMIT :limit;""", expression, filter.sql(), nameFilter)
                .single(callable)
                .map(FACET)
                .all();
    }

    /**
     * Whether the table is there yet. Asked before the first write, because a writer that starts
     * before the migration would otherwise report a failure per batch.
     */
    public boolean exists() {
        return count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'application_log';", call())
                > 0;
    }

    /**
     * How many lines are stored.
     */
    public int size() {
        return count("SELECT count(*) FROM application_log;", call());
    }

    /**
     * Removes lines older than the given number of days.
     *
     * @return how many were removed
     */
    public int prune(int keepDays) {
        return query("DELETE FROM application_log WHERE logged_at < now() - make_interval(days => :days);")
                .single(call().bind("days", keepDays))
                .update()
                .rows();
    }

    /**
     * Removes everything, which is what an operator asks for when the log holds something that
     * should not be kept.
     */
    public void clear() {
        query("DELETE FROM application_log;").single(call()).delete();
    }

    private static String pattern(String search) {
        if (search == null || search.isBlank()) return null;
        return "%" + search.trim() + "%";
    }
}
