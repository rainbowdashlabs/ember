/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstanceUserType;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * One account as the instance's account list shows it: who it is, where it belongs, and how it
 * signs in.
 *
 * @param name                     the register name
 * @param email                    the address mail can reach, or {@code null} for an account that has none
 * @param loginName                what the account types as its name at the login screen
 * @param instanceUserType         its role on the instance
 * @param lastSignInAt             its last sign-in, or {@code null} for one that never signed in
 * @param stations                 the stations it is a member of now, by name
 * @param associations             the associations it holds a role in
 * @param hasPassword              whether it holds a password at all
 * @param passwordSignIn           whether that password works on the login screen
 * @param oneTimePasswordExpiresAt when the one-time password it holds stops working, or {@code null}
 *                                 where it holds none
 * @param passkeys                 how many passkeys it may sign in with
 * @param twoFactor                whether a second factor is asked after its password
 */
public record AccountOverview(
        int id,
        UUID uid,
        String name,
        @Nullable String email,
        String loginName,
        InstanceUserType instanceUserType,
        @Nullable Instant lastSignInAt,
        List<String> stations,
        List<AssociationRole> associations,
        boolean hasPassword,
        boolean passwordSignIn,
        @Nullable Instant oneTimePasswordExpiresAt,
        int passkeys,
        boolean twoFactor) {

    /**
     * A role an account holds in an association.
     *
     * @param association the association's name
     * @param role        what the account is there
     */
    public record AssociationRole(String association, ClusterUserType role) {}

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountOverview> map() {
        return row -> {
            String email = row.getString("email");
            String username = row.getString("username");
            return new AccountOverview(
                    row.getInt("id"),
                    row.get("uid", UUID_STRING),
                    row.getString("name"),
                    Account.isRealEmail(email) ? email : null,
                    username != null && !username.isBlank() ? username : email,
                    row.getEnum("instance_user_type", InstanceUserType.class),
                    row.get("last_sign_in_at", INSTANT_TIMESTAMP),
                    texts(row.getArray("stations")),
                    roles(row.getArray("association_names"), row.getArray("association_roles")),
                    row.getBoolean("has_password"),
                    row.getBoolean("password_sign_in"),
                    row.get("one_time_password_expires_at", INSTANT_TIMESTAMP),
                    row.getInt("passkeys"),
                    row.getBoolean("two_factor"));
        };
    }

    private static List<String> texts(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }

    private static List<AssociationRole> roles(Array names, Array roles) throws SQLException {
        var associations = texts(names);
        var types = texts(roles);
        var result = new ArrayList<AssociationRole>(associations.size());
        for (int i = 0; i < associations.size(); i++) {
            result.add(new AssociationRole(associations.get(i), ClusterUserType.valueOf(types.get(i))));
        }
        return result;
    }
}
