/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

public record MemberRegistrationStats(
        int memberId, int registered, int accepted, int denied, int declined, LocalDate lastDenied) {

    public static RowMapping<MemberRegistrationStats> map() {
        return row -> new MemberRegistrationStats(
                row.getInt("member_id"),
                row.getInt("registered"),
                row.getInt("accepted"),
                row.getInt("denied"),
                row.getInt("declined"),
                row.getObject("last_denied", LocalDate.class));
    }
}
