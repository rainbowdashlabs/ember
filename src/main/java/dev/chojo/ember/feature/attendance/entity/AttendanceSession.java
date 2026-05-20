/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

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
 * @param eventId    optional linked event identifier
 * @param title      display title of the session
 */
public record AttendanceSession(
        int id, int templateId, Instant startTime, Instant endTime, Instant createdAt, Integer eventId, String title) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<AttendanceSession> map() {
        return row -> new AttendanceSession(
                row.getInt("id"),
                row.getInt("template_id"),
                row.get("start_time", INSTANT_TIMESTAMP),
                row.get("end_time", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("event_id", Integer.class),
                row.getString("title"));
    }
}
