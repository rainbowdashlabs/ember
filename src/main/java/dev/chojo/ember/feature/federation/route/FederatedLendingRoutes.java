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
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProductDays;
import dev.chojo.ember.feature.federation.service.LendingCatalogService;
import dev.chojo.ember.feature.federation.service.LendingCatalogService.ProductDetail;
import dev.chojo.ember.feature.federation.service.LendingCatalogService.ProductList;
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
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

    private final LendingCatalogService catalog;

    @Inject
    public FederatedLendingRoutes(
            LendingService service, LendingPictureService pictures, LendingCatalogService catalog) {
        this.service = service;
        this.pictures = pictures;
        this.catalog = catalog;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/federated/lending/available",
                this::listAvailable,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.get(
                prefix + "/federated/lending/products",
                this::listProducts,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.get(
                prefix + "/federated/lending/products/{stationUid}/{inventoryId}",
                this::product,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.get(
                prefix + "/federated/lending/products/{stationUid}/{inventoryId}/days",
                this::productDays,
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
            path = "/api/v1/federated/lending/products",
            methods = HttpMethod.GET,
            summary = "What the lending partners offer, one product per inventory, nearest first",
            queryParams = {
                @OpenApiParam(name = "q", description = "Search over names and descriptions"),
                @OpenApiParam(name = "from", description = "First day wanted, yyyy-MM-dd"),
                @OpenApiParam(name = "to", description = "Last day wanted, yyyy-MM-dd")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProductList.class)))
    private void listProducts(Context ctx) {
        var session = StationSession.from(ctx);
        LocalDate dateFrom = dayParam(ctx, "from");
        LocalDate dateTo = dayParam(ctx, "to");
        ctx.json(catalog.findProducts(
                session.stationId(), ctx.queryParam("q"), dateFrom, dateTo == null ? dateFrom : dateTo));
    }

    @OpenApi(
            path = "/api/v1/federated/lending/products/{stationUid}/{inventoryId}",
            methods = HttpMethod.GET,
            summary = "One product of a lending partner with its kinds or single pieces",
            pathParams = {
                @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true)
            },
            queryParams = {
                @OpenApiParam(name = "from", description = "First day wanted, yyyy-MM-dd"),
                @OpenApiParam(name = "to", description = "Last day wanted, yyyy-MM-dd")
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProductDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void product(Context ctx) {
        var session = StationSession.from(ctx);
        LocalDate dateFrom = dayParam(ctx, "from");
        LocalDate dateTo = dayParam(ctx, "to");
        ctx.json(catalog.findProduct(
                session.stationId(),
                RouteSupport.pathUuid(ctx, "stationUid"),
                RouteSupport.pathInt(ctx, "inventoryId"),
                dateFrom,
                dateTo == null ? dateFrom : dateTo));
    }

    @OpenApi(
            path = "/api/v1/federated/lending/products/{stationUid}/{inventoryId}/days",
            methods = HttpMethod.GET,
            summary = "What is free of a partner's product on each day of a month",
            pathParams = {
                @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true)
            },
            queryParams = {
                @OpenApiParam(name = "month", required = true, description = "The month, yyyy-MM"),
                @OpenApiParam(name = "itemId", type = Integer.class, description = "One single piece")
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RemoteProductDays.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void productDays(Context ctx) {
        var session = StationSession.from(ctx);
        String itemParam = ctx.queryParam("itemId");
        Integer itemId;
        try {
            itemId = itemParam == null || itemParam.isBlank() ? null : Integer.valueOf(itemParam);
        } catch (NumberFormatException e) {
            throw FederationRefusal.LENDING_PRODUCT_NOT_OFFERED.raise();
        }
        ctx.json(catalog.findProductDays(
                session.stationId(),
                RouteSupport.pathUuid(ctx, "stationUid"),
                RouteSupport.pathInt(ctx, "inventoryId"),
                ctx.queryParam("month"),
                itemId));
    }

    private static @Nullable LocalDate dayParam(Context ctx, String name) {
        String value = ctx.queryParam(name);
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw FederationRefusal.LENDING_DAY_NOT_A_DAY.raise();
        }
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
