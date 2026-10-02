/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

/**
 * The SQL that asks whether an appointment was called off, for the reads of every event repository.
 *
 * <p>A one-time appointment is called off by its one date, so a read about one-time appointments
 * that has to leave the cancelled ones out asks for a date row rather than for the series flag.
 */
public final class CancellationSql {

    /**
     * The condition, for a query reading {@code station_event} as {@code e}, that no date of the event
     * is off.
     */
    public static final String NO_DATE_CANCELLED = """
            NOT EXISTS (SELECT 1
                        FROM event_date_cancellation edc
                        WHERE edc.event_id = e.id
                          AND edc.restored_at IS NULL)""";

    private CancellationSql() {}
}
