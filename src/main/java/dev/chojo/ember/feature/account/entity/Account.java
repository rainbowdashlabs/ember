/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.InstanceUserType;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Represents a user account with personal information and email verification status.
 *
 * @param id                  the unique account identifier
 * @param uid                 the stable per-account UUID exposed publicly to key account-scoped resources
 * @param email               the account email address
 * @param username            the name this account signs in with beside its address, or {@code null}
 *                            when the address is the only way in
 * @param firstName           the user's first name
 * @param lastName            the user's last name
 * @param emailVerified       whether the email address has been verified
 * @param instanceUserType    the instance-level user type (USER or ADMINISTRATOR)
 * @param creatingStationId   station that initiated the account creation, or {@code null} for self-signup
 *                            and admin bootstrap. Used to pick the language for system mails.
 * @param setupCompletedAt    timestamp of the first successful login, or {@code null} while the account
 *                            is pending initial setup.
 */
public record Account(
        int id,
        UUID uid,
        String email,
        String username,
        String firstName,
        String lastName,
        boolean emailVerified,
        InstanceUserType instanceUserType,
        String fullName,
        Integer creatingStationId,
        Instant setupCompletedAt) {

    /**
     * What an address ends in when it was made up for somebody who is not meant to sign in. Nothing
     * can be delivered to it, so an account carrying one counts as having no address at all.
     */
    public static final String SYNTHETIC_EMAIL_SUFFIX = ".local";

    /**
     * Whether mail can actually reach this address, rather than it standing in for one.
     */
    public static boolean isRealEmail(String email) {
        return email != null
                && !email.isBlank()
                && !email.toLowerCase(Locale.ROOT).endsWith(SYNTHETIC_EMAIL_SUFFIX);
    }

    /**
     * Whether mail can actually reach this account.
     */
    public boolean hasRealEmail() {
        return isRealEmail(email);
    }

    /**
     * What somebody types to sign in as this account: the name it was given, or its address where it
     * was given none. Null for an account nobody can sign in as at all.
     */
    public String loginName() {
        return username != null && !username.isBlank() ? username : email;
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Account> map() {
        return row -> new Account(
                row.getInt("id"),
                row.get("uid", UUID_STRING),
                row.getString("email"),
                row.getString("username"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getBoolean("email_verified"),
                row.getEnum("instance_user_type", InstanceUserType.class),
                row.getString("full_name"),
                row.getObject("creating_station_id") != null ? row.getInt("creating_station_id") : null,
                row.get("setup_completed_at", INSTANT_TIMESTAMP));
    }
}
