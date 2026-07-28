/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.LendingService;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server lending endpoints served to federation partners. Requests carry an RSA-signed
 * envelope instead of a user session; the consumer side lives in {@link FederatedLendingRoutes}
 * and in the lending chat of {@link LendingRoutes}.
 */
@Singleton
public class RemoteLendingRoutes implements Routes {

    private final LendingService service;

    @Inject
    public RemoteLendingRoutes(LendingService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/remote/lending/messages/{requestId}", this::remoteGetMessages);
    }

    private void remoteGetMessages(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int requestId = pathInt(ctx, "requestId");
        var messages = service.getLocalMessages(requestId, partner.stationId());
        ctx.json(messages);
    }

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }
}
