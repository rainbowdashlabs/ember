/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a member's absence period with a reason.
 *
 * @param id          the absence record identifier
 * @param memberId    the member who is absent
 * @param absentFrom  the start date of the absence (inclusive)
 * @param absentUntil the end date of the absence (inclusive)
 * @param reason      the reason for the absence
 * @param createdAt   when the absence was recorded
 * @param createdBy   the member who created this absence record, or null
 */
public record MemberAbsence(
        int id,
        int memberId,
        LocalDate absentFrom,
        LocalDate absentUntil,
        String reason,
        Instant createdAt,
        Integer createdBy) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<MemberAbsence> map() {
        return row -> new MemberAbsence(
                row.getInt("id"),
                row.getInt("member_id"),
                row.getObject("absent_from", LocalDate.class),
                row.getObject("absent_until", LocalDate.class),
                row.getString("reason"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class));
    }

    /**
     * Checks whether this absence is currently active based on today's date.
     *
     * @return true if today falls within the absence period (inclusive)
     */
    public boolean isActive() {
        var today = LocalDate.now();
        return !today.isBefore(absentFrom) && !today.isAfter(absentUntil);
    }
}
