/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record StationEvent(
        int id,
        int stationId,
        String name,
        String description,
        EventType eventType,
        Integer dayOfWeek,
        Instant startTime,
        Instant endTime,
        Integer templateId,
        boolean requiresRegistration,
        Instant registrationDeadline,
        boolean requiresConfirmation,
        Integer categoryId) {

    public static RowMapping<StationEvent> map() {
        return row -> new StationEvent(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                EventType.valueOf(row.getString("event_type")),
                row.getObject("day_of_week", Integer.class),
                row.get("start_time", INSTANT_TIMESTAMP),
                row.get("end_time", INSTANT_TIMESTAMP),
                row.getObject("template_id", Integer.class),
                row.getBoolean("requires_registration"),
                row.get("registration_deadline", INSTANT_TIMESTAMP),
                row.getBoolean("requires_confirmation"),
                row.getObject("category_id", Integer.class));
    }

    public enum EventType {
        ONE_TIME,
        RECURRING,
        MONTHLY_FIRST,
        QUARTERLY,
        YEARLY
    }

    public boolean isRecurring() {
        return eventType != EventType.ONE_TIME;
    }
}
