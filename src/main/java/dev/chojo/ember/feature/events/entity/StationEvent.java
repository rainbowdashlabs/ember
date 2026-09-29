/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

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
 * @param repeatUntil          the last day a recurring event may fall on, or null where it has no end
 * @param repeatCount          how many times a recurring event takes place in total, counted from its
 *                             first date, or null where it has no end. Never set together with
 *                             {@code repeatUntil}: they are two ways of saying the same thing
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
        RestrictionMode viewRestrictionMode,
        boolean restricted,
        Boolean isPublic,
        Integer registrationLimit,
        boolean cancelled,
        Instant cancelledAt,
        String cancelReason,
        Integer minRegistrations,
        Instant thresholdDate,
        boolean thresholdNotified,
        Integer registrationCloseDays,
        LocalDate repeatUntil,
        Integer repeatCount) {

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
                row.getEnum("view_restriction_mode", RestrictionMode.class),
                row.getBoolean("restricted"),
                row.getObject("public", Boolean.class),
                row.getObject("registration_limit", Integer.class),
                row.getBoolean("cancelled"),
                row.get("cancelled_at", INSTANT_TIMESTAMP),
                row.getString("cancel_reason"),
                row.getObject("min_registrations", Integer.class),
                row.get("threshold_date", INSTANT_TIMESTAMP),
                row.getBoolean("threshold_notified"),
                row.getObject("registration_close_days", Integer.class),
                row.getObject("repeat_until", LocalDate.class),
                row.getObject("repeat_count", Integer.class));
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
     * When this appointment runs on a given day.
     *
     * <p>A one-off appointment carries its own dates and keeps them, which is what lets one run over
     * a weekend. A repeating one carries only the time of day and the length of the occasion: the
     * date beside them is the day somebody first configured it and says nothing about the occurrence
     * being recorded, so those are placed on the day asked for. Taking that date as it stood is what
     * once opened a sheet for a Tuesday two years ago.
     *
     * @param date the day the occurrence falls on
     * @return the two moments it runs between, empty where the appointment has no times at all
     */
    public Optional<Span> occurrenceOn(LocalDate date) {
        if (startTime == null) return Optional.empty();
        Instant end = endTime != null && endTime.isAfter(startTime) ? endTime : startTime;
        if (eventType == EventType.ONE_TIME) return Optional.of(new Span(startTime, end));
        Instant start =
                date.atTime(startTime.atZone(ZoneOffset.UTC).toLocalTime()).toInstant(ZoneOffset.UTC);
        return Optional.of(new Span(start, start.plus(Duration.between(startTime, end))));
    }

    /**
     * The two moments an occasion runs between.
     *
     * @param start when it begins
     * @param end   when it ends, which may be on a later day
     */
    public record Span(Instant start, Instant end) {}

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
