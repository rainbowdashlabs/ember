/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
        Integer registrationLimit,
        boolean cancelled,
        Instant cancelledAt,
        String cancelReason,
        Integer minRegistrations,
        Instant thresholdDate,
        boolean thresholdNotified,
        Integer registrationCloseDays) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<StationEvent> map() {
        return row -> new StationEvent(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getEnum("event_type", EventType.class),
                row.getObject("day_of_week", Integer.class),
                row.get("start_time", INSTANT_TIMESTAMP),
                row.get("end_time", INSTANT_TIMESTAMP),
                row.getObject("template_id", Integer.class),
                row.getBoolean("requires_registration"),
                row.get("registration_deadline", INSTANT_TIMESTAMP),
                row.getBoolean("requires_confirmation"),
                row.getObject("category_id", Integer.class),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getBoolean("restricted"),
                row.getObject("public", Boolean.class),
                row.getObject("registration_limit", Integer.class),
                row.getBoolean("cancelled"),
                row.get("cancelled_at", INSTANT_TIMESTAMP),
                row.getString("cancel_reason"),
                row.getObject("min_registrations", Integer.class),
                row.get("threshold_date", INSTANT_TIMESTAMP),
                row.getBoolean("threshold_notified"),
                row.getObject("registration_close_days", Integer.class));
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
     * Returns whether this recurring event falls on the given date, matching the configured
     * weekday and recurrence schedule. One-time events never match.
     *
     * @param date the calendar date to test
     * @return true if the event recurs on that date
     */
    public boolean occursOn(LocalDate date) {
        if (dayOfWeek == null || dayOfWeek != date.getDayOfWeek().getValue()) {
            return false;
        }
        return switch (eventType) {
            case RECURRING -> true;
            case MONTHLY_FIRST -> date.getDayOfMonth() <= 7;
            case QUARTERLY -> date.getDayOfMonth() <= 7 && (date.getMonthValue() - 1) % 3 == 0;
            case YEARLY ->
                startTime != null
                        && startTime.atZone(ZoneOffset.UTC).getMonthValue() == date.getMonthValue()
                        && startTime.atZone(ZoneOffset.UTC).getDayOfMonth() == date.getDayOfMonth();
            default -> false;
        };
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
