/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.StationMember;
import io.javalin.http.Context;

import java.util.UUID;

/**
 * A signed-in caller acting at a station they are a member of. Where a {@link UserSession} may name no
 * station or no membership, this one always holds both, so a route at a station reads them without
 * checking.
 *
 * @param user       the session it was taken from, for everything that is not about the station
 * @param stationId  the station the request named
 * @param stationUid that station's stable identity
 * @param member     the caller's membership there
 */
public record StationSession(UserSession user, int stationId, UUID stationUid, StationMember member) {

    /**
     * The session of a request at a station.
     *
     * @param ctx the request
     * @return the session, with its station and membership
     * @throws RefusalResponse {@link Refusal#NO_STATION_CHOSEN} when the request names no station,
     *                         {@link Refusal#NOT_A_MEMBER_OF_THIS_STATION} when the caller is no member of it
     */
    public static StationSession from(Context ctx) {
        return of(UserSession.from(ctx));
    }

    /**
     * The station side of a session, refused the same way as {@link #from(Context)}.
     *
     * @param user the signed-in caller
     * @return the session, with its station and membership
     */
    public static StationSession of(UserSession user) {
        Integer stationId = user.stationId();
        UUID stationUid = user.stationUid();
        if (stationId == null || stationUid == null) throw Refusal.NO_STATION_CHOSEN.raise();
        StationMember member = user.member();
        if (member == null) throw Refusal.NOT_A_MEMBER_OF_THIS_STATION.raise();
        return new StationSession(user, stationId, stationUid, member);
    }

    public int accountId() {
        return user.accountId();
    }

    public boolean hasPermission(StationPermission permission) {
        return user.hasPermission(permission);
    }

    public StationUserType userType() {
        return member.userType();
    }
}
