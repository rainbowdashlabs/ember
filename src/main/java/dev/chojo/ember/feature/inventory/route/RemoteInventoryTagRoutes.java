/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.feature.inventory.service.FederatedItemTagService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The things this station offers a partner for a word, served to the partner's instance.
 *
 * <p>Nothing is offered by default. The station has to have said that an inventory or a piece is
 * shared, and with whom, before a word finds it here, so a search by word cannot reach further than
 * the lending screen already lets a partner reach.
 */
@Singleton
public class RemoteInventoryTagRoutes implements Routes {

    public static final FederationEndpoint GET_TAGGED_ITEMS = FederationEndpoint.getList(
            FederationSurface.INVENTORY_LEND, "/remote/inventory/tagged/{tag}", TaggedItemSummary.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(GET_TAGGED_ITEMS);

    private final FederatedItemTagService service;

    @Inject
    public RemoteInventoryTagRoutes(FederatedItemTagService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(GET_TAGGED_ITEMS, this::remoteTaggedItems));
    }

    private void remoteTaggedItems(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        String tag = URLDecoder.decode(ctx.pathParam("tag"), StandardCharsets.UTF_8);
        ctx.json(service.serveToPartner(partner.stationId(), partner.id(), tag));
    }
}
