/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A pending WebAuthn challenge, parked between the start and the finish of a ceremony so the
 * verifier is stateless across the round trip to the browser. Single use and short lived; the
 * finish consumes the row whatever the outcome.
 *
 * @param accountId the account the ceremony belongs to, or {@code null} for a passwordless
 *         sign-in, which does not know the account until the assertion comes back
 */
public record WebAuthnChallenge(
        int id, ChallengePurpose purpose, Integer accountId, String optionsJson, Instant createdAt, Instant expiresAt) {

    public static RowMapping<WebAuthnChallenge> map() {
        return row -> new WebAuthnChallenge(
                row.getInt("id"),
                row.getEnum("purpose", ChallengePurpose.class),
                row.getObject("account_id", Integer.class),
                row.getString("options_json"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP));
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
