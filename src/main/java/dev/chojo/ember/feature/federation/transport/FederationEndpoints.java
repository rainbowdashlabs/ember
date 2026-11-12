/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
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
    private final ModuleAvailability modules;

    /**
     * Builds the registry and lets every feature register its serving functions.
     *
     * @param servers the features that answer federation requests
     * @param modules which modules the serving stations have
     */
    @Inject
    public FederationEndpoints(Set<FederationServer> servers, ModuleAvailability modules) {
        this.modules = modules;
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
     * <p>An endpoint of a module's surface answers only while the serving station has that module: what
     * a station switched off, or its cluster denies it, it shares with no partner, whatever it shared
     * before. A partner on this instance and one on another are refused alike. A push that only keeps
     * the station's own copy in step is still taken in, so nothing is out of date once the module is
     * back.
     *
     * @param endpoint the declared endpoint
     * @return its serving function
     */
    @SuppressWarnings("unchecked")
    public <B, R> FederationHandler<B, R> handlerFor(FederationEndpoint endpoint) {
        var handler = (FederationHandler<B, R>) handlers.get(endpoint);
        if (handler == null) throw new IllegalStateException("No serving function for " + endpoint.path());
        var module = endpoint.surface().module();
        if (module.isEmpty() || endpoint.keepsInStep()) return handler;
        return (partner, params, body) -> {
            if (module.get().isOffIn(modules.disabledAt(partner.row().stationId()))) {
                throw GeneralRefusal.MODULE_SWITCHED_OFF.raise();
            }
            return handler.serve(partner, params, body);
        };
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
