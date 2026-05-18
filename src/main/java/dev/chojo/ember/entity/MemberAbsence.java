/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record MemberAbsence(
        int id,
        int memberId,
        LocalDate absentFrom,
        LocalDate absentUntil,
        String reason,
        Instant createdAt,
        Integer createdBy) {
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

    public boolean isActive() {
        var today = LocalDate.now();
        return !today.isBefore(absentFrom) && !today.isAfter(absentUntil);
    }
}
