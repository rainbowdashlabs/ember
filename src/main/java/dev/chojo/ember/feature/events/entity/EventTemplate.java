/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.events.entity.StationEvent.EventType;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * An appointment template: the settings an appointment made from it starts with.
 *
 * @param id                              the template's id
 * @param stationId                       the station it belongs to
 * @param name                            the template's own name
 * @param title                           the name the appointment takes
 * @param description                     the description the appointment takes
 * @param categoryId                      the category the appointment takes
 * @param eventType                       how the appointment repeats
 * @param requiresRegistration            whether the appointment takes sign-ups
 * @param registrationDeadline            when sign-up closes, counted from each date's start; a
 *                                        template knows no date, so it holds no fixed deadline
 * @param requiresConfirmation            whether sign-ups wait for a manager to confirm them
 * @param restrictionMode                 how the sign-up audience combines
 * @param viewRestrictionMode             how the view audience combines
 * @param attendanceTemplateId            the attendance sheet the appointment is taken on
 * @param registrationLimit               how many places the appointment has, or null for no limit
 * @param minRegistrations                how many accepted sign-ups each date needs, or null for no minimum
 * @param thresholdDays                   how many days before each date the minimum must be reached
 * @param queueOrder                      how the waiting lists of appointments made from it are ordered
 */
public record EventTemplate(
        int id,
        int stationId,
        String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable Integer categoryId,
        @Nullable EventType eventType,
        @Nullable Boolean requiresRegistration,
        RegistrationDeadline registrationDeadline,
        @Nullable Boolean requiresConfirmation,
        @Nullable RestrictionMode restrictionMode,
        RestrictionMode viewRestrictionMode,
        @Nullable Integer attendanceTemplateId,
        @Nullable Integer registrationLimit,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays,
        @Nullable QueueOrder queueOrder) {

    /** Reads a missing deadline as none. */
    public EventTemplate {
        registrationDeadline = Objects.requireNonNullElse(registrationDeadline, RegistrationDeadline.none());
    }

    /**
     * A template that sets no waiting list order.
     */
    public EventTemplate(
            int id,
            int stationId,
            String name,
            @Nullable String title,
            @Nullable String description,
            @Nullable Integer categoryId,
            @Nullable EventType eventType,
            @Nullable Boolean requiresRegistration,
            RegistrationDeadline registrationDeadline,
            @Nullable Boolean requiresConfirmation,
            @Nullable RestrictionMode restrictionMode,
            RestrictionMode viewRestrictionMode,
            @Nullable Integer attendanceTemplateId,
            @Nullable Integer registrationLimit,
            @Nullable Integer minRegistrations,
            @Nullable Integer thresholdDays) {
        this(
                id,
                stationId,
                name,
                title,
                description,
                categoryId,
                eventType,
                requiresRegistration,
                registrationDeadline,
                requiresConfirmation,
                restrictionMode,
                viewRestrictionMode,
                attendanceTemplateId,
                registrationLimit,
                minRegistrations,
                thresholdDays,
                null);
    }

    public static RowMapping<EventTemplate> map() {
        return row -> new EventTemplate(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("title"),
                row.getString("description"),
                row.getObject("category_id", Integer.class),
                row.getEnum("event_type", EventType.class),
                row.getObject("requires_registration", Boolean.class),
                RegistrationDeadline.read(row),
                row.getObject("requires_confirmation", Boolean.class),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getEnum("view_restriction_mode", RestrictionMode.class),
                row.getObject("attendance_template_id", Integer.class),
                row.getObject("registration_limit", Integer.class),
                row.getObject("min_registrations", Integer.class),
                row.getObject("threshold_days", Integer.class),
                row.getEnum("queue_order", QueueOrder.class));
    }
}
