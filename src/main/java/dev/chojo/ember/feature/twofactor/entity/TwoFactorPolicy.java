/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One row of {@code two_factor_policy} — a per-(station, user-type) toggle that mandates 2FA
 * enrolment for anyone the row matches.
 *
 * @param id        row id
 * @param scope     {@link Scope#INSTANCE} or {@link Scope#STATION}
 * @param stationId null for instance-wide rows, set for station-scoped rows
 * @param userType  the user type the row applies to, or {@code null} to mean "every user type"
 * @param required  whether the matched users must enrol
 * @param graceDays grace window in days before enforcement bites (max 7, enforced in the config)
 * @param createdBy the station-member row that wrote the policy, if known
 * @param createdAt when the policy was created
 */
public record TwoFactorPolicy(
        int id,
        Scope scope,
        Integer stationId,
        StationUserType userType,
        boolean required,
        short graceDays,
        Integer createdBy,
        Instant createdAt) {

    public static RowMapping<TwoFactorPolicy> map() {
        return row -> {
            String userTypeName = row.getString("user_type");
            StationUserType userType =
                    userTypeName == null || userTypeName.isBlank() ? null : StationUserType.valueOf(userTypeName);
            Integer stationId = row.getObject("station_id", Integer.class);
            Integer createdBy = row.getObject("created_by", Integer.class);
            return new TwoFactorPolicy(
                    row.getInt("id"),
                    row.getEnum("scope", Scope.class),
                    stationId,
                    userType,
                    row.getBoolean("required"),
                    row.getShort("grace_days"),
                    createdBy,
                    row.get("created_at", INSTANT_TIMESTAMP));
        };
    }

    public enum Scope {
        INSTANCE,
        STATION
    }
}
