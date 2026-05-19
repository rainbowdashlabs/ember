/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record GdprConsent(
        int id,
        int accountId,
        String consentVersion,
        String privacyVersion,
        String tosVersion,
        String ipAddress,
        String country,
        String userAgent,
        Instant consentedAt) {

    public static RowMapping<GdprConsent> map() {
        return row -> new GdprConsent(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("consent_version"),
                row.getString("privacy_version"),
                row.getString("tos_version"),
                row.getString("ip_address"),
                row.getString("country"),
                row.getString("user_agent"),
                row.get("consented_at", INSTANT_TIMESTAMP));
    }
}
