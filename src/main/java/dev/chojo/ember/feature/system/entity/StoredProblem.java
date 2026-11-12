/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A problem as the database keeps it: every occurrence of one fault counted on one row.
 *
 * @param id               the row, stable across restarts
 * @param fingerprint      what makes two log events the same problem
 * @param level            what kind of problem it is
 * @param logger           which logger reported it
 * @param message          the message of the first occurrence
 * @param exceptionClass   the class of the exception, or null for a problem logged without one
 * @param exceptionMessage the message of the latest exception, or null
 * @param stacktrace       the stacktrace of the latest occurrence, or null
 * @param distinctMessages the different messages it was logged with, oldest first
 * @param count            how often it happened
 * @param firstSeen        when it happened first
 * @param lastSeen         when it happened last
 * @param state            where it stands with the administrators
 * @param stateChangedAt   when the state last changed, or null where it never did
 * @param reopenedCount    how often it came back after it was resolved
 */
public record StoredProblem(
        long id,
        String fingerprint,
        ProblemLevel level,
        String logger,
        String message,
        @Nullable String exceptionClass,
        @Nullable String exceptionMessage,
        @Nullable String stacktrace,
        List<String> distinctMessages,
        long count,
        Instant firstSeen,
        Instant lastSeen,
        ProblemState state,
        @Nullable Instant stateChangedAt,
        int reopenedCount) {

    /** The columns {@link #map()} reads, in a form a select can name. */
    public static final String COLUMNS =
            "id, fingerprint, level, logger, message, exception_class, exception_message, stacktrace, distinct_messages,"
                    + " count, first_seen, last_seen, state, state_changed_at, reopened_count";

    /** Reads a row selected with {@link #COLUMNS}. */
    public static RowMapping<StoredProblem> map() {
        return row -> new StoredProblem(
                row.getLong("id"),
                row.getString("fingerprint"),
                row.getEnum("level", ProblemLevel.class),
                row.getString("logger"),
                row.getString("message"),
                row.getString("exception_class"),
                row.getString("exception_message"),
                row.getString("stacktrace"),
                texts(row.getArray("distinct_messages")),
                row.getLong("count"),
                row.get("first_seen", INSTANT_TIMESTAMP),
                row.get("last_seen", INSTANT_TIMESTAMP),
                row.getEnum("state", ProblemState.class),
                row.get("state_changed_at", INSTANT_TIMESTAMP),
                row.getInt("reopened_count"));
    }

    private static List<String> texts(@Nullable Array array) throws SQLException {
        if (array == null) return List.of();
        return List.of((String[]) array.getArray());
    }
}
