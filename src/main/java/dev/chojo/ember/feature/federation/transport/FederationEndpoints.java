/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every serving function, one per declared endpoint. The {@code /remote} routes answer from it and
 * the local transport calls into it, so a station on this instance and one on another are answered
 * by the same code.
 */
@Singleton
public final class FederationEndpoints {
    private final Map<FederationEndpoint, FederationHandler<?, ?>> handlers = new LinkedHashMap<>();

    /**
     * Builds the registry and lets every feature register its serving functions.
     *
     * @param servers the features that answer federation requests
     */
    @Inject
    public FederationEndpoints(Set<FederationServer> servers) {
        servers.forEach(server -> server.serveOn(this));
    }

    /**
     * Registers the serving function of one endpoint.
     *
     * @param endpoint the declared endpoint
     * @param handler  the function answering it
     * @param <B>      the request body type
     * @param <R>      the answer type
     */
    public <B, R> void serve(FederationEndpoint endpoint, FederationHandler<B, R> handler) {
        if (handlers.putIfAbsent(endpoint, handler) != null) {
            throw new IllegalStateException("Two serving functions for " + endpoint.path());
        }
    }

    /**
     * The serving function of one endpoint.
     *
     * @param endpoint the declared endpoint
     * @return its serving function
     */
    @SuppressWarnings("unchecked")
    public <B, R> FederationHandler<B, R> handlerFor(FederationEndpoint endpoint) {
        var handler = handlers.get(endpoint);
        if (handler == null) throw new IllegalStateException("No serving function for " + endpoint.path());
        return (FederationHandler<B, R>) handler;
    }

    /**
     * Whether an endpoint has a serving function.
     *
     * @param endpoint the declared endpoint
     * @return true once one is registered
     */
    public boolean serves(FederationEndpoint endpoint) {
        return handlers.containsKey(endpoint);
    }
}
