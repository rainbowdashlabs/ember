/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.LendingCart;
import dev.chojo.ember.feature.federation.entity.LendingCartLine;
import dev.chojo.ember.feature.federation.service.LendingCartService;
import dev.chojo.ember.feature.federation.service.LendingCartService.BorrowAgainResult;
import dev.chojo.ember.feature.federation.service.LendingCartService.CartLineCheck;
import dev.chojo.ember.feature.federation.service.LendingCartService.NewCartLine;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.LendingRequestResponse;
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
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The station's cart of gear from its partners, for whoever may ask for gear: reading it, changing
 * it, asking whether it is still free, sending it, and filling it again from an earlier request.
 */
@Singleton
public class LendingCartRoutes implements Routes {
    private final LendingCartService service;
    private final LendingRequestViewService views;

    @Inject
    public LendingCartRoutes(LendingCartService service, LendingRequestViewService views) {
        this.service = service;
        this.views = views;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/lending/cart",
                this::getCart,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.put(
                prefix + "/lending/cart",
                this::setDetails,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.post(
                prefix + "/lending/cart/lines",
                this::addLine,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.put(
                prefix + "/lending/cart/lines/{lineId}",
                this::setQuantity,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.delete(
                prefix + "/lending/cart/lines/{lineId}",
                this::removeLine,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.get(
                prefix + "/lending/cart/check",
                this::check,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.post(
                prefix + "/lending/cart/checkout",
                this::checkout,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
        routes.post(
                prefix + "/lending/requests/{id}/borrow-again",
                this::borrowAgain,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationModule.INVENTORY_LENDING);
    }

    @OpenApi(
            path = "/api/v1/lending/cart",
            methods = HttpMethod.GET,
            summary = "The station's cart with its lines",
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CartView.class)))
    private void getCart(Context ctx) {
        ctx.json(view(StationSession.from(ctx).stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/cart",
            methods = HttpMethod.PUT,
            summary = "Sets the days, the occasion and the note of the cart",
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CartDetails.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CartView.class)))
    private void setDetails(Context ctx) {
        int stationId = StationSession.from(ctx).stationId();
        var body = ctx.bodyAsClass(CartDetails.class);
        service.setDetails(stationId, body.dateFrom(), body.dateTo(), body.occasion(), body.note());
        ctx.json(view(stationId));
    }

    @OpenApi(
            path = "/api/v1/lending/cart/lines",
            methods = HttpMethod.POST,
            summary = "Puts gear of a partner in the cart, adding to the same line where there is one",
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewCartLine.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CartView.class)))
    private void addLine(Context ctx) {
        int stationId = StationSession.from(ctx).stationId();
        service.add(stationId, ctx.bodyAsClass(NewCartLine.class));
        ctx.status(HttpStatus.CREATED).json(view(stationId));
    }

    @OpenApi(
            path = "/api/v1/lending/cart/lines/{lineId}",
            methods = HttpMethod.PUT,
            summary = "Sets how many of a counted line are wanted",
            pathParams = @OpenApiParam(name = "lineId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuantityBody.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CartView.class)))
    private void setQuantity(Context ctx) {
        int stationId = StationSession.from(ctx).stationId();
        service.setQuantity(
                stationId,
                pathInt(ctx, "lineId"),
                ctx.bodyAsClass(QuantityBody.class).quantity());
        ctx.json(view(stationId));
    }

    @OpenApi(
            path = "/api/v1/lending/cart/lines/{lineId}",
            methods = HttpMethod.DELETE,
            summary = "Takes a line out of the cart",
            pathParams = @OpenApiParam(name = "lineId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CartView.class)))
    private void removeLine(Context ctx) {
        int stationId = StationSession.from(ctx).stationId();
        service.remove(stationId, pathInt(ctx, "lineId"));
        ctx.json(view(stationId));
    }

    @OpenApi(
            path = "/api/v1/lending/cart/check",
            methods = HttpMethod.GET,
            summary = "What of the cart is free on its days, asked of the lending stations",
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CartLineCheck[].class)))
    private void check(Context ctx) {
        ctx.json(service.check(StationSession.from(ctx).stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/cart/checkout",
            methods = HttpMethod.POST,
            summary = "Sends the cart as one request per lending station",
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CheckoutView.class)))
    private void checkout(Context ctx) {
        var session = StationSession.from(ctx);
        var result = service.checkout(
                session.stationId(),
                session.member().id(),
                NameParts.of(session.user().account()).called());
        ctx.json(new CheckoutView(
                result.sent().stream()
                        .map(request -> views.describe(request, session.stationId()))
                        .toList(),
                result.failed(),
                result.notesMissing()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/borrow-again",
            methods = HttpMethod.POST,
            summary = "Puts the lines of an answered request back in the cart",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BorrowAgainResult.class)))
    private void borrowAgain(Context ctx) {
        ctx.json(service.borrowAgain(StationSession.from(ctx).stationId(), pathInt(ctx, "id")));
    }

    private CartView view(int stationId) {
        LendingCart cart = service.cart(stationId);
        var names = new HashMap<UUID, String>();
        var lines = service.lines(stationId).stream()
                .map(line -> CartLineView.of(
                        line,
                        names.computeIfAbsent(line.lendingStationUid(), uid -> service.stationName(uid, stationId))))
                .toList();
        return new CartView(cart.dateFrom(), cart.dateTo(), cart.occasion(), cart.note(), lines);
    }

    /**
     * What the cart asks of every lending station.
     *
     * @param dateFrom the first day wanted, or {@code null}
     * @param dateTo   the last day wanted, or {@code null} for a single day
     * @param occasion what the gear is wanted for
     * @param note     the first message of every request
     */
    public record CartDetails(
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo,
            @Nullable String occasion,
            @Nullable String note) {}

    public record QuantityBody(int quantity) {}

    /**
     * The cart as the station sees it.
     *
     * @param lines in the order they were put in
     */
    public record CartView(
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo,
            String occasion,
            String note,
            List<CartLineView> lines) {}

    /**
     * One line of the cart with the name of its lending station.
     *
     * @param stationId   the lending station
     * @param stationName what this station calls it
     */
    public record CartLineView(
            int id,
            UUID stationId,
            String stationName,
            int inventoryId,
            @Nullable Integer artId,
            @Nullable Integer itemId,
            int quantity,
            String inventoryName,
            String lineName,
            @Nullable String pictureKey) {

        static CartLineView of(LendingCartLine line, String stationName) {
            return new CartLineView(
                    line.id(),
                    line.lendingStationUid(),
                    stationName,
                    line.inventoryId(),
                    line.artId(),
                    line.itemId(),
                    line.quantity(),
                    line.inventoryName(),
                    line.lineName(),
                    line.pictureKey());
        }
    }

    /**
     * What sending the cart did.
     *
     * @param sent         the requests opened
     * @param failed       the lending stations whose lines stayed in the cart
     * @param notesMissing the lending stations whose request did not get the note
     */
    public record CheckoutView(List<LendingRequestResponse> sent, List<String> failed, List<String> notesMissing) {}
}
