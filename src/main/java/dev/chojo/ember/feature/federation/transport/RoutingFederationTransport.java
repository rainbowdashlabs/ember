/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The one binding of {@link FederationTransport}, and the one place that asks where a partner
 * lives: a partner on another instance goes to {@link HttpFederationTransport}, one on this
 * instance to {@link LocalFederationTransport}.
 *
 * <p>Every answer type is checked against the endpoint's declaration first, on both paths, so a
 * caller that reads more than the contract covers fails the same way wherever its partner is.
 */
@Singleton
public class RoutingFederationTransport implements FederationTransport {
    private final HttpFederationTransport http;
    private final LocalFederationTransport local;

    @Inject
    public RoutingFederationTransport(HttpFederationTransport http, LocalFederationTransport local) {
        this.http = http;
        this.local = local;
    }

    @Override
    public <T> T get(FederationPartner partner, FederationRequest request, Class<T> type) {
        request.requireResponseType(type);
        return to(partner).get(partner, request, type);
    }

    @Override
    public <T> List<T> getList(FederationPartner partner, FederationRequest request, Class<T> elementType) {
        request.requireResponseType(elementType);
        return to(partner).getList(partner, request, elementType);
    }

    @Override
    public <T> T send(FederationPartner partner, FederationRequest request, @Nullable Object body, Class<T> type) {
        request.requireResponseType(type);
        return to(partner).send(partner, request, body, type);
    }

    @Override
    public <T> List<T> sendList(
            FederationPartner partner, FederationRequest request, Object body, Class<T> elementType) {
        request.requireResponseType(elementType);
        return to(partner).sendList(partner, request, body, elementType);
    }

    @Override
    public void deliver(FederationPartner partner, FederationRequest request, @Nullable Object body) {
        to(partner).deliver(partner, request, body);
    }

    @Override
    public void notify(FederationPartner partner, FederationRequest webhook, Object body) {
        to(partner).notify(partner, webhook, body);
    }

    private FederationTransport to(FederationPartner partner) {
        return partner.isRemote() ? http : local;
    }
}
