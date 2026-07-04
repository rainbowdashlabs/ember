/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.Procurement;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * Routes for procurement request management including creating, fulfilling,
 * and cancelling procurement requests for inventory items.
 */
@Singleton
public class ProcurementRoutes implements Routes {
    private final ProcurementService procurementService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final InventoryRepository inventoryRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public ProcurementRoutes(
            ProcurementService procurementService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            InventoryRepository inventoryRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.procurementService = procurementService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.inventoryRepository = inventoryRepository;
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
        UserSession session = UserSession.from(ctx);
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
        UserSession session = UserSession.from(ctx);
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
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateProcurementRequest.class);
        inventoryRepository.findById(request.inventoryId()).orElseThrow(NotFoundResponse::new);
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var procurement = procurementService.findById(id).orElseThrow(NotFoundResponse::new);
        if (procurement.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (procurementService.fulfill(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var procurement = procurementService.findById(id).orElseThrow(NotFoundResponse::new);
        if (procurement.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (procurementService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private ProcurementResponse toResponse(Procurement procurement) {
        var member = stationMemberRepository.findById(procurement.memberId()).orElse(null);
        String memberName = member != null
                ? accountRepository
                        .findById(member.accountId())
                        .map(a -> (a.firstName() + " " + a.lastName()).trim())
                        .orElse("")
                : "";
        MemberIdentity memberIdentity =
                member != null ? memberIdentityFactory.local(member.stationId(), procurement.memberId()) : null;
        Inventory inventory =
                inventoryRepository.findById(procurement.inventoryId()).orElse(null);
        String inventoryName = inventory != null ? inventory.name() : "";
        String sizeLabel = null;
        if (procurement.sizeId() != null && inventory != null) {
            sizeLabel = inventoryRepository.findSizes(procurement.inventoryId()).stream()
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

    public record ProcurementResponse(
            int id,
            int inventoryId,
            String inventoryName,
            int memberId,
            String memberName,
            MemberIdentity memberIdentity,
            Integer sizeId,
            String sizeLabel,
            String notes,
            Instant requestedAt,
            Instant fulfilledAt) {}

    public record CreateProcurementRequest(int inventoryId, int memberId, Integer sizeId, String notes) {}
}
