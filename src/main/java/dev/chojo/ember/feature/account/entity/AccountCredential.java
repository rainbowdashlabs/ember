/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents stored password credentials for an account.
 *
 * @param accountId           the associated account identifier
 * @param passwordHash        the hashed password
 * @param forcePasswordChange whether the user must change their password on next login
 * @param lastBreachCheckAt   timestamp of the most recent HIBP breach check, or {@code null}
 *                            when the credential has never been checked or was rotated since
 *                            the last check
 */
public record AccountCredential(
        int accountId, String passwordHash, boolean forcePasswordChange, Instant lastBreachCheckAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountCredential> map() {
        return row -> new AccountCredential(
                row.getInt("account_id"),
                row.getString("password_hash"),
                row.getBoolean("force_password_change"),
                row.get("last_breach_check_at", INSTANT_TIMESTAMP));
    }
}
