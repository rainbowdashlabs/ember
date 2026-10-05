/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes.RemoteProtocolDetail;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService.SharedProtocolView;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Consumer endpoints for test protocols shared by federation partners. The service resolves the
 * owning station transparently whether it lives on this instance or on another one. The endpoints
 * an owning station serves to its partners live in {@link RemoteTestProtocolRoutes}.
 */
@Singleton
public class FederatedTestProtocolRoutes implements Routes {

    private final TestProtocolService service;

    @Inject
    public FederatedTestProtocolRoutes(TestProtocolService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federated/protocols", this::federatedBrowseProtocols, StationPermission.USER);
        routes.get(
                prefix + "/federated/{stationuid}/protocols/{id}",
                this::federatedGetProtocol,
                StationPermission.PROTOCOL_MANAGER);
        routes.post(
                prefix + "/federated/{stationuid}/protocols/{id}/copy",
                this::federatedCopyProtocol,
                StationPermission.PROTOCOL_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/federated/protocols",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SharedProtocolView[].class)))
    private void federatedBrowseProtocols(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(service.browseSharedProtocolViews(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/protocols/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RemoteProtocolDetail.class)))
    private void federatedGetProtocol(Context ctx) {
        var session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.getFederatedProtocol(session.stationId(), stationUid, protocolId));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/protocols/{id}/copy",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = TestProtocol.class)))
    private void federatedCopyProtocol(Context ctx) {
        var session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();
        var copied = service.copyFederatedProtocol(session.stationId(), stationUid, protocolId);
        ctx.status(HttpStatus.CREATED).json(copied);
    }
}
