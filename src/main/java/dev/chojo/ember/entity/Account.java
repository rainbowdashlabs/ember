/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record Account(int id, String email, String firstName, String lastName, boolean emailVerified) {
    public String fullName() {
        return firstName + " " + lastName;
    }

    public static RowMapping<Account> map() {
        return row -> new Account(
                row.getInt("id"),
                row.getString("email"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getBoolean("email_verified"));
    }
}
