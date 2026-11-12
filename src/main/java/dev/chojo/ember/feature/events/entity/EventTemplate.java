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
 * @param registrationDeadlineOffsetHours how many hours before the start sign-up closes, or null for no deadline
 * @param requiresConfirmation            whether sign-ups wait for a manager to confirm them
 * @param restrictionMode                 how the sign-up audience combines
 * @param viewRestrictionMode             how the view audience combines
 * @param attendanceTemplateId            the attendance sheet the appointment is taken on
 * @param registrationLimit               how many places the appointment has, or null for no limit
 * @param minRegistrations                how many accepted sign-ups each date needs, or null for no minimum
 * @param thresholdDays                   how many days before each date the minimum must be reached
 * @param registrationCloseDays           how many days before each date sign-up closes
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
        @Nullable Integer registrationDeadlineOffsetHours,
        @Nullable Boolean requiresConfirmation,
        @Nullable RestrictionMode restrictionMode,
        RestrictionMode viewRestrictionMode,
        @Nullable Integer attendanceTemplateId,
        @Nullable Integer registrationLimit,
        @Nullable Integer minRegistrations,
        @Nullable Integer thresholdDays,
        @Nullable Integer registrationCloseDays) {

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
                row.getObject("registration_deadline_offset_hours", Integer.class),
                row.getObject("requires_confirmation", Boolean.class),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getEnum("view_restriction_mode", RestrictionMode.class),
                row.getObject("attendance_template_id", Integer.class),
                row.getObject("registration_limit", Integer.class),
                row.getObject("min_registrations", Integer.class),
                row.getObject("threshold_days", Integer.class),
                row.getObject("registration_close_days", Integer.class));
    }
}
