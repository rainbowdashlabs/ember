/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a GDPR consent record capturing proof that a user accepted specific versions of legal documents.
 *
 * @param id             the unique consent record identifier
 * @param accountId      the account that gave consent
 * @param consentVersion the version hash of the consent text accepted
 * @param privacyVersion the version hash of the privacy policy accepted
 * @param tosVersion     the version hash of the terms of service accepted
 * @param ipAddress      the IP address of the user at the time of consent
 * @param country        the country derived from Cloudflare headers (may be null)
 * @param userAgent      the user agent string of the browser used
 * @param consentedAt    the timestamp when consent was given
 */
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

    /** Creates a row mapping for database result set conversion. */
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
