/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

/**
 * One end of the link between an appointment and the one it took over from.
 *
 * @param eventId the appointment at the other end
 * @param name    its name
 * @param date    the date the newer one took over from
 * @param series  whether the appointment at the other end repeats
 */
public record EventLink(int eventId, String name, LocalDate date, boolean series) {

    /** Reads the other appointment's id, name and kind, and the date from the named column. */
    public static RowMapping<EventLink> map(String dateColumn) {
        return row -> new EventLink(
                row.getInt("id"),
                row.getString("name"),
                row.getObject(dateColumn, LocalDate.class),
                !StationEvent.EventType.ONE_TIME.name().equals(row.getString("event_type")));
    }
}
