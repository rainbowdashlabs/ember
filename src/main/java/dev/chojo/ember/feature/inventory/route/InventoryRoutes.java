/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.cluster.entity.LossReportRequirement;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryIntakeRow;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MemberInventoryEntry;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryExportService;
import dev.chojo.ember.feature.inventory.service.InventoryIntakeService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.LossReportService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
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

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for inventory management including CRUD operations on inventories, sizes, items,
 * requirements, member item assignments, and PDF export.
 */
@Singleton
public class InventoryRoutes implements Routes {
    private final InventoryService inventoryService;
    private final InventoryCheckService checkService;
    private final InventoryExportService inventoryExportService;
    private final InventoryContainerService containerService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final LossReportService lossReportService;
    private final InventoryIntakeService intakeService;

    @Inject
    public InventoryRoutes(
            InventoryService inventoryService,
            InventoryCheckService checkService,
            InventoryExportService inventoryExportService,
            InventoryContainerService containerService,
            MemberIdentityFactory memberIdentityFactory,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            LossReportService lossReportService,
            InventoryIntakeService intakeService) {
        this.intakeService = intakeService;
        this.inventoryService = inventoryService;
        this.checkService = checkService;
        this.inventoryExportService = inventoryExportService;
        this.containerService = containerService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.lossReportService = lossReportService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/my-inventory-items", this::myItems, StationPermission.USER);
        routes.get(prefix + "/my-inventory-requirements", this::myRequirements, StationPermission.USER);
        routes.get(
                prefix + "/station-members/{memberId}/inventory-items",
                this::memberItems,
                StationPermission.MEMBER_READ,
                StationPermission.INVENTORY_READ);
        routes.get(
                prefix + "/station-members/{memberId}/inventory-requirements",
                this::memberRequirements,
                StationPermission.MEMBER_READ,
                StationPermission.INVENTORY_READ);
        routes.post(
                prefix + "/station-members/{memberId}/inventory-items",
                this::createAndHandOut,
                StationPermission.INVENTORY_CREATE_EXTERNAL,
                StationPermission.INVENTORY_CREATE_INTERNAL);
        // Inventory CRUD - read vs write
        routes.get(prefix + "/inventories", this::list, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventories", this::create, StationPermission.INVENTORY_CREATE);
        routes.get(prefix + "/inventories/all-items", this::listAllItems, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventories/all-sizes", this::listAllSizes, StationPermission.LOGIN);
        routes.get(prefix + "/inventories/summary", this::listSummaries, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventories/{id}", this::get, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventories/{id}", this::update, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + "/inventories/{id}", this::delete, StationPermission.INVENTORY_MANAGER);

        routes.get(prefix + "/inventories/{inventoryId}/sizes", this::listSizes, StationPermission.LOGIN);
        routes.post(prefix + "/inventories/{inventoryId}/sizes", this::createSize, StationPermission.INVENTORY_CREATE);
        routes.put(
                prefix + "/inventories/{inventoryId}/sizes/{sizeId}",
                this::updateSize,
                StationPermission.INVENTORY_EDIT);
        routes.delete(
                prefix + "/inventories/{inventoryId}/sizes/{sizeId}",
                this::deleteSize,
                StationPermission.INVENTORY_EDIT);
        // Items - read needs INVENTORY_READ, edit needs INVENTORY_EDIT, create needs INVENTORY_CREATE
        routes.get(prefix + "/inventories/{inventoryId}/items", this::listItems, StationPermission.INVENTORY_READ);
        routes.post(
                prefix + "/inventories/{inventoryId}/items",
                this::createItem,
                StationPermission.INVENTORY_CREATE_EXTERNAL,
                StationPermission.INVENTORY_CREATE_INTERNAL);
        routes.post(
                prefix + "/inventories/{inventoryId}/items/batch",
                this::takeStock,
                StationPermission.INVENTORY_CREATE_EXTERNAL,
                StationPermission.INVENTORY_CREATE_INTERNAL);
        routes.get(
                prefix + "/inventory-items/by-internal-id", this::findByInternalId, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-items/{id}", this::getItem, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventory-items/{id}", this::updateItem, StationPermission.INVENTORY_EDIT);
        routes.put(
                prefix + "/inventory-items/{id}/assign",
                this::assignItem,
                StationPermission.INVENTORY_EDIT,
                StationPermission.INVENTORY_ASSIGN);
        routes.get(prefix + "/inventory-items/{id}/location", this::getItemLocation, StationPermission.INVENTORY_READ);
        routes.put(
                prefix + "/inventory-items/{id}/container",
                this::setItemContainer,
                StationPermission.INVENTORY_STORAGE);
        routes.get(prefix + "/inventory-items/{id}/history", this::getHistory, StationPermission.INVENTORY_READ);
        // Marking gear lost is self-service: whoever holds it may say so, and INVENTORY_EDIT reaches any of it
        routes.put(prefix + "/inventory-items/{id}/lost", this::markLost, StationPermission.USER);
        routes.delete(prefix + "/inventory-items/{id}/lost", this::markFound, StationPermission.INVENTORY_EDIT);
        // Declaring somebody else's gear gone is heavier than asking for a different size, so it is not
        // the exchange right that reaches it
        routes.get(
                prefix + "/inventory-items/{id}/loss-report",
                this::lossReportTerms,
                StationPermission.INVENTORY_MANAGER);
        routes.post(
                prefix + "/inventory-items/{id}/loss-report", this::reportLoss, StationPermission.INVENTORY_MANAGER);
        routes.delete(prefix + "/inventory-items/{id}", this::deleteItem, StationPermission.INVENTORY_EDIT);

        routes.get(prefix + "/inventory-requirements", this::listAllRequirements, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-owner-above", this::ownerAbove, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventory-requirements", this::createRequirement, StationPermission.INVENTORY_MANAGER);
        routes.put(
                prefix + "/inventory-requirements/{id}", this::updateRequirement, StationPermission.INVENTORY_MANAGER);
        routes.patch(
                prefix + "/inventory-requirements/{id}/position",
                this::updateRequirementPosition,
                StationPermission.INVENTORY_MANAGER);
        routes.delete(
                prefix + "/inventory-requirements/{id}", this::deleteRequirement, StationPermission.INVENTORY_MANAGER);

        routes.post(prefix + "/inventories/members/export", this::exportMembers, StationPermission.INVENTORY_READ);

        // A member has to know whether a note is expected before they are refused for leaving it out
        routes.get(prefix + "/inventory-settings", this::getInventorySettings, StationPermission.USER);
        routes.put(prefix + "/inventory-settings", this::updateInventorySettings, StationPermission.INVENTORY_MANAGER);
    }

    // -- Inventories --

    /**
     * The body this caller answers for when they change how a piece of gear is described.
     *
     * <p>An association's own gear sits on the station it owns, and its screens act there, so its requests
     * arrive as ordinary station requests. What tells them apart from a station holding somebody else's
     * jacket is the association they name and the right they hold at it.
     *
     * @param session who is asking
     * @return the cluster they act for, or {@code null} when they are acting as the station alone
     */
    private Integer describingClusterId(UserSession session) {
        if (session.clusterId() == null) return null;
        if (!session.hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_EDIT)) return null;
        return session.clusterId();
    }

    private void verifyItemOwnership(int itemId, UserSession session) {
        var item = inventoryService.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        var inventory = inventoryService.findById(item.inventoryId()).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, inventory.stationId());
    }

