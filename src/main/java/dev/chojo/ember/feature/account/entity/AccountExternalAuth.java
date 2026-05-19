/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record AccountExternalAuth(int id, int accountId, String provider, String externalId) {
    public static RowMapping<AccountExternalAuth> map() {
        return row -> new AccountExternalAuth(
                row.getInt("id"), row.getInt("account_id"), row.getString("provider"), row.getString("external_id"));
    }
}
