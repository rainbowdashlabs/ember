/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.Procurement;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
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

import java.time.Instant;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Routes for procurement request management including creating, fulfilling,
 * and cancelling procurement requests for inventory items.
 */
@Singleton
public class ProcurementRoutes implements Routes {
    private final ProcurementService procurementService;
    private final StationMemberService memberService;
    private final MemberNameResolver names;
    private final InventoryService inventoryService;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public ProcurementRoutes(
            ProcurementService procurementService,
            StationMemberService memberService,
            MemberNameResolver names,
            InventoryService inventoryService,
            MemberIdentityFactory memberIdentityFactory) {
        this.procurementService = procurementService;
        this.memberService = memberService;
        this.names = names;
        this.inventoryService = inventoryService;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/procurement",
                this::list,
                StationPermission.INVENTORY_PROCUREMENT,
                StationPermission.INVENTORY_READ);
        routes.get(
                prefix + "/procurement/open",
                this::listOpen,
                StationPermission.INVENTORY_PROCUREMENT,
                StationPermission.INVENTORY_READ);
        routes.post(prefix + "/procurement", this::create, StationPermission.INVENTORY_PROCUREMENT);
        routes.put(prefix + "/procurement/{id}/fulfill", this::fulfill, StationPermission.INVENTORY_PROCUREMENT);
        routes.delete(prefix + "/procurement/{id}", this::delete, StationPermission.INVENTORY_PROCUREMENT);
    }

    @OpenApi(
            path = "/api/v1/procurement",
            methods = HttpMethod.GET,
            summary = "List all procurement requests for the current station",
            tags = {"Procurement"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcurementResponse[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var procurements = procurementService.findByStation(session.stationId());
        ctx.json(procurements.stream().map(this::toResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/procurement/open",
            methods = HttpMethod.GET,
            summary = "List open (unfulfilled) procurement requests for the current station",
            tags = {"Procurement"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcurementResponse[].class)))
    private void listOpen(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var procurements = procurementService.findOpen(session.stationId());
        ctx.json(procurements.stream().map(this::toResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/procurement",
            methods = HttpMethod.POST,
            summary = "Create a procurement request",
            tags = {"Procurement"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateProcurementRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = ProcurementResponse.class)))
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CreateProcurementRequest.class);
        var procurement = procurementService.create(
                session.stationId(), request.inventoryId(), request.memberId(), request.sizeId(), request.notes());
        ctx.status(HttpStatus.CREATED).json(toResponse(procurement));
    }

    @OpenApi(
            path = "/api/v1/procurement/{id}/fulfill",
            methods = HttpMethod.PUT,
            summary = "Mark a procurement request as fulfilled",
            tags = {"Procurement"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void fulfill(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, procurementService::findById, Procurement::stationId);
        if (procurementService.fulfill(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw InventoryRefusal.PROCUREMENT_NOT_FULFILLED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/procurement/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a procurement request",
            tags = {"Procurement"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, procurementService::findById, Procurement::stationId);
        if (procurementService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw InventoryRefusal.PROCUREMENT_NOT_DELETED.raise();
        }
    }

    /** The order as the API shows it. An order a cluster places for its own store names no member. */
    private ProcurementResponse toResponse(Procurement procurement) {
        var member = procurement.memberId() == null
                ? null
                : memberService.findById(procurement.memberId()).orElse(null);
        String memberName = member != null ? Objects.requireNonNullElse(names.called(member.id()), "") : "";
        MemberIdentity memberIdentity =
                member != null ? memberIdentityFactory.local(member.stationId(), procurement.memberId()) : null;
        Inventory inventory =
                inventoryService.findById(procurement.inventoryId()).orElse(null);
        String inventoryName = inventory != null ? inventory.name() : "";
        String sizeLabel = null;
        if (procurement.sizeId() != null && inventory != null) {
            sizeLabel = inventoryService.findSizes(procurement.inventoryId()).stream()
                    .filter(s -> s.id() == procurement.sizeId())
                    .map(InventorySize::label)
                    .findFirst()
                    .orElse(null);
        }
        return new ProcurementResponse(
                procurement.id(),
                procurement.inventoryId(),
                inventoryName,
                procurement.memberId(),
                memberName,
                memberIdentity,
                procurement.sizeId(),
                sizeLabel,
                procurement.notes(),
                procurement.requestedAt(),
                procurement.fulfilledAt());
    }

    /**
     * @param memberId who it is for, or {@code null} for an order a cluster places for its own store
     */
    public record ProcurementResponse(
            int id,
            int inventoryId,
            String inventoryName,
            @Nullable Integer memberId,
            String memberName,
            @Nullable MemberIdentity memberIdentity,
            @Nullable Integer sizeId,
            @Nullable String sizeLabel,
            String notes,
            Instant requestedAt,
            @Nullable Instant fulfilledAt) {}

    /**
     * @param memberId who it is for, left out for an order a cluster places for its own store
     */
    public record CreateProcurementRequest(
            int inventoryId,
            @Nullable Integer memberId,
            @Nullable Integer sizeId,
            @Nullable String notes) {}
}
