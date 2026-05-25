/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryExportService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Routes for inventory management including CRUD operations on inventories, sizes, items,
 * requirements, member item assignments, and PDF export.
 */
@Singleton
public class InventoryRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(InventoryRoutes.class);
    private final InventoryService inventoryService;
    private final InventoryCheckService checkService;
    private final InventoryExportService inventoryExportService;

    @Inject
    public InventoryRoutes(
            InventoryService inventoryService,
            InventoryCheckService checkService,
            InventoryExportService inventoryExportService) {
        this.inventoryService = inventoryService;
        this.checkService = checkService;
        this.inventoryExportService = inventoryExportService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void verifyItemOwnership(int itemId, UserSession session) {
        var item = inventoryService.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        var inventory = inventoryService.findById(item.inventoryId()).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
    }

    // -- Inventories --

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/my-inventory-items", this::myItems, Roles.USER);
        routes.get(prefix + "/my-inventory-requirements", this::myRequirements, Roles.USER);
        routes.get(
                prefix + "/station-members/{memberId}/inventory-items",
                this::memberItems,
                Roles.MEMBER_MANAGER,
                Roles.INVENTORY_MANAGER);
        routes.get(prefix + "/inventories", this::list, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/inventories", this::create, Roles.INVENTORY_MANAGER);
        routes.get(prefix + "/inventories/all-items", this::listAllItems, Roles.INVENTORY_MANAGER);
        routes.get(prefix + "/inventories/all-sizes", this::listAllSizes, Roles.LOGIN);
        routes.get(prefix + "/inventories/{id}", this::get, Roles.INVENTORY_MANAGER);
        routes.put(prefix + "/inventories/{id}", this::update, Roles.INVENTORY_MANAGER);
        routes.delete(prefix + "/inventories/{id}", this::delete, Roles.INVENTORY_MANAGER);

        routes.get(prefix + "/inventories/{inventoryId}/sizes", this::listSizes, Roles.LOGIN);
        routes.post(prefix + "/inventories/{inventoryId}/sizes", this::createSize, Roles.INVENTORY_MANAGER);
        routes.put(prefix + "/inventories/{inventoryId}/sizes/{sizeId}", this::updateSize, Roles.INVENTORY_MANAGER);
        routes.delete(prefix + "/inventories/{inventoryId}/sizes/{sizeId}", this::deleteSize, Roles.INVENTORY_MANAGER);

        routes.get(prefix + "/inventories/{inventoryId}/items", this::listItems, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/inventories/{inventoryId}/items", this::createItem, Roles.INVENTORY_MANAGER);
        routes.get(prefix + "/inventory-items/{id}", this::getItem, Roles.INVENTORY_MANAGER);
        routes.put(prefix + "/inventory-items/{id}", this::updateItem, Roles.INVENTORY_MANAGER);
        routes.put(prefix + "/inventory-items/{id}/assign", this::assignItem, Roles.INVENTORY_MANAGER);
        routes.get(prefix + "/inventory-items/{id}/history", this::getHistory, Roles.INVENTORY_MANAGER);
        routes.put(prefix + "/inventory-items/{id}/lost", this::markLost, Roles.INVENTORY_MANAGER);
        routes.delete(prefix + "/inventory-items/{id}/lost", this::markFound, Roles.INVENTORY_MANAGER);
        routes.delete(prefix + "/inventory-items/{id}", this::deleteItem, Roles.INVENTORY_MANAGER);

        routes.get(prefix + "/inventory-requirements", this::listAllRequirements, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/inventory-requirements", this::createRequirement, Roles.INVENTORY_MANAGER);
        routes.put(prefix + "/inventory-requirements/{id}", this::updateRequirement, Roles.INVENTORY_MANAGER);
        routes.patch(
                prefix + "/inventory-requirements/{id}/position",
                this::updateRequirementPosition,
                Roles.INVENTORY_MANAGER);
        routes.delete(prefix + "/inventory-requirements/{id}", this::deleteRequirement, Roles.INVENTORY_MANAGER);

        routes.post(prefix + "/inventories/members/export", this::exportMembers, Roles.INVENTORY_MANAGER);
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
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var items = inventoryService.findItemsByMember(memberId);
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
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.create(
                        session.stationId(),
                        request.name(),
                        parseInventoryType(request.inventoryType()),
                        request.hasSizes()));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        inventoryService
                .findById(id)
                .ifPresentOrElse(
                        inventory -> {
                            var sizes = inventoryService.findSizes(id);
                            ctx.json(new InventoryDetail(
                                    inventory.id(),
                                    inventory.stationId(),
                                    inventory.name(),
                                    inventory.inventoryType().name(),
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var inventory = inventoryService.findById(id).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var request = ctx.bodyAsClass(InventoryRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        inventoryService
                .update(id, request.name(), parseInventoryType(request.inventoryType()), request.hasSizes())
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var inventory = inventoryService.findById(id).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
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
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        int sizeId = ctx.pathParamAsClass("sizeId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        int sizeId = ctx.pathParamAsClass("sizeId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
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
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var request = ctx.bodyAsClass(ItemRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        InventoryItem.ItemSource source = InventoryItem.ItemSource.INTERNAL;
        if (request.itemSource() != null) {
            source = InventoryItem.ItemSource.valueOf(request.itemSource());
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        verifyItemOwnership(id, UserSession.from(ctx));
        var request = ctx.bodyAsClass(AssignRequest.class);
        inventoryService
                .assignItem(id, request.memberId(), request.memberName())
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    private void getHistory(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(inventoryService.findHistory(id));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        verifyItemOwnership(id, UserSession.from(ctx));
        if (inventoryService.deleteItem(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Requirements --

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
        var request = ctx.bodyAsClass(RequirementRequest.class);
        if (request.inventoryId() == 0) {
            throw new BadRequestResponse("inventoryId is required");
        }
        int roleId = request.roleId() != null ? request.roleId() : 0;
        int groupId = request.groupId() != null ? request.groupId() : 0;
        if (roleId == 0 && groupId == 0) {
            throw new BadRequestResponse("roleId or groupId is required");
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createRequirement(
                        request.inventoryId(), roleId, groupId, request.quantity() > 0 ? request.quantity() : 1));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (inventoryService.deleteRequirement(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Request/Response records --

    private InventoryType parseInventoryType(String type) {
        try {
            return InventoryType.valueOf(type);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid inventory type: {}", type, e);
            throw new BadRequestResponse("Invalid inventory type: " + type);
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

    public record InventoryRequest(String name, String inventoryType, boolean hasSizes) {}

    public record InventoryDetail(
            int id, int stationId, String name, String inventoryType, boolean hasSizes, List<InventorySize> sizes) {}

    public record SizeRequest(String label, int position, String note) {}

    public record ItemRequest(String internalId, String name, Integer sizeId, String metadata, String itemSource) {}

    public record AssignRequest(Integer memberId, String memberName) {}

    public record RequirementRequest(int inventoryId, Integer roleId, Integer groupId, int quantity) {}

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
