/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.feature.inventory.service.FederatedItemTagService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Searching for things by a word past the station's own shelves.
 *
 * <p>Whoever may ask a partner for gear may run this search, because it answers the same question a
 * step earlier: not "what does that station have" but "who has one of these at all".
 */
@Singleton
public class FederatedInventoryTagRoutes implements Routes {
    private final FederatedItemTagService service;

    @Inject
    public FederatedInventoryTagRoutes(FederatedItemTagService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/federated/inventory-tags/items",
                this::itemsByTag,
                StationPermission.INVENTORY_LENDING_REQUEST);
    }

    @OpenApi(
            path = "/api/v1/federated/inventory-tags/items",
            methods = HttpMethod.GET,
            summary = "The things carrying a word, here and at every partner that lends to this station",
            tags = {"Inventory"},
            queryParams = @OpenApiParam(name = "tag", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TaggedItemSummary[].class)))
    private void itemsByTag(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(service.findAcrossPartners(session.stationId(), ctx.queryParam("tag")));
    }
}
