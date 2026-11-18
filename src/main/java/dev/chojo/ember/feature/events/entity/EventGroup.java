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
 * An event group: one-time appointments of a station that belong together and are signed up for once.
 *
 * <p>The event group holds what its parts share: the name, a description, the sign-up with its limit,
 * deadline and confirmation, and who may see and join it. Each part stays an ordinary appointment
 * with its own time, place and attendance sheet.
 *
 * @param id                  the event group
 * @param stationId           the station it and all its parts belong to
 * @param details             what a manager sets on it: name, description, the sign-up with its limit,
 *                            deadline and confirmation, its colour and its minimum
 * @param restrictionMode     how the entries of who may sign up combine
 * @param viewRestrictionMode how the entries of who may see the event group combine
 * @param cancelled           whether the whole event group was called off
 * @param cancelledAt         when it was called off
 * @param cancelReason        why it was called off
 */
public record EventGroup(
        int id,
        int stationId,
        EventGroupDetails details,
        RestrictionMode restrictionMode,
        RestrictionMode viewRestrictionMode,
        boolean cancelled,
        @Nullable Instant cancelledAt,
        @Nullable String cancelReason) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS =
            "id, station_id, name, description, registration_limit, registration_deadline, requires_confirmation, restriction_mode, view_restriction_mode, cancelled, cancelled_at, cancel_reason, color, min_registrations, threshold_days, part_registration_limit, queue_order";

    /** Reads an event group from a row holding {@link #COLUMNS}. */
    public static RowMapping<EventGroup> map() {
        return row -> new EventGroup(
                row.getInt("id"),
                row.getInt("station_id"),
                new EventGroupDetails(
                        row.getString("name"),
                        row.getString("description"),
                        row.getObject("registration_limit", Integer.class),
                        row.get("registration_deadline", INSTANT_TIMESTAMP),
                        row.getBoolean("requires_confirmation"),
                        row.getString("color"),
                        row.getObject("min_registrations", Integer.class),
                        row.getObject("threshold_days", Integer.class),
                        row.getObject("part_registration_limit", Integer.class),
                        row.getEnum("queue_order", QueueOrder.class)),
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
        var deadline = details.registrationDeadline();
        return deadline != null && now.isAfter(deadline);
    }
}
