/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record AttendanceEntry(
        int id,
        int sessionId,
        int memberId,
        AttendanceStatus status,
        Instant checkIn,
        Instant checkOut,
        EntrySource source) {
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

    public Optional<Instant> checkInOpt() {
        return Optional.ofNullable(checkIn);
    }

    public Optional<Instant> checkOutOpt() {
        return Optional.ofNullable(checkOut);
    }

    public enum AttendanceStatus {
        UNCONFIRMED,
        PRESENT,
        ABSENT,
        DECLINED
    }

    public enum EntrySource {
        EXPECTED,
        EXTRA
    }
}
