/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents stored password credentials for an account.
 *
 * @param accountId           the associated account identifier
 * @param passwordHash        the hashed password
 * @param forcePasswordChange whether the user must change their password on next login
 */
public record AccountCredential(int accountId, String passwordHash, boolean forcePasswordChange) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountCredential> map() {
        return row -> new AccountCredential(
                row.getInt("account_id"), row.getString("password_hash"), row.getBoolean("force_password_change"));
    }
}
