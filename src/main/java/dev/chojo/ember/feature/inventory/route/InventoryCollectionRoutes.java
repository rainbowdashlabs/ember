/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.CollectionLine;
import dev.chojo.ember.feature.inventory.entity.InventoryCollection;
import dev.chojo.ember.feature.inventory.entity.ResolvedCollection;
import dev.chojo.ember.feature.inventory.repository.InventoryCollectionRepository;
import dev.chojo.ember.feature.inventory.service.InventoryCollectionService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Collections: the named sets a station keeps, and what they would find if fetched.
 *
 * <p>Reading one takes {@code INVENTORY_READ} and changing one takes {@code INVENTORY_EDIT}, because a
 * collection is a note about the station's own gear rather than a second kind of stock. No new
 * permission is minted for it.
 */
@Singleton
public class InventoryCollectionRoutes implements Routes {

    private final InventoryCollectionService collectionService;
    private final InventoryService inventoryService;

    @Inject
    public InventoryCollectionRoutes(InventoryCollectionService collectionService, InventoryService inventoryService) {
        this.collectionService = collectionService;
        this.inventoryService = inventoryService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/inventory-collections", this::list, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventory-collections", this::create, StationPermission.INVENTORY_EDIT);
        routes.get(prefix + "/inventory-collections/{id}", this::get, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventory-collections/{id}", this::update, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + "/inventory-collections/{id}", this::delete, StationPermission.INVENTORY_EDIT);
        routes.post(prefix + "/inventory-collections/{id}/lines", this::addLine, StationPermission.INVENTORY_EDIT);
        routes.put(
                prefix + "/inventory-collections/{id}/line-order",
                this::reorderLines,
                StationPermission.INVENTORY_EDIT);
        routes.put(
                prefix + "/inventory-collections/{id}/lines/{lineId}",
                this::updateLine,
                StationPermission.INVENTORY_EDIT);
        routes.delete(
                prefix + "/inventory-collections/{id}/lines/{lineId}",
                this::deleteLine,
                StationPermission.INVENTORY_EDIT);

        routes.get(
                prefix + "/inventory-items/{id}/collections",
                this::collectionsHoldingItem,
                StationPermission.INVENTORY_READ);
        routes.get(
                prefix + "/inventories/{id}/collections",
                this::collectionsTouchingInventory,
                StationPermission.INVENTORY_READ);
        routes.get(
                prefix + "/inventories/{id}/arts/{artId}/collections",
                this::collectionsAskingForArt,
                StationPermission.INVENTORY_READ);
    }

