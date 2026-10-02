/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * What ties an account has beyond the station that asks about it. Either one means the account is
 * not that station's alone, so its administration may not decide on the account's password.
 *
 * @param elsewhere   whether the account is or was a member of any other station
 * @param association whether the account holds a role in any association
 */
public record AccountTies(boolean elsewhere, boolean association) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountTies> map() {
        return row -> new AccountTies(row.getBoolean("elsewhere"), row.getBoolean("association"));
    }
}
