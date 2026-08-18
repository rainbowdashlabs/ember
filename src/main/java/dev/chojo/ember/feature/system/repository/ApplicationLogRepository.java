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
     * Searches the log, newest first.
     *
     * @param levels only these severities, or empty for all of them
     * @param search a fragment to look for in the message or the logger name, or null for all
     * @param before only lines older than this row, for paging further back; null starts at the top
     * @param limit  how many lines at most
     */
    public List<LogEntry> search(List<String> levels, String search, Long before, int limit) {
        String term = pattern(search);
        var wanted = levels.stream().filter(KNOWN_LEVELS::contains).toList();
        String levelFilter =
                wanted.isEmpty() ? "" : wanted.stream().collect(Collectors.joining("', '", " AND level IN ('", "')"));
        String beforeFilter = before == null ? "" : " AND id < :before";
        String searchFilter = term == null ? "" : " AND (message ILIKE :search OR logger ILIKE :search)";
        var callable = call().bind("limit", limit);
        if (before != null) callable = callable.bind("before", before);
        if (term != null) callable = callable.bind("search", term);
        return query("""
                        SELECT
                            id, logged_at, level, logger, thread, message, throwable
                        FROM
                            application_log
                        WHERE
                            1 = 1 %s %s %s
                        ORDER BY
                            id DESC
                        LIMIT :limit;""", levelFilter, beforeFilter, searchFilter)
                .single(callable)
                .map(LOG_ENTRY)
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
