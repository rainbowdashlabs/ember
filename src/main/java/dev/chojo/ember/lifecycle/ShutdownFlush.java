/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

/**
 * Buffered state that has to reach the database before the connection pool closes, registered through the
 * {@code ShutdownFlush} multibinder. The {@link Lifecycle} calls it after the HTTP server and the scheduled work
 * have stopped, so nothing adds to a buffer once it has been written.
 */
public interface ShutdownFlush {

    /**
     * The name the flush is logged under.
     *
     * @return the flush's name
     */
    String name();

    /**
     * Writes everything still buffered, including data of periods that are not over yet.
     */
    void flushAll();

    /**
     * Where the flush stands among the others: lower runs first. The application log goes last, so the
     * lines logged while shutting down reach its table too.
     *
     * @return the position, {@code 0} by default
     */
    default int order() {
        return 0;
    }
}
