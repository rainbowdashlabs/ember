/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.service.LendingPictureService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.federation.service.LendingService.AvailableInventoryResult;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consumer endpoints for inventory offered by federation partners. Lending has no local
 * counterpart to this listing - it only exists as an aggregation across partners. The endpoints
 * an owning station serves to its partners live in {@link RemoteLendingRoutes}. The photos of what
 * partners offer come from this instance's own copies, never from the partner's address.
 */
@Singleton
public class FederatedLendingRoutes implements Routes {

    private final LendingService service;

    private final LendingPictureService pictures;

    @Inject
    public FederatedLendingRoutes(LendingService service, LendingPictureService pictures) {
        this.service = service;
        this.pictures = pictures;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/federated/lending/available",
                this::listAvailable,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.get(
                prefix + "/federated/lending/pictures/{stationUid}/{pictureKey}",
                this::picture,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
    }

    @OpenApi(
            path = "/api/v1/federated/lending/pictures/{stationUid}/{pictureKey}",
            methods = HttpMethod.GET,
            summary = "The photo a partner station shows offered gear with",
            pathParams = {
                @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "pictureKey", type = UUID.class, required = true)
            },
            queryParams = @OpenApiParam(name = "size", type = Integer.class, description = "Side in pixels"),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void picture(Context ctx) {
        var session = StationSession.from(ctx);
        UUID partner = RouteSupport.pathUuid(ctx, "stationUid");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        UUID pictureKey = RouteSupport.pathUuid(ctx, "pictureKey");
        var photo = pictures.federatedPicture(session.stationId(), partner, pictureKey, size);
        ctx.contentType(photo.contentType());
        ctx.header("Cache-Control", "private, max-age=86400");
        ctx.result(photo.data());
    }

    @OpenApi(
            path = "/api/v1/federated/lending/available",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AvailableInventoryResult.class)))
    private void listAvailable(Context ctx) {
        var session = StationSession.from(ctx);
        String query = ctx.queryParam("q");
        String fromParam = ctx.queryParam("from");
        String toParam = ctx.queryParam("to");
        LocalDate dateFrom = fromParam != null && !fromParam.isBlank() ? LocalDate.parse(fromParam) : null;
        LocalDate dateTo = toParam != null && !toParam.isBlank() ? LocalDate.parse(toParam) : dateFrom;
        ctx.json(service.findAvailableInventory(session.stationId(), query, dateFrom, dateTo));
    }
}
