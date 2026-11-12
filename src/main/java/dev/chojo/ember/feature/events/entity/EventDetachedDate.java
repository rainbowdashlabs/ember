/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

/**
 * A date a series no longer holds because it became an appointment of its own.
 *
 * @param eventId   the series the date was taken out of
 * @param eventDate the date, on the station's clock
 */
public record EventDetachedDate(int eventId, LocalDate eventDate) {

    /** Reads a row of {@code station_event_detached_date}. */
    public static RowMapping<EventDetachedDate> map() {
        return row -> new EventDetachedDate(row.getInt("event_id"), row.getObject("event_date", LocalDate.class));
    }
}
