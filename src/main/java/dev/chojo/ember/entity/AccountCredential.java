/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record AccountCredential(int accountId, String passwordHash, boolean forcePasswordChange) {
    public static RowMapping<AccountCredential> map() {
        return row -> new AccountCredential(
                row.getInt("account_id"), row.getString("password_hash"), row.getBoolean("force_password_change"));
    }
}
