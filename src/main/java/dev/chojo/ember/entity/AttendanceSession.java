/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record AttendanceSession(
        int id, int templateId, Instant startTime, Instant endTime, Instant createdAt, Integer eventId, String title) {
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
