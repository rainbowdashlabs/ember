/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import java.sql.SQLException;

/**
 * Says in one line what went wrong with a query, for the log line every failed query writes.
 *
 * <p>The query library wraps the database's error in one of its own whose message repeats the whole
 * statement over several lines. A log line that only said "Database query error" left the reader with
 * the stack trace, and wherever the trace is split from its line, as in a log collector that takes
 * every printed line on its own, with nothing at all. The database's own error, first line only, is
 * what tells one failed query from another.
 */
public final class QueryErrors {

    private QueryErrors() {}

    /**
     * The database's own error, first line only.
     *
     * @param error what the query library reported
     * @return the first line of the innermost database error, or of the error itself where it wraps
     *     none
     */
    public static String summary(SQLException error) {
        Throwable innermost = error;
        while (innermost.getCause() instanceof SQLException cause && cause != innermost) {
            innermost = cause;
        }
        String message = innermost.getMessage();
        if (message == null || message.isBlank()) return innermost.getClass().getSimpleName();
        String first = message.strip().lines().findFirst().orElse("");
        return first.isBlank() ? innermost.getClass().getSimpleName() : first.strip();
    }
}
