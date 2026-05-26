/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an event belonging to a station, which can be one-time or recurring on various schedules.
 *
 * @param id                   the unique identifier of the event
 * @param stationId            the station this event belongs to
 * @param name                 the display name of the event
 * @param description          an optional description
 * @param eventType            the recurrence type of the event
 * @param dayOfWeek            the ISO day of week (1=Monday..7=Sunday) for recurring events, or null for one-time
 * @param startTime            the start time of the event
 * @param endTime              the end time of the event
 * @param templateId           an optional attendance template ID linked to this event
 * @param requiresRegistration whether members must register before attending
 * @param registrationDeadline the deadline for registration, or null if no deadline
 * @param requiresConfirmation whether registrations must be confirmed by a manager
 * @param categoryId           the optional category this event is assigned to
 */
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
        Integer categoryId,
        RestrictionMode restrictionMode,
        boolean restricted,
        Boolean isPublic,
        Integer registrationLimit) {

    /**
     * Creates a row mapping for database result set conversion.
     */
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
                row.getObject("category_id", Integer.class),
                RestrictionMode.valueOf(row.getString("restriction_mode")),
                row.getBoolean("restricted"),
                row.getObject("public", Boolean.class),
                row.getObject("registration_limit", Integer.class));
    }

    /**
     * Returns whether this event recurs on a schedule.
     *
     * @return true if the event type is not {@link EventType#ONE_TIME}
     */
    public boolean isRecurring() {
        return eventType != EventType.ONE_TIME;
    }

    /**
     * The recurrence schedule types for events.
     */
    public enum EventType {
        ONE_TIME,
        RECURRING,
        MONTHLY_FIRST,
        QUARTERLY,
        YEARLY
    }
}
