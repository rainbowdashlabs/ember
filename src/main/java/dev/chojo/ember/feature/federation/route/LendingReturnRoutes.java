/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService;
import dev.chojo.ember.feature.federation.service.LendingReturnService;
import dev.chojo.ember.feature.federation.service.LendingReturnService.LoanPiece;
import dev.chojo.ember.feature.federation.service.LendingReturnService.PieceCheck;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/** Taking lent gear back piece by piece, and reminding the borrowing station of gear past its last day. */
@Singleton
public class LendingReturnRoutes implements Routes {
    private final LendingReturnService service;
    private final LendingRequestViewService views;

    @Inject
    public LendingReturnRoutes(LendingReturnService service, LendingRequestViewService views) {
        this.service = service;
        this.views = views;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/lending/requests/{id}/return-check",
                this::pieces,
                StationPermission.INVENTORY_LENDING_MANAGER,
                StationModule.INVENTORY_LENDING);
        routes.post(
                prefix + "/lending/requests/{id}/return-check",
                this::checkReturn,
                StationPermission.INVENTORY_LENDING_MANAGER,
                StationModule.INVENTORY_LENDING);
        routes.post(
                prefix + "/lending/requests/{id}/remind",
                this::remind,
                StationPermission.INVENTORY_LENDING_MANAGER,
                StationModule.INVENTORY_LENDING);
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/return-check",
            methods = HttpMethod.GET,
            summary = "The pieces a loan of this station holds, to check back",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoanPiece[].class)))
    private void pieces(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(service.pieces(views.requireOwner(pathInt(ctx, "id"), session.stationId())));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/return-check",
            methods = HttpMethod.POST,
            summary = "Takes the gear of a loan back, every piece in order, damaged or missing",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReturnCheckBody.class)),
            responses = @OpenApiResponse(status = "204"))
    private void checkReturn(Context ctx) {
        var session = StationSession.from(ctx);
        var request = views.requireOwner(pathInt(ctx, "id"), session.stationId());
        var body = ctx.bodyAsClass(ReturnCheckBody.class);
        service.checkReturn(
                request,
                session.stationId(),
                session.member().id(),
                NameParts.of(session.user().account()).called(),
                Objects.requireNonNullElse(body.pieces(), List.of()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/remind",
            methods = HttpMethod.POST,
            summary = "Reminds the borrowing station of gear past its last day",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void remind(Context ctx) {
        var session = StationSession.from(ctx);
        var request = views.requireOwner(pathInt(ctx, "id"), session.stationId());
        service.remind(
                request,
                session.stationId(),
                session.member().id(),
                NameParts.of(session.user().account()).called(),
                LocalDate.now());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record ReturnCheckBody(List<PieceCheck> pieces) {}
}
