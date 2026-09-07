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
import dev.chojo.ember.feature.inventory.entity.ArtStock;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemNameCount;
import dev.chojo.ember.feature.inventory.service.InventoryArtService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
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

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for the kinds of thing an inventory holds, and for the tidying up that puts pieces under
 * them.
 *
 * <p>Reading a kind needs no more than reading the inventory. Making one needs the permission to
 * edit an inventory, deliberately not the one to record a piece: that one is inherited by everybody
 * running a stock check or an exchange, and a vocabulary anybody can extend on the way past is not a
 * vocabulary.
 */
@Singleton
public class InventoryArtRoutes implements Routes {

    private final InventoryArtService artService;
    private final InventoryService inventoryService;

    @Inject
    public InventoryArtRoutes(InventoryArtService artService, InventoryService inventoryService) {
        this.artService = artService;
        this.inventoryService = inventoryService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/inventories/{inventoryId}/arts", this::listArts, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventories/{inventoryId}/arts", this::createArt, StationPermission.INVENTORY_EDIT);
        routes.put(
                prefix + "/inventories/{inventoryId}/arts/{artId}", this::updateArt, StationPermission.INVENTORY_EDIT);
        routes.delete(
                prefix + "/inventories/{inventoryId}/arts/{artId}", this::deleteArt, StationPermission.INVENTORY_EDIT);
        routes.get(prefix + "/inventories/{inventoryId}/art-stock", this::artStock, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventories/{inventoryId}/item-names", this::itemNames, StationPermission.INVENTORY_READ);
        routes.get(
                prefix + "/inventories/{inventoryId}/art-items/{artId}",
                this::itemsOfArt,
                StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventories/{inventoryId}/item-arts", this::assignArt, StationPermission.INVENTORY_EDIT);
        routes.post(
                prefix + "/inventories/{inventoryId}/art-merges", this::mergeIntoArt, StationPermission.INVENTORY_EDIT);
    }

    private Inventory ownedInventory(int inventoryId, UserSession session) {
        Inventory inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, inventory.stationId());
        return inventory;
    }

    private void verifyArtInInventory(int inventoryId, int artId) {
        if (artService.findByInventory(inventoryId).stream().noneMatch(art -> art.id() == artId)) {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/arts",
            methods = HttpMethod.GET,
            summary = "List the kinds of thing an inventory holds",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryArt[].class)))
    private void listArts(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        ctx.json(artService.findByInventory(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/arts",
            methods = HttpMethod.POST,
            summary = "Write down a kind of thing",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ArtRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryArt.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createArt(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        var body = ctx.bodyAsClass(ArtRequest.class);
        ctx.status(HttpStatus.CREATED).json(artService.create(inventoryId, body.name(), body.note(), body.position()));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/arts/{artId}",
            methods = HttpMethod.PUT,
            summary = "Rename a kind of thing or move it in the list",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "artId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ArtRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryArt.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateArt(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int artId = pathInt(ctx, "artId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        verifyArtInInventory(inventoryId, artId);
        var body = ctx.bodyAsClass(ArtRequest.class);
        artService.update(artId, body.name(), body.note(), body.position()).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/arts/{artId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a kind of thing, leaving its pieces where they are",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "artId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteArt(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int artId = pathInt(ctx, "artId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        verifyArtInInventory(inventoryId, artId);
        if (artService.delete(artId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/art-stock",
            methods = HttpMethod.GET,
            summary = "How many pieces of each kind an inventory holds, and how many are free",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ArtStock[].class)))
    private void artStock(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        ctx.json(artService.stock(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/item-names",
            methods = HttpMethod.GET,
            summary = "The distinct names written on the pieces, with a count each",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ItemNameCount[].class)))
    private void itemNames(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        ctx.json(artService.nameCounts(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/art-items/{artId}",
            methods = HttpMethod.GET,
            summary = "The pieces of one kind",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "artId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem[].class)))
    private void itemsOfArt(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int artId = pathInt(ctx, "artId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        verifyArtInInventory(inventoryId, artId);
        ctx.json(artService.findItems(artId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/item-arts",
            methods = HttpMethod.PUT,
            summary = "Put pieces under a kind, leaving their names as they are",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ArtAssignRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TidyResult.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void assignArt(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        var body = ctx.bodyAsClass(ArtAssignRequest.class);
        ctx.json(new TidyResult(artService.assign(inventoryId, body.artId(), itemIds(body.itemIds()))));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/art-merges",
            methods = HttpMethod.POST,
            summary = "Put pieces under a kind and rewrite their names to it",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ArtMergeRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TidyResult.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void mergeIntoArt(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        ownedInventory(inventoryId, UserSession.from(ctx));
        var body = ctx.bodyAsClass(ArtMergeRequest.class);
        ctx.json(new TidyResult(artService.merge(inventoryId, body.artId(), itemIds(body.itemIds()))));
    }

    private static List<Integer> itemIds(List<Integer> ids) {
        return ids == null ? List.of() : ids;
    }

    /**
     * Request body for writing down or renaming a kind.
     *
     * @param name     what the station calls it
     * @param note     a free note, may be empty
     * @param position the sort position among the kinds of the same inventory
     */
    public record ArtRequest(String name, String note, int position) {}

    /**
     * Request body for putting pieces under a kind without touching their names.
     *
     * @param artId   the kind, or {@code null} to take the kind away again
     * @param itemIds the pieces
     */
    public record ArtAssignRequest(Integer artId, List<Integer> itemIds) {}

    /**
     * Request body for the tidying merge, which rewrites the names of the pieces it moves.
     *
     * @param artId   the kind they all become, and whose name they all take
     * @param itemIds the pieces
     */
    public record ArtMergeRequest(int artId, List<Integer> itemIds) {}

    /**
     * How many pieces a tidying action changed.
     *
     * @param changed the count
     */
    public record TidyResult(int changed) {}
}
