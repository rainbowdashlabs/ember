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
 * Represents a single member's attendance record within a session.
 *
 * @param id        unique entry identifier
 * @param sessionId the attendance session this entry belongs to
 * @param memberId  the station member this entry tracks
 * @param status    current attendance status (e.g. PRESENT, ABSENT)
 * @param checkIn   optional check-in timestamp
 * @param checkOut  optional check-out timestamp
 * @param source    how this entry was created (EXPECTED from group or EXTRA manually added)
 */
public record AttendanceEntry(
        int id,
        int sessionId,
        int memberId,
        AttendanceStatus status,
        Instant checkIn,
        Instant checkOut,
        EntrySource source) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AttendanceEntry> map() {
        return row -> new AttendanceEntry(
                row.getInt("id"),
                row.getInt("session_id"),
                row.getInt("member_id"),
                row.getEnum("status", AttendanceStatus.class),
                row.get("check_in", INSTANT_TIMESTAMP),
                row.get("check_out", INSTANT_TIMESTAMP),
                row.getEnum("source", EntrySource.class));
    }

    /**
     * The attendance status of a member in a session.
     */
    public enum AttendanceStatus {
        UNCONFIRMED,
        PRESENT,
        ABSENT,
        DECLINED
    }

    /**
     * Indicates how an attendance entry was created.
     */
    public enum EntrySource {
        EXPECTED,
        EXTRA
    }
}
