/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.StationMember;
import io.javalin.http.Context;

import java.util.Optional;
import java.util.Set;

public record UserSession(Account account, Integer stationId, StationMember member, Set<Roles> roles) {

    public int accountId() {
        return account.id();
    }

    public Optional<StationMember> memberOpt() {
        return Optional.ofNullable(member);
    }

    public Optional<Integer> stationIdOpt() {
        return Optional.ofNullable(stationId);
    }

    public boolean hasRole(Roles role) {
        return roles.contains(role);
    }

    public static UserSession from(Context ctx) {
        return ctx.attribute(ApiServer.ATTR_SESSION);
    }
}
