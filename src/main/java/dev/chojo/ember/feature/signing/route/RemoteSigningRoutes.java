/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityStatement;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server signing endpoints served to federation partners, through the serving functions of
 * {@code PartnerAuthorities}. Requests carry an RSA-signed envelope instead of a user session.
 *
 * <p>They belong to the event sharing surface, since documents a partner seals reach an organiser only
 * through shared events.
 */
@Singleton
public class RemoteSigningRoutes implements Routes {

    /**
     * The signing authorities of the serving station's installation, stated against the asking station's
     * challenge (query parameter {@code challenge}) and signed with the serving station's federation key.
     */
    public static final FederationEndpoint AUTHORITIES = FederationEndpoint.get(
            FederationSurface.EVENT_SHARE, "/remote/signing/authorities", SigningAuthorityStatement.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(AUTHORITIES);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteSigningRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(AUTHORITIES));
    }
}
