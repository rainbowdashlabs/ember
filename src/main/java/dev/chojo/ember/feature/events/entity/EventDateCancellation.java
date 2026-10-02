/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One date of an appointment that was called off, a one-time appointment included.
 *
 * <p>The date is off while {@code restoredAt} is empty. A restored row is kept rather than removed,
 * because it is what tells the minimum-registration check that a manager brought the date back on
 * purpose and it is not to be called off again.
 *
 * @param eventId     the appointment
 * @param eventDate   the date on the station's calendar
 * @param cause       who called it off
 * @param reason      the reason a manager gave, null for the check and where none was given
 * @param cancelledAt when it was called off, the last time where that happened more than once
 * @param cancelledBy the manager who called it off, null for the check
 * @param restoredAt  when a manager brought it back, null while it stays off
 */
public record EventDateCancellation(
        int eventId,
        LocalDate eventDate,
        CancellationCause cause,
        String reason,
        Instant cancelledAt,
        Integer cancelledBy,
        Instant restoredAt) {

    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<EventDateCancellation> map() {
        return row -> new EventDateCancellation(
                row.getInt("event_id"),
                row.getObject("event_date", LocalDate.class),
                row.getEnum("cause", CancellationCause.class),
                row.getString("reason"),
                row.get("cancelled_at", INSTANT_TIMESTAMP),
                row.getObject("cancelled_by", Integer.class),
                row.get("restored_at", INSTANT_TIMESTAMP));
    }

    /** Whether the date is off, which it is until a manager restores it. */
    public boolean isActive() {
        return restoredAt == null;
    }

    /** What a reader is told about this cancellation. */
    public CancellationNotice notice() {
        return new CancellationNotice(eventDate, cause, reason, cancelledAt);
    }
}
