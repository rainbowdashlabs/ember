/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.InstanceUserType;

import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Represents a user account with personal information and email verification status.
 *
 * @param id               the unique account identifier
 * @param uid              the stable per-account UUID exposed publicly to key account-scoped resources
 * @param email            the account email address
 * @param firstName        the user's first name
 * @param lastName         the user's last name
 * @param emailVerified    whether the email address has been verified
 * @param instanceUserType the instance-level user type (USER or ADMINISTRATOR)
 */
public record Account(
        int id,
        UUID uid,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified,
        InstanceUserType instanceUserType,
        String fullName) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Account> map() {
        return row -> new Account(
                row.getInt("id"),
                row.get("uid", UUID_STRING),
                row.getString("email"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getBoolean("email_verified"),
                row.getEnum("instance_user_type", InstanceUserType.class),
                row.getString("full_name"));
    }
}
