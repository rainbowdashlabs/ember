/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.entity.LentOutItem;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.AvailableItemDetail;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.EnrichedItem;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.EnrichedMessage;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.LendingRequestResponse;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.members.entity.NameParts;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for cross-station inventory lending, chat messages, and date blocking, as seen by the
 * station that owns the request. The aggregated partner inventory listing lives in
 * {@link FederatedLendingRoutes}, the server-to-server endpoints in {@link RemoteLendingRoutes}.
 */
@Singleton
public class LendingRoutes implements Routes {

    private final LendingService service;
    private final LendingRequestViewService views;

    @Inject
    public LendingRoutes(LendingService service, LendingRequestViewService views) {
        this.service = service;
        this.views = views;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/lending/requests",
                this::listRequests,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.post(prefix + "/lending/requests", this::createRequest, StationPermission.INVENTORY_LENDING_REQUEST);
        routes.get(
                prefix + "/lending/requests/{id}",
                this::getRequest,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationPermission.INVENTORY_LENDING_MANAGER);

        routes.post(
                prefix + "/lending/requests/{id}/approve",
                this::approveRequest,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.post(
                prefix + "/lending/requests/{id}/decline",
                this::declineRequest,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.get(
                prefix + "/lending/requests/{id}/available-items",
                this::availableItemsForRequest,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.post(
                prefix + "/lending/requests/{id}/assign-items",
                this::assignItems,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.post(
                prefix + "/lending/requests/{id}/lent", this::markLent, StationPermission.INVENTORY_LENDING_MANAGER);

        routes.post(
                prefix + "/lending/requests/{id}/returned",
                this::markReturned,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.post(
                prefix + "/lending/requests/{id}/close",
                this::closeRequest,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationPermission.INVENTORY_LENDING_MANAGER);

        routes.get(
                prefix + "/lending/inventory/{inventoryId}/lent-out",
                this::lentOutByInventory,
                StationPermission.INVENTORY_READ);

        routes.get(
                prefix + "/lending/requests/{id}/messages",
                this::getMessages,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.post(
                prefix + "/lending/requests/{id}/messages",
                this::sendMessage,
                StationPermission.INVENTORY_LENDING_REQUEST,
                StationPermission.INVENTORY_LENDING_MANAGER);

        routes.get(prefix + "/lending/blocks", this::listBlocks, StationPermission.INVENTORY_LENDING_MANAGER);
        routes.post(prefix + "/lending/blocks", this::createBlock, StationPermission.INVENTORY_LENDING_MANAGER);
        routes.delete(prefix + "/lending/blocks/{id}", this::deleteBlock, StationPermission.INVENTORY_LENDING_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/lending/requests",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingRequestResponse[].class)))
    private void listRequests(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(views.requestsFor(
                session.stationId(), session.hasPermission(StationPermission.INVENTORY_LENDING_MANAGER)));
    }

    @OpenApi(
            path = "/api/v1/lending/requests",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateLendingRequest.class)),
            responses =
                    @OpenApiResponse(status = "201", content = @OpenApiContent(from = LendingRequestResponse.class)))
    private void createRequest(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateLendingRequest.class);
        if (views.isOwnStation(session.stationId(), req.owningStationId())) {
            throw Refusal.LENDING_FROM_OWN_STATION.raise();
        }
        if (req.dateFrom() == null) {
            throw Refusal.LENDING_FIRST_DAY_MISSING.raise();
        }

        LocalDate dateTo = req.dateTo() != null ? req.dateTo() : req.dateFrom();
        var lines = req.items() == null
                ? List.<LendingService.RequestLine>of()
                : req.items().stream()
                        .map(item -> new LendingService.RequestLine(
                                item.inventoryId(), item.itemId(), item.artId(), item.quantity(), item.needId()))
                        .toList();
        var request = service.createRequest(
                session.stationId(),
                req.owningStationId(),
                req.dateFrom(),
                dateTo,
                session.member().id(),
                req.eventId(),
                req.eventDate(),
                views.occasionOf(session.stationId(), req.eventId()),
                lines);

        ctx.status(HttpStatus.CREATED).json(views.describe(request, session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingRequestDetail.class)))
    private void getRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var request = views.requireParty(id, session.stationId());
        ctx.json(new LendingRequestDetail(views.describe(request, session.stationId()), views.describeItems(id)));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/approve",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingRequestResponse.class)))
    private void approveRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireOwner(id, session.stationId());
        service.approveRequest(id, session.stationId());
        ctx.json(views.describe(
                service.findRequest(id).orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_AFTER_APPROVAL::raise),
                session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/decline",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DeclineBody.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingRequestResponse.class)))
    private void declineRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireOwner(id, session.stationId());
        var body = ctx.bodyAsClass(DeclineBody.class);
        service.declineRequest(id, session.stationId(), body.reason());
        ctx.json(views.describe(
                service.findRequest(id).orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_AFTER_DECLINE::raise),
                session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/available-items",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AvailableItemDetail[].class)))
    private void availableItemsForRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireOwner(id, session.stationId());
        ctx.json(views.availableItems(id, session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/assign-items",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignItemsRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void assignItems(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var request = views.requireOwner(id, session.stationId());
        if (request.status() != LendingStatus.APPROVED) {
            throw Refusal.LENDING_REQUEST_NOT_APPROVED.raise();
        }

        var assignments = ctx.bodyAsClass(AssignItemsRequest.class);
        if (assignments.items() != null) {
            for (var a : assignments.items()) {
                service.assignItem(a.requestItemId(), a.itemId(), session.stationId());
            }
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/lent",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingRequestResponse.class)))
    private void markLent(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireOwner(id, session.stationId());
        service.markLent(id, session.stationId());
        ctx.json(views.describe(
                service.findRequest(id).orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_AFTER_LENDING::raise),
                session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/returned",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingRequestResponse.class)))
    private void markReturned(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireParty(id, session.stationId());
        service.markReturned(id, session.stationId());
        ctx.json(views.describe(
                service.findRequest(id).orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_AFTER_RETURN::raise),
                session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/close",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingRequestResponse.class)))
    private void closeRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireParty(id, session.stationId());
        service.closeRequest(id, session.stationId());
        ctx.json(views.describe(
                service.findRequest(id).orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_AFTER_CLOSING::raise),
                session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/messages",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EnrichedMessage[].class)))
    private void getMessages(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireParty(id, session.stationId());
        ctx.json(service.getMessages(id, session.stationId()).stream()
                .map(message -> views.describe(message, session.stationId()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/lending/requests/{id}/messages",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MessageBody.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = LendingMessage.class)))
    private void sendMessage(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        views.requireParty(id, session.stationId());
        var body = ctx.bodyAsClass(MessageBody.class);
        if (body.message() == null || body.message().isBlank()) {
            throw Refusal.LENDING_MESSAGE_NEEDS_TEXT.raise();
        }
        String senderName = NameParts.of(session.account()).called();
        var msg = service.sendMessage(id, session.stationId(), session.member().id(), senderName, body.message());
        ctx.status(HttpStatus.CREATED).json(msg);
    }

    @OpenApi(
            path = "/api/v1/lending/blocks",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryBlock[].class)))
    private void listBlocks(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findBlocks(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/blocks",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateBlockRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryBlock.class)))
    private void createBlock(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateBlockRequest.class);
        if (req.blockFrom() == null || req.blockTo() == null) {
            throw Refusal.LENDING_BLOCK_SPAN_MISSING.raise();
        }
        var block = service.createBlock(
                session.stationId(),
                req.inventoryId(),
                req.itemId(),
                req.blockFrom(),
                req.blockTo(),
                req.reason() != null ? req.reason() : "");
        ctx.status(HttpStatus.CREATED).json(block);
    }

    @OpenApi(
            path = "/api/v1/lending/blocks/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteBlock(Context ctx) {
        int id = pathInt(ctx, "id");
        service.deleteBlock(id, UserSession.from(ctx).stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/lending/inventory/{inventoryId}/lent-out",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LentOutItem[].class)))
    private void lentOutByInventory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        ctx.json(views.lentOut(inventoryId, session.stationId()));
    }

    /**
     * @param eventId   the appointment the list was collected for, or {@code null}
     * @param eventDate the date of that appointment, or {@code null}
     */
    public record CreateLendingRequest(
            UUID owningStationId,
            LocalDate dateFrom,
            @Nullable LocalDate dateTo,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate,
            List<LendingItemRequest> items) {}

    /**
     * @param artId  the kind of thing the line asks for, or {@code null}
     * @param needId the line of an appointment's needs this fills, or {@code null}
     */
    public record LendingItemRequest(
            @Nullable Integer inventoryId,
            @Nullable Integer itemId,
            @Nullable Integer artId,
            int quantity,
            @Nullable Integer needId) {}

    public record DeclineBody(@Nullable String reason) {}

    public record MessageBody(String message) {}

    public record CreateBlockRequest(
            @Nullable Integer inventoryId,
            @Nullable Integer itemId,
            LocalDate blockFrom,
            LocalDate blockTo,
            @Nullable String reason) {}

    public record LendingRequestDetail(LendingRequestResponse request, List<EnrichedItem> items) {}

    public record AssignItemsRequest(List<ItemAssignment> items) {}

    public record ItemAssignment(int requestItemId, int itemId) {}
}
