/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.CheckItemRequest;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository.MemberCheckSummary;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
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
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for inventory check operations including starting, locking, recording item checks,
 * and completing inventory checks with result summaries.
 */
@Singleton
public class InventoryCheckRoutes implements Routes {
    private final InventoryCheckService checkService;
    private final InventoryService inventoryService;
    private final InventoryContainerService containerService;
    private final StationMemberRepository stationMemberRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public InventoryCheckRoutes(
            InventoryCheckService checkService,
            InventoryService inventoryService,
            InventoryContainerService containerService,
            StationMemberRepository stationMemberRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.checkService = checkService;
        this.inventoryService = inventoryService;
        this.containerService = containerService;
        this.stationMemberRepository = stationMemberRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * Asserts the given container belongs to the caller's station.
     */
    private void verifyContainerInStation(int containerId, UserSession session) {
        var container = containerService.findById(containerId).orElseThrow(NotFoundResponse::new);
        if (container.stationId() != session.stationId()) {
            throw new NotFoundResponse();
        }
    }

    /**
     * Asserts the given item's inventory belongs to the caller's station.
     */
    private void verifyItemInStation(int itemId, UserSession session) {
        var item = inventoryService.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        var inventory = inventoryService.findById(item.inventoryId()).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new NotFoundResponse();
        }
    }

