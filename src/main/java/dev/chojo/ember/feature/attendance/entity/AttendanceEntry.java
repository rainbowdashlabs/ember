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
     * When this member arrived, as a sheet shows it.
     *
     * <p>Nearly everybody who was there was there from the start, so an entry that carries no time
     * of its own stands for the session's. That reading only holds for somebody who was actually
     * there: filling the session's hours in beside a member marked absent would put them on the
     * sheet for an evening they missed.
     *
     * @param sessionStart when the session began
     * @return the member's own check-in, the session's start where they were present and wrote none
     *         down, and nothing at all otherwise
     */
    public Instant shownCheckIn(Instant sessionStart) {
        if (checkIn != null) return checkIn;
        return status == AttendanceStatus.PRESENT ? sessionStart : null;
    }

    /**
     * When this member left, read the same way as {@link #shownCheckIn(Instant)}.
     *
     * @param sessionEnd when the session ended
     * @return the member's own check-out, the session's end where they were present and wrote none
     *         down, and nothing at all otherwise
     */
    public Instant shownCheckOut(Instant sessionEnd) {
        if (checkOut != null) return checkOut;
        return status == AttendanceStatus.PRESENT ? sessionEnd : null;
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
