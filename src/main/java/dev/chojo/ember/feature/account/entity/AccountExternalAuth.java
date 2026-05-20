/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents an external authentication link (e.g. OAuth provider) associated with an account.
 *
 * @param id         the unique identifier of this external auth record
 * @param accountId  the associated account identifier
 * @param provider   the external authentication provider name (e.g. "google", "github")
 * @param externalId the user's identifier at the external provider
 */
public record AccountExternalAuth(int id, int accountId, String provider, String externalId) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<AccountExternalAuth> map() {
        return row -> new AccountExternalAuth(
                row.getInt("id"), row.getInt("account_id"), row.getString("provider"), row.getString("external_id"));
    }
}