    /**
     * Asserts the given member belongs to the caller's station.
     */
    private void verifyMemberInStation(int memberId, UserSession session) {
        var member = stationMemberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        if (member.stationId() != session.stationId()) {
            throw new NotFoundResponse();
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/inventory-checks", this::overview, StationPermission.INVENTORY_CHECK);
        routes.post(prefix + "/inventory-checks/{memberId}/start", this::startCheck, StationPermission.INVENTORY_CHECK);
        routes.post(
                prefix + "/inventory-checks/{memberId}/complete",
                this::completeCheck,
                StationPermission.INVENTORY_CHECK);
        routes.post(
                prefix + "/inventory-checks/{memberId}/cancel", this::cancelCheck, StationPermission.INVENTORY_CHECK);
        routes.get(prefix + "/inventory-checks/{memberId}/last", this::lastCheck, StationPermission.INVENTORY_CHECK);
        routes.put(prefix + "/inventory-checks/{memberId}/assign", this::assignItem, StationPermission.INVENTORY_CHECK);
        routes.put(
                prefix + "/inventory-checks/{memberId}/unassign",
                this::unassignItem,
                StationPermission.INVENTORY_CHECK);
        routes.post(
                prefix + "/inventory-checks/{memberId}/create-assign",
                this::createAndAssign,
                StationPermission.INVENTORY_CHECK);
        routes.get(prefix + "/inventory-checks/next", this::nextMember, StationPermission.INVENTORY_CHECK);
        routes.get(
                prefix + "/inventory-checks/container/{containerId}/expected",
                this::containerExpectedItems,
                StationPermission.INVENTORY_CHECK);
        routes.get(
                prefix + "/inventory-checks/container/{containerId}/last-results",
                this::containerLastItemResults,
                StationPermission.INVENTORY_CHECK);
        routes.get(
                prefix + "/inventory-checks/item/{itemId}/history",
                this::itemCheckHistory,
                StationPermission.INVENTORY_CHECK);
        routes.post(
                prefix + "/inventory-checks/container/{containerId}/complete",
                this::completeContainerCheck,
                StationPermission.INVENTORY_CHECK);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/container/{containerId}/expected",
            methods = HttpMethod.GET,
            summary = "List the items expected inside a container for a check",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "containerId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "deep", type = Boolean.class),
            responses = @OpenApiResponse(status = "200"))
    private void containerExpectedItems(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int containerId = pathInt(ctx, "containerId");
        verifyContainerInStation(containerId, session);
        boolean deep = "true".equalsIgnoreCase(ctx.queryParam("deep"));
        ctx.json(checkService.expectedContainerItems(containerId, deep));
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/container/{containerId}/last-results",
            methods = HttpMethod.GET,
            summary = "Latest check result per item currently in the container",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "containerId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "deep", type = Boolean.class),
            responses = @OpenApiResponse(status = "200"))
    private void containerLastItemResults(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int containerId = pathInt(ctx, "containerId");
        verifyContainerInStation(containerId, session);
        boolean deep = "true".equalsIgnoreCase(ctx.queryParam("deep"));
        ctx.json(checkService.lastCheckForContainerItems(containerId, deep));
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/item/{itemId}/history",
            methods = HttpMethod.GET,
            summary = "All recorded check results for a single item, newest-first",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "itemId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void itemCheckHistory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int itemId = pathInt(ctx, "itemId");
        verifyItemInStation(itemId, session);
        ctx.json(checkService.findCheckHistoryForItem(itemId));
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/container/{containerId}/complete",
            methods = HttpMethod.POST,
            summary = "Complete a container-scope inventory check",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "containerId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CompleteContainerCheckRequest.class)),
            responses = @OpenApiResponse(status = "201"))
    private void completeContainerCheck(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int containerId = pathInt(ctx, "containerId");
        var request = ctx.bodyAsClass(CompleteContainerCheckRequest.class);
        if (request.items() == null) {
            throw new BadRequestResponse("items are required");
        }
        List<CheckItemRequest> items = request.items().stream()
                .map(i ->
                        new CheckItemRequest(i.itemId(), i.inventoryId(), i.result(), i.note() != null ? i.note() : ""))
                .toList();
        var check = checkService.completeContainerCheck(
                session.stationId(), containerId, session.member().id(), request.deep(), items);
        ctx.status(HttpStatus.CREATED).json(check);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks",
            methods = HttpMethod.GET,
            summary = "Get inventory check overview for the current station",
            tags = {"Inventory Checks"},
            responses = @OpenApiResponse(status = "200"))
    private void overview(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var summaries = checkService.getCheckOverview(session.stationId());
        var enriched = summaries.stream()
                .map(s -> new EnrichedCheckSummary(s, memberIdentityFactory.local(session.stationId(), s.memberId())))
                .toList();
        ctx.json(enriched);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/{memberId}/start",
            methods = HttpMethod.POST,
            summary = "Start an inventory check for a member",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void startCheck(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        var state = checkService.startCheck(
                session.stationId(), memberId, session.member().id());
        ctx.json(state);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/{memberId}/complete",
            methods = HttpMethod.POST,
            summary = "Complete an inventory check for a member",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CompleteCheckRequest.class)),
            responses = @OpenApiResponse(status = "201"))
    private void completeCheck(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        var request = ctx.bodyAsClass(CompleteCheckRequest.class);

        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestResponse("items are required");
        }

        List<CheckItemRequest> items = request.items().stream()
                .map(i ->
                        new CheckItemRequest(i.itemId(), i.inventoryId(), i.result(), i.note() != null ? i.note() : ""))
                .toList();

