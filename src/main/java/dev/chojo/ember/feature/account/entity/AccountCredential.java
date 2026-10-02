/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

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
 * @param oneTimePasswordExpiresAt when the one-time password an administrator issued stops
 *                            working, or {@code null} where the password on file is not one
 */
public record AccountCredential(
        int accountId,
        String passwordHash,
        boolean forcePasswordChange,
        Instant lastBreachCheckAt,
        Instant passwordLoginDisabledAt,
        @Nullable Instant oneTimePasswordExpiresAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountCredential> map() {
        return row -> new AccountCredential(
                row.getInt("account_id"),
                row.getString("password_hash"),
                row.getBoolean("force_password_change"),
                row.get("last_breach_check_at", INSTANT_TIMESTAMP),
                row.get("password_login_disabled_at", INSTANT_TIMESTAMP),
                row.get("one_time_password_expires_at", INSTANT_TIMESTAMP));
    }

    /**
     * Both halves of "password sign-in is on" for this row; the caller still has to treat a
     * missing row as off.
     */
    public boolean passwordLoginEnabled() {
        return passwordLoginDisabledAt == null;
    }

    /**
     * Whether the password on file is a one-time password whose time is up.
     *
     * @param now the moment to judge by
     * @return {@code true} when it was issued as a one-time password and has expired
     */
    public boolean oneTimePasswordExpired(Instant now) {
        Instant expiresAt = oneTimePasswordExpiresAt;
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