    /**
     * Loads an inventory and asserts it belongs to the caller's station, returning it. Answers
     * 404 both when absent and when owned by another station.
     */
    /** Whose gear somebody may write down: their own station's, the association's, or both. */
    private void requireMayCreate(UserSession session, ItemOwner owner) {
        StationPermission required = owner == ItemOwner.CLUSTER
                ? StationPermission.INVENTORY_CREATE_EXTERNAL
                : StationPermission.INVENTORY_CREATE_INTERNAL;
        if (!session.hasPermission(required)) {
            throw new ForbiddenResponse("Missing permission " + required.name() + " to create items owned by "
                    + owner.name().toLowerCase());
        }
    }

    private Inventory requireOwnedInventory(int inventoryId, UserSession session) {
        var inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, inventory.stationId());
        return inventory;
    }

    /**
     * Asserts the given requirement belongs to an inventory of the caller's station.
     */
    private void verifyRequirementOwnership(int requirementId, UserSession session) {
        if (inventoryService.findAllRequirementsByStation(session.stationId()).stream()
                .noneMatch(r -> r.id() == requirementId)) {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories",
            methods = HttpMethod.GET,
            summary = "List inventories for the current station",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Inventory[].class)))
    private void myItems(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(inventoryService.findMemberEntries(session.member().id()).stream()
                .map(this::toMyItem)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/my-inventory-requirements",
            methods = HttpMethod.GET,
            summary = "List inventory requirements for the current member",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MyRequirement[].class)))
    private void myRequirements(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var required = checkService.getRequiredItems(
                session.stationId(), session.member().id());
        var result = required.stream()
                .map(r -> new MyRequirement(r.inventoryId(), r.inventoryName(), r.requiredQuantity()))
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/inventory-items",
            methods = HttpMethod.GET,
            summary = "List inventory items for a specific member",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MyInventoryItem[].class)))
    private void memberItems(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        ctx.json(inventoryService.findMemberEntries(memberId).stream()
                .filter(entry -> inventoryService
                        .findById(entry.item().inventoryId())
                        .map(inv -> inv.stationId() == session.stationId())
                        .orElse(false))
                .map(this::toMyItem)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/inventory-requirements",
            methods = HttpMethod.GET,
            summary = "What a member is required to hold, and what is still missing",
            description =
                    "The same requirements the stock-taking works from, read without taking the member's record for a check. Carries the pieces of each inventory that are in nobody's hands, so one of them can be handed over on the spot.",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberRequirements.class)))
    private void memberRequirements(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        requireMemberOfStation(memberId, session);
        var required = checkService.getRequiredItems(session.stationId(), memberId);
        Map<Integer, List<InventoryItem>> unassigned = new LinkedHashMap<>();
        for (var requirement : required) {
            unassigned.put(requirement.inventoryId(), inventoryService.unassignedItems(requirement.inventoryId()));
        }
        ctx.json(new MemberRequirements(required, unassigned));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/inventory-items",
            methods = HttpMethod.POST,
            summary = "Take a new piece into stock and hand it to a member",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = HandOutRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryItem.class)))
    private void createAndHandOut(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        requireMemberOfStation(memberId, session);
        var request = ctx.bodyAsClass(HandOutRequest.class);
        var inventory = inventoryService
                .findById(request.inventoryId())
                .orElseThrow(() -> new NotFoundResponse("Inventory not found"));
        RouteSupport.requireSameStation(session, inventory.stationId());
        String actor = session.account().firstName() + " " + session.account().lastName();
        var item = inventoryService.createAndHandOut(request.inventoryId(), request.sizeId(), memberId, actor);
        ctx.status(HttpStatus.CREATED).json(item);
    }

    private void requireMemberOfStation(int memberId, UserSession session) {
        var member = stationMemberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, member.stationId());
    }

    /**
     * What a member is expected to hold, and what could be handed to them right now.
     *
     * @param required   one entry per inventory the member is required to hold something from
     * @param unassigned the pieces of each of those inventories that are in nobody's hands
     */
    public record MemberRequirements(
            List<RequiredInventoryItem> required, Map<Integer, List<InventoryItem>> unassigned) {}

    /**
     * A piece to be made and handed over in one step.
     *
     * @param inventoryId the inventory it belongs to
     * @param sizeId      the size, or {@code null} where the inventory keeps none
     */
    public record HandOutRequest(int inventoryId, Integer sizeId) {}

    /**
     * Renders one line of a member's own inventory, carrying the step of whatever movement the item
     * is on so the member can watch an exchange happen rather than watch their jacket vanish.
     */
    private MyInventoryItem toMyItem(MemberInventoryEntry entry) {
        var item = entry.item();
        String inventoryName = inventoryService
                .findById(item.inventoryId())
                .map(Inventory::name)
                .orElse("");
        String sizeName = null;
        if (item.sizeId() != null) {
            sizeName = inventoryService.findSizes(item.inventoryId()).stream()
                    .filter(s -> s.id() == item.sizeId())
                    .map(InventorySize::label)
                    .findFirst()
                    .orElse(null);
        }
        return new MyInventoryItem(
                item.id(),
                item.inventoryId(),
                item.name(),
                item.internalId(),
                inventoryName,
                item.sizeId(),
                sizeName,
                item.lostAt(),
                item.custody(),
                entry.movementId(),
                entry.movementStep(),
                item.ownerKind(),
                item.ownerClusterId(),
                item.lostNote(),
                noteAuthor(item.lostNoteBy()));
    }

    /**
     * Who wrote the note about a loss, as an identity rather than a name.
     *
     * <p>It matters who it was: a guardian may report a loss for the person they act for, and the note then
     * says so rather than reading as if the member wrote it themselves.
     */
    private MemberIdentity noteAuthor(Integer memberId) {
        return memberId == null ? null : memberIdentityFactory.fromMemberId(memberId);
    }

    @OpenApi(
            path = "/api/v1/inventories",
            methods = HttpMethod.GET,
            summary = "List inventories for the current station",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Inventory[].class)))
    private void listSummaries(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(inventoryService.findSummaries(session.stationId()));
    }

    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(inventoryService.findByStation(session.stationId()));
    }

    private void listAllItems(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(inventoryService.findAllItemsByStation(session.stationId()));
    }

    private void listAllSizes(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(inventoryService.findAllSizesByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventories",
            methods = HttpMethod.POST,
            summary = "Create an inventory",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InventoryRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = Inventory.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(InventoryRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        if (request.inventoryType() == null) {
            throw new BadRequestResponse("inventoryType is required");
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.create(
                        session.stationId(), request.name(), request.inventoryType(), request.hasSizes()));
    }

    @OpenApi(
            path = "/api/v1/inventories/{id}",
            methods = HttpMethod.GET,
            summary = "Get an inventory with its sizes",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedInventory(id, session);
        inventoryService
                .findById(id)
                .ifPresentOrElse(
                        inventory -> {
                            var sizes = inventoryService.findSizes(id);
                            ctx.json(new InventoryDetail(
                                    inventory.id(),
                                    inventory.stationId(),
                                    inventory.name(),
                                    inventory.inventoryType(),
                                    inventory.hasSizes(),
                                    sizes));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/inventories/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InventoryRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Inventory.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwnedInventory(id, session);
        var request = ctx.bodyAsClass(InventoryRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        if (request.inventoryType() == null) {
            throw new BadRequestResponse("inventoryType is required");
        }
        inventoryService
                .update(id, request.name(), request.inventoryType(), request.hasSizes())
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    // -- Sizes --

    @OpenApi(
            path = "/api/v1/inventories/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwnedInventory(id, session);
        if (inventoryService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes",
            methods = HttpMethod.GET,
            summary = "List sizes of an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySize[].class)))
    private void listSizes(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        requireOwnedInventory(inventoryId, session);
        ctx.json(inventoryService.findSizes(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes",
            methods = HttpMethod.POST,
            summary = "Create an inventory size",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SizeRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventorySize[].class)))
    private void createSize(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        UserSession session = UserSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(SizeRequest.class);
        if (isBlank(request.label())) {
            throw new BadRequestResponse("label is required");
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createSize(inventoryId, request.label(), request.position(), request.note()));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes/{sizeId}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory size",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "sizeId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SizeRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySize[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateSize(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int sizeId = pathInt(ctx, "sizeId");
        UserSession session = UserSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(SizeRequest.class);
        if (isBlank(request.label())) {
            throw new BadRequestResponse("label is required");
        }
        inventoryService
                .updateSize(inventoryId, sizeId, request.label(), request.position(), request.note())
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    // -- Items --

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes/{sizeId}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory size",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "sizeId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySize[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteSize(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int sizeId = pathInt(ctx, "sizeId");
        UserSession session = UserSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        inventoryService.deleteSize(inventoryId, sizeId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/items",
            methods = HttpMethod.GET,
            summary = "List items in an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem[].class)))
    private void listItems(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        requireOwnedInventory(inventoryId, session);
        ctx.json(inventoryService.findStock(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/items",
            methods = HttpMethod.POST,
            summary = "Create an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ItemRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryItem.class)))
    private void createItem(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        UserSession session = UserSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(ItemRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        ItemOwner owner = request.ownerKind() != null ? request.ownerKind() : ItemOwner.STATION;
        requireMayCreate(session, owner);
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createItem(
                        inventoryId,
                        request.internalId(),
                        request.name(),
                        request.sizeId(),
                        request.metadata(),
                        owner,
                        request.ownerClusterId()));
    }

    /**
     * Writes down an inventory the station already owns, one line per piece, and hands each piece to
     * the member on its line.
     */
    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/items/batch",
            methods = HttpMethod.POST,
            summary = "Write down several pieces at once and assign them",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = IntakeRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryItem[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void takeStock(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        UserSession session = UserSession.from(ctx);
        var inventory = requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(IntakeRequest.class);
        var rows = request.rows() != null ? request.rows() : List.<InventoryIntakeRow>of();
        for (InventoryIntakeRow row : rows) {
            requireMayCreate(session, row.ownerKind() != null ? row.ownerKind() : ItemOwner.STATION);
        }
        if (!session.hasPermission(StationPermission.INVENTORY_ASSIGN)
                && !session.hasPermission(StationPermission.INVENTORY_EDIT)
                && rows.stream().anyMatch(row -> row.memberId() != null)) {
            throw new ForbiddenResponse("Missing permission to hand a piece to a member");
        }
        ctx.status(HttpStatus.CREATED)
                .json(intakeService.takeStock(inventoryId, session.stationId(), inventory.name(), rows));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/by-internal-id",
            methods = HttpMethod.GET,
            summary = "Find an inventory item by internal ID",
            tags = {"Inventory"},
            queryParams = @OpenApiParam(name = "internalId", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void findByInternalId(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String internalId = ctx.queryParam("internalId");
        if (internalId == null || internalId.isBlank()) {
            throw new BadRequestResponse("internalId is required");
        }
        inventoryService.findByInternalId(session.stationId(), internalId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}",
            methods = HttpMethod.GET,
            summary = "Get an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        inventoryService.findItemById(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ItemRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        var request = ctx.bodyAsClass(ItemRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        inventoryService
                .updateItem(
                        id,
                        request.internalId(),
                        request.name(),
                        request.sizeId(),
                        request.metadata(),
                        describingClusterId(session))
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/assign",
            methods = HttpMethod.PUT,
            summary = "Assign or unassign an inventory item to a member",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void assignItem(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, UserSession.from(ctx));
        var request = ctx.bodyAsClass(AssignRequest.class);
        inventoryService
                .assignItem(id, request.memberId(), request.memberName())
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/location",
            methods = HttpMethod.GET,
            summary = "Get an item's container path",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ItemLocationResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getItemLocation(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, UserSession.from(ctx));
        InventoryItem item = inventoryService.findItemById(id).orElseThrow(NotFoundResponse::new);
        ContainerPath path = containerService.pathOfItem(item);
        ctx.json(new ItemLocationResponse(item.id(), item.containerId(), path.segments(), path.ids(), path.display()));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/container",
            methods = HttpMethod.PUT,
            summary = "Place an item into a container, or clear its container",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ContainerAssignRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setItemContainer(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, UserSession.from(ctx));
        var body = ctx.bodyAsClass(ContainerAssignRequest.class);
        try {
            if (containerService.setItemContainer(id, body.containerId())) {
                ctx.status(HttpStatus.NO_CONTENT);
            } else {
                throw new NotFoundResponse();
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void getHistory(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        ctx.json(inventoryService.findHistory(id).stream()
                .map(h -> new HistoryResponse(
                        h.id(),
                        h.itemId(),
                        h.memberId(),
                        h.memberName(),
                        h.memberId() != null ? memberIdentityFactory.local(session.stationId(), h.memberId()) : null,
                        h.givenOut(),
                        h.returned()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/lost",
            methods = HttpMethod.PUT,
            summary = "Mark an inventory item as lost",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LostRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void markLost(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        var item = inventoryService.findItemById(id).orElseThrow(NotFoundResponse::new);
        String note =
                ctx.body().isBlank() ? null : ctx.bodyAsClass(LostRequest.class).note();
        note = isBlank(note) ? null : note.trim();

        // Whoever looks after the station's gear reaches all of it. Everybody else reaches what they hold.
        if (!session.hasPermission(StationPermission.INVENTORY_EDIT)) {
            requireHolds(session, item);
            if (note == null && lossNoteRequired(session.stationId())) {
                throw new BadRequestResponse("This station asks for a note when gear goes missing");
            }
        }
        Integer noteBy = note == null || session.member() == null
                ? null
                : session.member().id();
        inventoryService.markLost(id, note, noteBy).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    /**
     * Refuses somebody reporting a loss of gear that is not theirs to report.
     *
     * <p>Nothing is granted here and nothing is configured: an item assigned to you is yours to say you
     * cannot find, and a guardian says it for the person they act for, the way they do everything else in
     * that person's profile. Anything wider needs {@code INVENTORY_EDIT}, which is checked before this.
     */
    private void requireHolds(UserSession session, InventoryItem item) {
        if (item.assignedTo() == null || session.member() == null) {
            throw new ForbiddenResponse("Only somebody holding this gear can report it missing");
        }
        int holder = item.assignedTo();
        if (holder == session.member().id()) return;
        boolean actsForThem = session.hasPermission(StationPermission.MEMBER_GUARDIAN)
                && stationMemberRepository.findManagers(holder).stream()
                        .anyMatch(m -> m.id() == session.member().id());
        if (!actsForThem) {
            throw new ForbiddenResponse("Only somebody holding this gear can report it missing");
        }
    }

    private boolean lossNoteRequired(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::lossNoteRequired)
                .orElse(false);
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/loss-report",
            methods = HttpMethod.GET,
            summary = "What the body that owns this gear asks for with a loss report",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LossReportTerms.class)))
    private void lossReportTerms(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        var requires = lossReportService.requirementFor(id);
        ctx.json(new LossReportTerms(requires.isPresent(), requires.orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/loss-report",
            methods = HttpMethod.POST,
            summary = "Report a missing item to the body that owns it",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "201"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reportLoss(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);

        // Multipart, because the owner may demand a document and a report short of one is refused outright.
        // Writing the report first and attaching afterwards would leave half a request standing.
        String note = ctx.formParam("note");
        var file = ctx.uploadedFile("document");
        LossReportService.Attachment attachment = null;
        if (file != null) {
            try (var content = file.content()) {
                attachment = new LossReportService.Attachment(
                        file.filename(),
                        file.contentType() != null ? file.contentType() : "application/octet-stream",
                        content.readAllBytes());
            } catch (IOException e) {
                throw new BadRequestResponse("That file could not be read");
            }
        }
        var movement = lossReportService.report(
                session.stationId(), id, note, attachment, session.member().id());
        ctx.status(HttpStatus.CREATED).json(movement);
    }

    @OpenApi(
            path = "/api/v1/inventory-settings",
            methods = HttpMethod.GET,
            summary = "The station's inventory settings",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySettings.class)))
    private void getInventorySettings(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(new InventorySettings(lossNoteRequired(session.stationId())));
    }

    @OpenApi(
            path = "/api/v1/inventory-settings",
            methods = HttpMethod.PUT,
            summary = "Change the station's inventory settings",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InventorySettings.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySettings.class)))
    private void updateInventorySettings(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(InventorySettings.class);
        stationRepository.updateLossNoteRequired(session.stationId(), request.lossNoteRequired());
        ctx.json(new InventorySettings(lossNoteRequired(session.stationId())));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/lost",
            methods = HttpMethod.DELETE,
            summary = "Mark an inventory item as found",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void markFound(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, UserSession.from(ctx));
        inventoryService.markFound(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        if (inventoryService.deleteItem(id, describingClusterId(session))) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements",
            methods = HttpMethod.GET,
            summary = "List all inventory requirements for the current station",
            tags = {"Inventory"},
            description = "The station's own and those of the cluster above it, the latter named and read-only.",
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequirementResponse[].class)))
    private void listAllRequirements(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String clusterName = inventoryService.ownerAbove(session.stationId()).orElse(null);
        ctx.json(inventoryService.findRequirementsVisibleAt(session.stationId()).stream()
                .map(visible -> new RequirementResponse(
                        visible.requirement().id(),
                        visible.requirement().inventoryId(),
                        visible.inventoryName(),
                        visible.requirement().userType(),
                        visible.requirement().groupId(),
                        visible.requirement().stationGroupId(),
                        visible.requirement().quantity(),
                        visible.requirement().position(),
                        visible.fromCluster() ? clusterName : null))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/inventory-owner-above",
            methods = HttpMethod.GET,
            summary = "The body above this station that keeps its gear here",
            description = "Answers with a name when the station belongs to an association that keeps its gear in "
                    + "Ember, and with nothing when it does not. What a station may ask for follows from it.",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OwnerAboveResponse.class)))
    private void ownerAbove(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(new OwnerAboveResponse(
                inventoryService.ownerAbove(session.stationId()).orElse(null)));
    }

    // -- Requirements --

    @OpenApi(
            path = "/api/v1/inventory-requirements",
            methods = HttpMethod.POST,
            summary = "Create an inventory requirement",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RequirementRequest.class)),
            responses = {
                @OpenApiResponse(status = "201"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createRequirement(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(RequirementRequest.class);
        if (request.inventoryId() == 0) {
            throw new BadRequestResponse("inventoryId is required");
        }
        requireOwnedInventory(request.inventoryId(), session);
        StationUserType userType = request.userType();
        int groupId = request.groupId() != null ? request.groupId() : 0;
        if (userType == null && groupId == 0) {
            throw new BadRequestResponse("userType or groupId is required");
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createRequirement(
                        request.inventoryId(),
                        userType,
                        groupId,
                        request.stationGroupId(),
                        request.quantity() > 0 ? request.quantity() : 1));
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory requirement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateRequirementRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateRequirement(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyRequirementOwnership(id, session);
        var request = ctx.bodyAsClass(UpdateRequirementRequest.class);
        if (inventoryService.updateRequirement(id, request.quantity() > 0 ? request.quantity() : 1)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements/{id}/position",
            methods = HttpMethod.PATCH,
            summary = "Update the position of an inventory requirement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdatePositionRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateRequirementPosition(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyRequirementOwnership(id, session);
        var request = ctx.bodyAsClass(UpdatePositionRequest.class);
        if (inventoryService.updateRequirementPosition(id, request.position())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory requirement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteRequirement(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyRequirementOwnership(id, session);
        if (inventoryService.deleteRequirement(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/members/export",
            methods = HttpMethod.POST,
            summary = "Export member inventory list as PDF",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberExportRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
            })
    private void exportMembers(Context ctx) {
        var session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(MemberExportRequest.class);
        var account = session.account();
        String generatedBy = (account.firstName() + " " + account.lastName()).trim();
        var pdf = inventoryExportService.exportPdf(
                session.stationId(),
                body.memberIds(),
                body.inventoryIds(),
                body.extraFieldIds() != null ? body.extraFieldIds() : List.of(),
                generatedBy,
                body.showName() != null ? body.showName() : true,
                body.showInternalId() != null ? body.showInternalId() : false,
                body.showSize() != null ? body.showSize() : true);
        if (pdf.isPresent()) {
            ctx.contentType("application/pdf");
            ctx.result(pdf.get());
        } else {
            throw new BadRequestResponse("Export failed");
        }
    }

    // -- Request/Response records --

    public record HistoryResponse(
            int id,
            int itemId,
            Integer memberId,
            String memberName,
            MemberIdentity memberIdentity,
            Instant givenOut,
            Instant returned) {}

    public record MyInventoryItem(
            int id,
            int inventoryId,
            String name,
            String internalId,
            String inventoryName,
            Integer sizeId,
            String sizeName,
            Instant lostAt,
            ItemCustody custody,
            Integer movementId,
            String movementStep,
            /** Who owns it, which a member is entitled to know about what they are looking after. */
            ItemOwner ownerKind,
            Integer ownerClusterId,
            /** What was written when it was reported missing, which the member wrote or had written for them. */
            String lostNote,
            MemberIdentity lostNoteBy) {}

    public record MyRequirement(int inventoryId, String inventoryName, int requiredQuantity) {}

    public record InventoryRequest(String name, InventoryType inventoryType, boolean hasSizes) {}

    public record InventoryDetail(
            int id,
            int stationId,
            String name,
            InventoryType inventoryType,
            boolean hasSizes,
            List<InventorySize> sizes) {}

    public record SizeRequest(String label, int position, String note) {}

    public record ItemRequest(
            String internalId,
            String name,
            Integer sizeId,
            InventoryItemMetadata metadata,
            ItemOwner ownerKind,
            Integer ownerClusterId) {}

    /**
     * @param rows the lines of a stock-taking, in the order they were shown. A line that names no
     *             piece is passed over, so a table opened with a row per member needs no tidying up
     *             before it is saved
     */
    public record IntakeRequest(List<InventoryIntakeRow> rows) {}

    public record AssignRequest(Integer memberId, String memberName) {}

    /** What was written when gear was reported missing. */
    public record LostRequest(String note) {}

    /** What a station has decided about its gear beyond any one inventory. */
    public record InventorySettings(boolean lossNoteRequired) {}

    /**
     * What the body that owns a piece of gear asks for before it will consider replacing it.
     *
     * @param reportable whether there is an owner here to report to at all
     * @param requires   nothing, a note, or a document as well
     */
    public record LossReportTerms(boolean reportable, LossReportRequirement requires) {}

    public record ContainerAssignRequest(Integer containerId) {}

    public record ItemLocationResponse(
            int itemId, Integer containerId, List<String> pathSegments, List<Integer> pathIds, String pathDisplay) {}

    /**
     * A requirement as a station reads it.
     *
     * @param clusterName the cluster that wrote it, or {@code null} for one the station wrote itself. A
     *                    station may read what the cluster asks of its people and change none of it, so the
     *                    name is both the badge and the reason the controls are gone.
     */
    public record RequirementResponse(
            int id,
            int inventoryId,
            String inventoryName,
            StationUserType userType,
            int groupId,
            Integer stationGroupId,
            int quantity,
            int position,
            String clusterName) {}

    /**
     * @param stationGroupId the group of stations it counts at, or null for every station reading it. Only
     *                       an association writing its own requirement may name one.
     */
    public record RequirementRequest(
            int inventoryId, StationUserType userType, Integer groupId, Integer stationGroupId, int quantity) {}

    public record UpdateRequirementRequest(int quantity) {}

    /**
     * @param name the association above the station, or null when there is none keeping gear here
     */
    public record OwnerAboveResponse(String name) {}

    public record UpdatePositionRequest(int position) {}

    public record MemberExportRequest(
            List<Integer> memberIds,
            List<Integer> inventoryIds,
            List<Integer> extraFieldIds,
            Boolean showName,
            Boolean showInternalId,
            Boolean showSize) {}
}
