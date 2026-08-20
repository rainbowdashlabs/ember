/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
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
 * @param clusterId           the cluster the request is acting for, or {@code null} if none is selected
 * @param clusterUid          that cluster's stable identity, or {@code null}
 * @param clusterMember       the caller's membership of it, or {@code null} if they have none
 * @param clusterPermissions  the fully expanded set of cluster permissions for this session
 */
public record UserSession(
        Account account,
        int sessionId,
        Integer stationId,
        UUID stationUid,
        StationMember member,
        Set<StationPermission> permissions,
        Set<InstancePermission> instancePermissions,
        Instant twoFactorVerifiedAt,
        Integer clusterId,
        UUID clusterUid,
        ClusterMember clusterMember,
        Set<ClusterPermission> clusterPermissions) {

    /**
     * A session carrying no cluster context, which is every request that did not name one.
     */
    public UserSession(
            Account account,
            int sessionId,
            Integer stationId,
            UUID stationUid,
            StationMember member,
            Set<StationPermission> permissions,
            Set<InstancePermission> instancePermissions,
            Instant twoFactorVerifiedAt) {
        this(
                account,
                sessionId,
                stationId,
                stationUid,
                member,
                permissions,
                instancePermissions,
                twoFactorVerifiedAt,
                null,
                null,
                null,
                Set.of());
    }

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

    /**
     * Whether the caller may do this on the cluster they are acting for.
     *
     * <p>A request can legitimately carry both contexts: a cluster manager who is also a member of one of its
     * stations is one person with two hats, which is why this sits on the same record rather than in a second
     * session type.
     *
     * @param p the permission to check
     * @return {@code true} when the caller holds it at the cluster named by this request
     */
    public boolean hasClusterPermission(ClusterPermission p) {
        return clusterPermissions.contains(p);
    }

    public Optional<Integer> clusterIdOpt() {
        return Optional.ofNullable(clusterId);
    }

    public ClusterUserType clusterUserType() {
        return clusterMember != null ? clusterMember.userType() : null;
    }

    public StationUserType userType() {
        return member != null ? member.userType() : null;
    }

    public InstanceUserType instanceUserType() {
        return account.instanceUserType();
    }
}
