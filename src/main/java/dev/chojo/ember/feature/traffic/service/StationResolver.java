/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.service;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.UserSession;
import io.javalin.http.Context;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Resolves the owning station for a request so the after-handler can charge the right
 * bucket. Resolution order, from cheapest to most specific:
 *
 * <ol>
 *   <li>An explicit {@link #ATTR_TRAFFIC_STATION_ID} context attribute set upstream (e.g.
 *       by the feed-token handler that already resolves the token's owning station).</li>
 *   <li>An authenticated {@link UserSession} carrying a station scope.</li>
 *   <li>A {@link FederationSession} — charge the serving station.</li>
 * </ol>
 *
 * <p>If none of the above apply, the request is recorded against the instance-global
 * bucket ({@code station_id = NULL}) so totals still add up at the instance level.
 */
@Singleton
public class StationResolver {

    /**
     * Context attribute key for handlers that already know the station for a public route
     * (e.g. feed-token resolvers, public-page lookups). Setting this short-circuits the
     * session checks in {@link #resolve(Context)}.
     */
    public static final String ATTR_TRAFFIC_STATION_ID = "trafficStationId";

    public Optional<Integer> resolve(Context ctx) {
        Object explicit = ctx.attribute(ATTR_TRAFFIC_STATION_ID);
        if (explicit instanceof Integer id) {
            return Optional.of(id);
        }

        UserSession user = ctx.attribute(ApiServer.ATTR_SESSION);
        if (user != null && user.stationId() != null) {
            return Optional.of(user.stationId());
        }

        FederationSession fed = ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION);
        if (fed != null) {
            return Optional.of(fed.stationId());
        }

        return Optional.empty();
    }
}
