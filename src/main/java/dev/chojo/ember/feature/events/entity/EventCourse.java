/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A course: one-time appointments of a station that belong together and are signed up for once.
 *
 * <p>The course holds what its parts share: the name, a description, the sign-up with its limit,
 * deadline and confirmation, and who may see and join it. Each part stays an ordinary appointment
 * with its own time, place and attendance sheet.
 *
 * @param id                   the course
 * @param stationId            the station it and all its parts belong to
 * @param name                 the name shown on every part
 * @param description          what the course is about, as Markdown
 * @param registrationLimit    how many people the course takes, or null for no limit
 * @param registrationDeadline the instant sign-ups close, or null for no deadline
 * @param requiresConfirmation whether a manager confirms each sign-up
 * @param restrictionMode      how the entries of who may sign up combine
 * @param viewRestrictionMode  how the entries of who may see the course combine
 * @param cancelled            whether the whole course was called off
 * @param cancelledAt          when it was called off
 * @param cancelReason         why it was called off
 */
public record EventCourse(
        int id,
        int stationId,
        String name,
        @Nullable String description,
        @Nullable Integer registrationLimit,
        @Nullable Instant registrationDeadline,
        boolean requiresConfirmation,
        RestrictionMode restrictionMode,
        RestrictionMode viewRestrictionMode,
        boolean cancelled,
        @Nullable Instant cancelledAt,
        @Nullable String cancelReason) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS =
            "id, station_id, name, description, registration_limit, registration_deadline, requires_confirmation, restriction_mode, view_restriction_mode, cancelled, cancelled_at, cancel_reason";

    /** Reads a course from a row holding {@link #COLUMNS}. */
    public static RowMapping<EventCourse> map() {
        return row -> new EventCourse(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getObject("registration_limit", Integer.class),
                row.get("registration_deadline", INSTANT_TIMESTAMP),
                row.getBoolean("requires_confirmation"),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getEnum("view_restriction_mode", RestrictionMode.class),
                row.getBoolean("cancelled"),
                row.get("cancelled_at", INSTANT_TIMESTAMP),
                row.getString("cancel_reason"));
    }

    /**
     * Whether sign-ups are closed by the deadline.
     *
     * @param now the instant asked about
     * @return true once a deadline has passed
     */
    public boolean deadlinePassed(Instant now) {
        return registrationDeadline != null && now.isAfter(registrationDeadline);
    }
}
