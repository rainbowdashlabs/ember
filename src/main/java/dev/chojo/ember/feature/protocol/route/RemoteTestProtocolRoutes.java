/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server test protocol endpoints served to federation partners. Requests carry an
 * RSA-signed envelope instead of a user session; the consumer side that calls these endpoints
 * lives in {@link FederatedTestProtocolRoutes}.
 */
@Singleton
public class RemoteTestProtocolRoutes implements Routes {

    private final TestProtocolService service;
    private final FederationRepository federationRepository;

    @Inject
    public RemoteTestProtocolRoutes(TestProtocolService service, FederationRepository federationRepository) {
        this.service = service;
        this.federationRepository = federationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/remote/protocols", this::remoteBrowseProtocols);
        routes.get(prefix + "/remote/protocols/{id}", this::remoteGetProtocol);
    }

    private void remoteBrowseProtocols(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var shares = federationRepository.findProtocolShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.protocolId() != null)
                .flatMap(s -> service.findProtocol(s.protocolId()).stream())
                .filter(proto -> proto.stationId() == partner.stationId())
                .map(proto -> new RemoteProtocolSummary(
                        proto.id(),
                        proto.name(),
                        proto.description(),
                        proto.updatedAt().toString()))
                .toList();
        ctx.json(result);
    }

    private void remoteGetProtocol(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();
        var protocol = service.findProtocol(protocolId).orElseThrow(NotFoundResponse::new);
        if (protocol.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("Protocol not shared with this partner");
        }
        var sections = service.findSections(protocolId);
        var items = service.findAllItemsByProtocol(protocolId);
        ctx.json(new RemoteProtocolDetail(protocol, sections, items));
    }

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    private record RemoteProtocolSummary(int id, String name, String description, String updatedAt) {}

    private record RemoteProtocolDetail(
            TestProtocol protocol, List<TestProtocolSection> sections, List<TestProtocolItem> items) {}
}
