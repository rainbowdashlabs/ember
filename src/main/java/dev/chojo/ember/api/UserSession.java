/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an authenticated user's session context, including account info, station scope, and resolved permissions.
 *
 * @param account             the authenticated account
 * @param sessionId           the underlying account_session row id
 * @param stationId           the internal station ID, or {@code null} if no station is selected
 * @param stationUid          the external station UUID, or {@code null} if no station is selected
 * @param member              the station member record if the user belongs to the station, or {@code null}
 * @param permissions         the fully expanded set of station permissions for this session
 * @param instancePermissions the instance-level permissions
 * @param twoFactorVerifiedAt when the session last completed a 2FA challenge (login or step-up); {@code null} if never
 */
public record UserSession(
        Account account,
        int sessionId,
        Integer stationId,
        UUID stationUid,
        StationMember member,
        Set<StationPermission> permissions,
        Set<InstancePermission> instancePermissions,
        Instant twoFactorVerifiedAt) {

    public static UserSession from(Context ctx) {
        return ctx.attribute(ApiServer.ATTR_SESSION);
    }

    public int accountId() {
        return account.id();
    }

    public Optional<StationMember> memberOpt() {
        return Optional.ofNullable(member);
    }

    public Optional<Integer> stationIdOpt() {
        return Optional.ofNullable(stationId);
    }

    public boolean hasPermission(StationPermission p) {
        return permissions.contains(p);
    }

    public boolean hasInstancePermission(InstancePermission p) {
        return instancePermissions.contains(p);
    }

    public StationUserType userType() {
        return member != null ? member.userType() : null;
    }

    public InstanceUserType instanceUserType() {
        return account.instanceUserType();
    }
}
