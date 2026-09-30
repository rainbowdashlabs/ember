/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.station.service.StationDiscoveryService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * The discovery page of this instance: public listing and invitation codes for anybody, and a
 * request to federate for a station's federation manager.
 */
@Singleton
public class DiscoveryRoutes implements Routes {
    private final StationDiscoveryService discovery;

    @Inject
    public DiscoveryRoutes(StationDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/discovery", this::listDiscoverable);
        routes.post(prefix + "/public/discovery/invite", this::generateInviteForStation);
        routes.post(prefix + "/discovery/request", this::requestFederation, StationPermission.STATION_FEDERATION);
    }

    /**
     * Lists stations for the discovery page, for whoever is asking, signed in or not.
     */
    private void listDiscoverable(Context ctx) {
        UserSession session = ctx.attribute(ApiServer.ATTR_SESSION);
        ctx.json(discovery.list(session != null, session == null ? null : session.stationId()));
    }

    private void generateInviteForStation(Context ctx) {
        var body = ctx.bodyAsClass(FederationRequestBody.class);
        UserSession session = ctx.attribute(ApiServer.ATTR_SESSION);
        ctx.json(new InviteResponse(discovery.inviteCode(session != null, body.stationUid())));
    }

    private void requestFederation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        discovery.requestFederation(
                session.stationId(),
                ctx.bodyAsClass(FederationRequestBody.class).stationUid());
        ctx.json(new MessageResponse("Federation request sent"));
    }

    public record FederationRequestBody(UUID stationUid) {}

    public record InviteResponse(String inviteCode) {}
}
