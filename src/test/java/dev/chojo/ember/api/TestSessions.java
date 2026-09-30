/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Who is asking, for a request sent through {@link RouteHarness#as(UserSession)}.
 *
 * <p>Every session here belongs to account {@value #ACCOUNT_ID}. A station session carries member
 * {@value #MEMBER_ID} of that station, holding exactly the permissions named and nothing expanded
 * from them, so a test states what the route has to see and no more.
 */
public final class TestSessions {
    /** The account every session belongs to. */
    public static final int ACCOUNT_ID = 1;

    /** The member a station session acts as. */
    public static final int MEMBER_ID = 11;

    private TestSessions() {}

    /**
     * The account every session belongs to.
     *
     * @return the account
     */
    public static Account account() {
        return new Account(
                ACCOUNT_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "mara@test.com",
                null,
                "Mara",
                "Nager",
                true,
                null,
                "Mara Nager",
                null,
                null);
    }

    /**
     * An instance administrator with no station chosen.
     *
     * @return the session
     */
    public static UserSession administrator() {
        return new UserSession(
                account(), 1, null, null, null, Set.of(), Set.of(InstancePermission.ADMINISTRATOR), null);
    }

    /**
     * Somebody acting for a cluster, holding the given permissions there and no station.
     *
     * @param clusterId   the cluster
     * @param permissions what they may do for it
     * @return the session
     */
    public static UserSession clusterMember(int clusterId, ClusterPermission... permissions) {
        return new UserSession(
                account(),
                1,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                null,
                null,
                null,
                false,
                clusterId,
                UUID.fromString("00000000-0000-0000-0001-%012d".formatted(clusterId)),
                null,
                Set.of(permissions));
    }

    /**
     * A member of a station holding the given permissions there.
     *
     * @param stationId   the station
     * @param permissions what the member may do there
     * @return the session
     */
    public static UserSession member(int stationId, StationPermission... permissions) {
        var member = new StationMember(
                MEMBER_ID,
                stationId,
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                ACCOUNT_ID,
                false,
                null,
                "Mara Nager",
                StationUserType.MEMBER,
                LocalDate.of(2020, 1, 1));
        return new UserSession(
                account(),
                1,
                stationId,
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(stationId)),
                member,
                Set.of(permissions),
                Set.of(),
                null);
    }
}
