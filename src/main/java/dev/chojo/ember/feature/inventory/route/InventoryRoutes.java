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
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryExportService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
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

import java.time.Instant;
import java.util.List;

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

    @Inject
    public InventoryRoutes(
            InventoryService inventoryService,
            InventoryCheckService checkService,
            InventoryExportService inventoryExportService,
            InventoryContainerService containerService,
            MemberIdentityFactory memberIdentityFactory) {
        this.inventoryService = inventoryService;
        this.checkService = checkService;
        this.inventoryExportService = inventoryExportService;
        this.containerService = containerService;
        this.memberIdentityFactory = memberIdentityFactory;
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
        routes.put(prefix + "/inventory-items/{id}/lost", this::markLost, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + "/inventory-items/{id}/lost", this::markFound, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + "/inventory-items/{id}", this::deleteItem, StationPermission.INVENTORY_EDIT);

        routes.get(prefix + "/inventory-requirements", this::listAllRequirements, StationPermission.INVENTORY_READ);
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
    }

    // -- Inventories --

    private void verifyItemOwnership(int itemId, UserSession session) {
        var item = inventoryService.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        var inventory = inventoryService.findById(item.inventoryId()).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, inventory.stationId());
    }

    /**
     * Loads an inventory and asserts it belongs to the caller's station, returning it. Answers
     * 404 both when absent and when owned by another station.
     */
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
        var items = inventoryService.findItemsByMember(session.member().id());
        var result = items.stream()
                .map(item -> {
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
                            item.lostAt());
                })
                .toList();
        ctx.json(result);
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
        var items = inventoryService.findItemsByMember(memberId).stream()
                .filter(item -> inventoryService
                        .findById(item.inventoryId())
                        .map(inv -> inv.stationId() == session.stationId())
                        .orElse(false))
                .toList();
        ctx.json(items.stream()
                .map(item -> {
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
                            item.lostAt());
                })
                .toList());
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
        ctx.json(inventoryService.findItems(inventoryId));
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
        InventoryItem.ItemSource source =
                request.itemSource() != null ? request.itemSource() : InventoryItem.ItemSource.INTERNAL;
        StationPermission required = source == InventoryItem.ItemSource.EXTERNAL
                ? StationPermission.INVENTORY_CREATE_EXTERNAL
                : StationPermission.INVENTORY_CREATE_INTERNAL;
        if (!session.hasPermission(required)) {
            throw new ForbiddenResponse("Missing permission " + required.name() + " to create "
                    + source.name().toLowerCase() + " items");
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createItem(
                        inventoryId,
                        request.internalId(),
                        request.name(),
                        request.sizeId(),
                        request.metadata(),
                        source));
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
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, UserSession.from(ctx));
        var request = ctx.bodyAsClass(ItemRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        inventoryService
                .updateItem(id, request.internalId(), request.name(), request.sizeId(), request.metadata())
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
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void markLost(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, UserSession.from(ctx));
        inventoryService.markLost(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
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
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, UserSession.from(ctx));
        if (inventoryService.deleteItem(id)) {
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
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryRequirement[].class)))
    private void listAllRequirements(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(inventoryService.findAllRequirementsByStation(session.stationId()));
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
                        request.inventoryId(), userType, groupId, request.quantity() > 0 ? request.quantity() : 1));
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
            Instant lostAt) {}

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
            InventoryItem.ItemSource itemSource) {}

    public record AssignRequest(Integer memberId, String memberName) {}

    public record ContainerAssignRequest(Integer containerId) {}

    public record ItemLocationResponse(
            int itemId, Integer containerId, List<String> pathSegments, List<Integer> pathIds, String pathDisplay) {}

    public record RequirementRequest(int inventoryId, StationUserType userType, Integer groupId, int quantity) {}

    public record UpdateRequirementRequest(int quantity) {}

    public record UpdatePositionRequest(int position) {}

    public record MemberExportRequest(
            List<Integer> memberIds,
            List<Integer> inventoryIds,
            List<Integer> extraFieldIds,
            Boolean showName,
            Boolean showInternalId,
            Boolean showSize) {}
}
