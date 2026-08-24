/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.service.LendingService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server lending endpoints served to federation partners. Requests carry an RSA-signed
 * envelope instead of a user session; the consumer side lives in {@link FederatedLendingRoutes}
 * and in the lending chat of {@link LendingRoutes}.
 */
@Singleton
public class RemoteLendingRoutes implements Routes {

    public static final FederationEndpoint GET_MESSAGES = FederationEndpoint.getList(
            FederationSurface.INVENTORY_LEND, "/remote/lending/messages/{requestId}", LendingMessage.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(GET_MESSAGES);

    private final LendingService service;

    @Inject
    public RemoteLendingRoutes(LendingService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(GET_MESSAGES, this::remoteGetMessages));
    }

    private void remoteGetMessages(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int requestId = pathInt(ctx, "requestId");
        var messages = service.getLocalMessages(requestId, partner.stationId(), partner.partnerStationId());
        ctx.json(messages);
    }
}
