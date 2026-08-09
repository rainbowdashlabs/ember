/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server test protocol endpoints served to federation partners. Requests carry an
 * RSA-signed envelope instead of a user session; the consumer side that calls these endpoints
 * lives in {@link FederatedTestProtocolRoutes}.
 */
@Singleton
public class RemoteTestProtocolRoutes implements Routes {

    public static final FederationEndpoint BROWSE_PROTOCOLS = FederationEndpoint.getList(
            FederationSurface.PROTOCOL_SHARE, "/remote/protocols", RemoteProtocolSummary.class);
    public static final FederationEndpoint GET_PROTOCOL = FederationEndpoint.get(
            FederationSurface.PROTOCOL_SHARE, "/remote/protocols/{id}", RemoteProtocolDetail.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(BROWSE_PROTOCOLS, GET_PROTOCOL);

    private final TestProtocolService service;
    private final FederationRepository federationRepository;

    @Inject
    public RemoteTestProtocolRoutes(TestProtocolService service, FederationRepository federationRepository) {
        this.service = service;
        this.federationRepository = federationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(BROWSE_PROTOCOLS, this::remoteBrowseProtocols)
                        .handle(GET_PROTOCOL, this::remoteGetProtocol));
    }

    private void remoteBrowseProtocols(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
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
        var partner = FederationSession.requirePartner(ctx);
        int protocolId = pathInt(ctx, "id");
        var protocol = service.findProtocol(protocolId).orElseThrow(NotFoundResponse::new);
        if (protocol.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("Protocol not shared with this partner");
        }
        var sections = service.findSections(protocolId);
        var items = service.findAllItemsByProtocol(protocolId);
        ctx.json(new RemoteProtocolDetail(protocol, sections, items));
    }

    public record RemoteProtocolSummary(int id, String name, String description, String updatedAt) {}

    public record RemoteProtocolDetail(
            TestProtocol protocol, List<TestProtocolSection> sections, List<TestProtocolItem> items) {}
}
