/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.service.LendingService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;

/**
 * Consumer endpoints for inventory offered by federation partners. Lending has no local
 * counterpart to this listing - it only exists as an aggregation across partners. The endpoints
 * an owning station serves to its partners live in {@link RemoteLendingRoutes}.
 */
@Singleton
public class FederatedLendingRoutes implements Routes {

    private final LendingService service;

    @Inject
    public FederatedLendingRoutes(LendingService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/federated/lending/available",
                this::listAvailable,
                StationPermission.INVENTORY_LENDING_REQUEST);
    }

    private void listAvailable(Context ctx) {
        var session = UserSession.from(ctx);
        String query = ctx.queryParam("q");
        String fromParam = ctx.queryParam("from");
        String toParam = ctx.queryParam("to");
        LocalDate dateFrom = fromParam != null && !fromParam.isBlank() ? LocalDate.parse(fromParam) : null;
        LocalDate dateTo = toParam != null && !toParam.isBlank() ? LocalDate.parse(toParam) : dateFrom;
        ctx.json(service.findAvailableInventory(session.stationId(), query, dateFrom, dateTo));
    }
}
