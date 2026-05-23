/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import io.javalin.http.Context;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an authenticated user's session context, including account info, station scope, and resolved roles.
 *
 * @param account    the authenticated account
 * @param stationId  the internal station ID, or {@code null} if no station is selected
 * @param stationUid the external station UUID, or {@code null} if no station is selected
 * @param member     the station member record if the user belongs to the station, or {@code null}
 * @param roles      the fully expanded set of roles for this session
 */
public record UserSession(Account account, Integer stationId, UUID stationUid, StationMember member, Set<Roles> roles) {

    /**
     * Extracts the user session from a Javalin request context.
     *
     * @param ctx the Javalin context
     * @return the user session stored as a context attribute
     */
    public static UserSession from(Context ctx) {
        return ctx.attribute(ApiServer.ATTR_SESSION);
    }

    /**
     * Returns the account ID for convenience.
     *
     * @return the account's database ID
     */
    public int accountId() {
        return account.id();
    }

    /**
     * Returns the station member as an optional, empty if the user has no membership in the current station.
     *
     * @return optional station member
     */
    public Optional<StationMember> memberOpt() {
        return Optional.ofNullable(member);
    }

    /**
     * Returns the station ID as an optional, empty if no station is selected.
     *
     * @return optional station ID
     */
    public Optional<Integer> stationIdOpt() {
        return Optional.ofNullable(stationId);
    }

    /**
     * Checks whether this session's role set contains the given role.
     *
     * @param role the role to check
     * @return {@code true} if the user has the role
     */
    public boolean hasRole(Roles role) {
        return roles.contains(role);
    }
}
