/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a user account with personal information and email verification status.
 *
 * @param id            the unique account identifier
 * @param email         the account email address
 * @param firstName     the user's first name
 * @param lastName      the user's last name
 * @param emailVerified whether the email address has been verified
 */
public record Account(int id, String email, String firstName, String lastName, boolean emailVerified) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<Account> map() {
        return row -> new Account(
                row.getInt("id"),
                row.getString("email"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getBoolean("email_verified"));
    }

    /**
     * Returns the full name by combining first and last name.
     *
     * @return the concatenated first and last name
     */
    public String fullName() {
        return firstName + " " + lastName;
    }
}
