/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Duration;
import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * An attendance session representing a specific occurrence where attendance is tracked.
 *
 * @param id         unique session identifier
 * @param templateId the template this session was created from
 * @param startTime  scheduled start time of the session
 * @param endTime    scheduled end time of the session
 * @param createdAt  timestamp when the session was created
 * @param eventId       optional linked event identifier
 * @param title         display title of the session
 * @param unlockedUntil when a manager's reopening runs out, null where nobody reopened this sheet
 * @param lockedAt      when somebody closed this sheet by hand, null where nobody did
 */
public record AttendanceSession(
        int id,
        int templateId,
        Instant startTime,
        Instant endTime,
        Instant createdAt,
        Integer eventId,
        String title,
        Instant unlockedUntil,
        Instant lockedAt) {

    /**
     * Whether the sheet may still be written to at the given moment.
     *
     * <p>Closing it by hand outranks everything, because closing a sheet somebody has just reopened
     * has to mean something. Otherwise a reopening that is still running keeps it open, and failing
     * both, its age against the configured span decides.
     *
     * @param now             the moment to judge by
     * @param freezeAfterDays how many days after its evening a sheet closes on its own
     * @return true where the sheet is open for writing
     */
    public boolean isOpen(Instant now, int freezeAfterDays) {
        if (lockedAt != null) return false;
        if (unlockedUntil != null && unlockedUntil.isAfter(now)) return true;
        return endTime.plus(Duration.ofDays(freezeAfterDays)).isAfter(now);
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AttendanceSession> map() {
        return row -> new AttendanceSession(
                row.getInt("id"),
                row.getInt("template_id"),
                row.get("start_time", INSTANT_TIMESTAMP),
                row.get("end_time", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("event_id", Integer.class),
                row.getString("title"),
                row.get("unlocked_until", INSTANT_TIMESTAMP),
                row.get("locked_at", INSTANT_TIMESTAMP));
    }
}