    private InventoryCollection ownCollection(Context ctx) {
        UserSession session = UserSession.from(ctx);
        InventoryCollection collection =
                collectionService.findById(pathInt(ctx, "id")).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, collection.stationId());
        return collection;
    }

    private CollectionLine ownLine(Context ctx) {
        InventoryCollection collection = ownCollection(ctx);
        int lineId = pathInt(ctx, "lineId");
        return collectionService.findLines(collection.id()).stream()
                .filter(candidate -> candidate.id() == lineId)
                .findFirst()
                .orElseThrow(NotFoundResponse::new);
    }

    @OpenApi(
            path = "/api/v1/inventory-collections",
            methods = HttpMethod.GET,
            summary = "List the collections of the current station",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CollectionResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(collectionService.findByStation(session.stationId()).stream()
                .map(CollectionResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/inventory-collections",
            methods = HttpMethod.POST,
            summary = "Create a collection",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CollectionRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryCollection.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(CollectionRequest.class);
        try {
            ctx.status(HttpStatus.CREATED)
                    .json(collectionService.create(
                            session.stationId(),
                            body.name(),
                            body.note(),
                            session.member().id()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-collections/{id}",
            methods = HttpMethod.GET,
            summary = "Read a collection against the stock",
            description =
                    "Answers per line what the station could put its hands on. Pass from and to to subtract what is already promised to a lending request overlapping that window; without them the answer is what is here today.",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = {
                @OpenApiParam(name = "from", type = String.class),
                @OpenApiParam(name = "to", type = String.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ResolvedCollection.class)))
    private void get(Context ctx) {
        InventoryCollection collection = ownCollection(ctx);
        LocalDate from = queryDate(ctx, "from");
        LocalDate to = queryDate(ctx, "to");
        try {
            ctx.json(collectionService.resolve(collection.id(), collection.stationId(), from, to));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-collections/{id}",
            methods = HttpMethod.PUT,
            summary = "Rename a collection",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CollectionRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        InventoryCollection collection = ownCollection(ctx);
        var body = ctx.bodyAsClass(CollectionRequest.class);
        try {
            collectionService.update(collection.id(), body.name(), body.note());
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-collections/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a collection",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        InventoryCollection collection = ownCollection(ctx);
        collectionService.delete(collection.id(), collection.stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/inventory-collections/{id}/lines",
            methods = HttpMethod.POST,
            summary = "Add a line to a collection",
            description =
                    "Name a piece with itemId, ask for a count of one kind with artId and quantity, or for a count out of a whole inventory with inventoryId and quantity. Exactly one of the three.",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LineRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = CollectionLine.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void addLine(Context ctx) {
        InventoryCollection collection = ownCollection(ctx);
        var body = ctx.bodyAsClass(LineRequest.class);
        if (Stream.of(body.itemId(), body.artId(), body.inventoryId())
                        .filter(Objects::nonNull)
                        .count()
                != 1) {
            throw new BadRequestResponse("A line names a piece, a kind or an inventory, and exactly one of them");
        }
        try {
            ctx.status(HttpStatus.CREATED).json(addLineOf(collection, body));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-collections/{id}/lines/{lineId}",
            methods = HttpMethod.PUT,
            summary = "Change how many pieces a counted line asks for",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "lineId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuantityRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateLine(Context ctx) {
        CollectionLine line = ownLine(ctx);
        var body = ctx.bodyAsClass(QuantityRequest.class);
        try {
            collectionService.updateLineQuantity(line.id(), body.quantity());
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-collections/{id}/line-order",
            methods = HttpMethod.PUT,
            summary = "Reorder the lines of a collection",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LineOrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorderLines(Context ctx) {
        InventoryCollection collection = ownCollection(ctx);
        var body = ctx.bodyAsClass(LineOrderRequest.class);
        collectionService.reorderLines(collection.id(), body.lineIds());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/inventory-collections/{id}/lines/{lineId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a line from a collection",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "lineId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteLine(Context ctx) {
        CollectionLine line = ownLine(ctx);
        collectionService.deleteLine(line.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/collections",
            methods = HttpMethod.GET,
            summary = "The collections that would lose a line if this piece went",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void collectionsHoldingItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int itemId = pathInt(ctx, "id");
        var item = inventoryService.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        var inventory = inventoryService.findById(item.inventoryId()).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, inventory.stationId());
        ctx.json(names(collectionService.collectionsHoldingItem(itemId)));
    }

    @OpenApi(
            path = "/api/v1/inventories/{id}/collections",
            methods = HttpMethod.GET,
            summary = "The collections that would lose a line if this inventory went",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void collectionsTouchingInventory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "id");
        var inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, inventory.stationId());
        ctx.json(names(collectionService.collectionsTouchingInventory(inventoryId)));
    }

    @OpenApi(
            path = "/api/v1/inventories/{id}/arts/{artId}/collections",
            methods = HttpMethod.GET,
            summary = "The collections that would lose a line if this kind of thing went",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "artId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void collectionsAskingForArt(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var inventory = inventoryService.findById(pathInt(ctx, "id")).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, inventory.stationId());
        ctx.json(names(collectionService.collectionsAskingForArt(pathInt(ctx, "artId"))));
    }

    private static List<String> names(List<InventoryCollection> collections) {
        return collections.stream().map(InventoryCollection::name).toList();
    }

    private static LocalDate queryDate(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BadRequestResponse("%s is not a date".formatted(name));
        }
    }

    private CollectionLine addLineOf(InventoryCollection collection, LineRequest body) {
        if (body.itemId() != null) {
            return collectionService.addItemLine(collection.id(), collection.stationId(), body.itemId());
        }
        if (body.artId() != null) {
            return collectionService.addArtLine(
                    collection.id(), collection.stationId(), body.artId(), body.quantity());
        }
        return collectionService.addInventoryLine(
                collection.id(), collection.stationId(), body.inventoryId(), body.quantity());
    }

    /**
     * A collection as the list screen reads it.
     *
     * @param lineCount how many lines it carries
     */
    public record CollectionResponse(int id, String name, String note, int lineCount) {
        static CollectionResponse of(InventoryCollectionRepository.CollectionSummary summary) {
            return new CollectionResponse(
                    summary.collection().id(),
                    summary.collection().name(),
                    summary.collection().note(),
                    summary.lineCount());
        }
    }

    /**
     * The name and note of a collection.
     */
    public record CollectionRequest(String name, String note) {}

    /**
     * A line to add: a named piece, a count of one kind, or a count out of a whole inventory.
     *
     * @param itemId      the named piece, or null
     * @param artId       the kind of thing counted, or null
     * @param inventoryId the inventory drawn from, or null
     * @param quantity    how many pieces a counted line asks for
     */
    public record LineRequest(Integer itemId, Integer artId, Integer inventoryId, int quantity) {}

    /**
     * A new count for a line.
     */
    public record QuantityRequest(int quantity) {}

    /**
     * The line IDs of a collection in their new order.
     */
    public record LineOrderRequest(List<Integer> lineIds) {}
}