        var check = checkService.completeCheck(
                session.stationId(), memberId, session.member().id(), items);
        ctx.status(HttpStatus.CREATED).json(check);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/{memberId}/cancel",
            methods = HttpMethod.POST,
            summary = "Cancel an inventory check for a member",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void cancelCheck(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        verifyMemberInStation(memberId, session);
        checkService.cancelCheck(memberId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/{memberId}/last",
            methods = HttpMethod.GET,
            summary = "Get the last inventory check for a member",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "404")})
    private void lastCheck(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        verifyMemberInStation(memberId, session);
        var detail = checkService.lastCheckDetail(memberId);
        if (detail.isEmpty()) throw new NotFoundResponse();
        ctx.json(detail.get());
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/{memberId}/assign",
            methods = HttpMethod.PUT,
            summary = "Assign an item during an inventory check",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignItemRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void assignItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        var request = ctx.bodyAsClass(AssignItemRequest.class);
        String memberName =
                session.account().firstName() + " " + session.account().lastName();
        // Unassign old item if swapping
        if (request.oldItemId() != null && request.oldItemId() > 0) {
            inventoryService.assignItem(request.oldItemId(), null, null);
        }
        inventoryService.assignItem(request.newItemId(), memberId, memberName);
        // Return refreshed check state
        var state = checkService.startCheck(
                session.stationId(), memberId, session.member().id());
        ctx.json(state);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/{memberId}/unassign",
            methods = HttpMethod.PUT,
            summary = "Unassign an item during an inventory check",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UnassignItemRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void unassignItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        var request = ctx.bodyAsClass(UnassignItemRequest.class);
        inventoryService.assignItem(request.itemId(), null, null);
        var state = checkService.startCheck(
                session.stationId(), memberId, session.member().id());
        ctx.json(state);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/{memberId}/create-assign",
            methods = HttpMethod.POST,
            summary = "Create a new item and assign it during an inventory check",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateAndAssignRequest.class)),
            responses = @OpenApiResponse(status = "201"))
    private void createAndAssign(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        var request = ctx.bodyAsClass(CreateAndAssignRequest.class);
        String memberName =
                session.account().firstName() + " " + session.account().lastName();

        // Unassign old item if swapping
        if (request.oldItemId() != null && request.oldItemId() > 0) {
            inventoryService.assignItem(request.oldItemId(), null, null);
        }

        // Create a new item and assign it
        var inv = inventoryService.findById(request.inventoryId()).orElseThrow();
        var item = inventoryService.createItem(request.inventoryId(), null, inv.name(), request.sizeId(), null);
        inventoryService.assignItem(item.id(), memberId, memberName);

        var state = checkService.startCheck(
                session.stationId(), memberId, session.member().id());
        ctx.status(HttpStatus.CREATED).json(state);
    }

    @OpenApi(
            path = "/api/v1/inventory-checks/next",
            methods = HttpMethod.GET,
            summary = "Get the next member for an inventory check",
            tags = {"Inventory Checks"},
            queryParams = @OpenApiParam(name = "currentMemberId", type = Integer.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NextMemberResponse.class)))
    private void nextMember(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int currentMemberId =
                ctx.queryParamAsClass("currentMemberId", Integer.class).getOrDefault(0);
        boolean teamOnly = ctx.queryParamAsClass("teamOnly", Boolean.class).getOrDefault(false);
        var next = checkService.nextMember(session.stationId(), currentMemberId, teamOnly);
        ctx.json(new NextMemberResponse(next.orElse(null)));
    }

    public record EnrichedCheckSummary(
            int memberId,
            String firstName,
            String lastName,
            Instant lastCheckedAt,
            String checkerFirstName,
            String checkerLastName,
            boolean locked,
            Integer lockedBy,
            String lockerFirstName,
            String lockerLastName,
            StationUserType userType,
            MemberIdentity identity) {
        EnrichedCheckSummary(MemberCheckSummary s, MemberIdentity identity) {
            this(
                    s.memberId(),
                    s.firstName(),
                    s.lastName(),
                    s.lastCheckedAt(),
                    s.checkerFirstName(),
                    s.checkerLastName(),
                    s.locked(),
                    s.lockedBy(),
                    s.lockerFirstName(),
                    s.lockerLastName(),
                    s.userType(),
                    identity);
        }
    }

    public record CompleteCheckRequest(List<CheckItemResult> items) {}

    public record CompleteContainerCheckRequest(boolean deep, List<CheckItemResult> items) {}

    public record CheckItemResult(Integer itemId, Integer inventoryId, CheckResult result, String note) {}

    public record AssignItemRequest(int newItemId, Integer oldItemId) {}

    public record UnassignItemRequest(int itemId) {}

    public record CreateAndAssignRequest(int inventoryId, Integer sizeId, Integer oldItemId) {}

    public record NextMemberResponse(Integer memberId) {}
}
