/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server test protocol endpoints served to federation partners, through the serving
 * functions of {@code TestProtocolService}. Requests carry an RSA-signed envelope instead of a user
 * session; the consumer side that calls these endpoints lives in {@link FederatedTestProtocolRoutes}.
 */
@Singleton
public class RemoteTestProtocolRoutes implements Routes {

    public static final FederationEndpoint BROWSE_PROTOCOLS = FederationEndpoint.getList(
            FederationSurface.PROTOCOL_SHARE, "/remote/protocols", RemoteProtocolSummary.class);
    public static final FederationEndpoint GET_PROTOCOL = FederationEndpoint.get(
            FederationSurface.PROTOCOL_SHARE, "/remote/protocols/{id}", RemoteProtocolDetail.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(BROWSE_PROTOCOLS, GET_PROTOCOL);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteTestProtocolRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(BROWSE_PROTOCOLS)
                .serve(GET_PROTOCOL));
    }

    public record RemoteProtocolSummary(int id, String name, String description, String updatedAt) {}

    public record RemoteProtocolDetail(
            TestProtocol protocol, List<TestProtocolSection> sections, List<TestProtocolItem> items) {}
}
