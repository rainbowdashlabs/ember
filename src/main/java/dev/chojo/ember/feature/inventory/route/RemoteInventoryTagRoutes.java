/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteInventoryTagRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, endpoints, binder -> binder.serve(GET_TAGGED_ITEMS));
    }
}
