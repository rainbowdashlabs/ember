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
 * @param passwordLoginDisabledAt when the account switched its password sign-in off, or
 *                            {@code null} while the password works on the login screen. A
 *                            switch, not a deletion: the hash stays, and setting a new password
 *                            through the forgotten-password flow switches it back on
 */
public record AccountCredential(
        int accountId,
        String passwordHash,
        boolean forcePasswordChange,
        Instant lastBreachCheckAt,
        Instant passwordLoginDisabledAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountCredential> map() {
        return row -> new AccountCredential(
                row.getInt("account_id"),
                row.getString("password_hash"),
                row.getBoolean("force_password_change"),
                row.get("last_breach_check_at", INSTANT_TIMESTAMP),
                row.get("password_login_disabled_at", INSTANT_TIMESTAMP));
    }

    /**
     * Both halves of "password sign-in is on" for this row; the caller still has to treat a
     * missing row as off.
     */
    public boolean passwordLoginEnabled() {
        return passwordLoginDisabledAt == null;
    }
}
