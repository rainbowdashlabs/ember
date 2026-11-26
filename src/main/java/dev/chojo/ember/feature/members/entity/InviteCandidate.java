/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * An account a member manager found by its name, to invite into their station. Carries nothing the
 * manager could not see already: the name, and the tie that makes it findable. Never the address.
 *
 * @param accountId    the account, for the server only
 * @param accountUid   the account as the invitation names it
 * @param firstName    its first name
 * @param lastName     its last name
 * @param because      why the manager may find it
 * @param through      the shared station or association by name, or null for an account that opted in
 * @param alreadyHere  whether it is a member of the inviting station already
 * @param alreadyAsked whether the inviting station waits for it to answer a link request
 * @param declinedLately whether it declined a link request of the inviting station in the last 30 days
 */
public record InviteCandidate(
        int accountId,
        UUID accountUid,
        String firstName,
        String lastName,
        FindableBecause because,
        @Nullable String through,
        boolean alreadyHere,
        boolean alreadyAsked,
        boolean declinedLately) {

    /** Reads a row of the candidate query. */
    public static RowMapping<InviteCandidate> map() {
        return row -> new InviteCandidate(
                row.getInt("account_id"),
                row.get("account_uid", StandardValueConverter.UUID_STRING),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getEnum("because", FindableBecause.class),
                row.getString("through"),
                row.getBoolean("already_here"),
                row.getBoolean("already_asked"),
                row.getBoolean("declined_lately"));
    }
}
