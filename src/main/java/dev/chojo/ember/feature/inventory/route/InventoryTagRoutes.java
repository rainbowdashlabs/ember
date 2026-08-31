/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.cluster.entity.RecommendedTag;
import dev.chojo.ember.feature.cluster.service.ClusterInventoryTagService;
import dev.chojo.ember.feature.inventory.entity.InventoryTag;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.feature.inventory.service.InventoryTagService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The words a station puts on its things.
 *
 * <p>Reading them is open to anybody who may see the stock, because a tag is shown beside a thing
 * wherever the thing is shown. Writing one down needs the right to edit the stock, deliberately not
 * the rights that come with running a stock check or an exchange: a vocabulary anybody can extend on
 * the way past is not a vocabulary.
 */
@Singleton
public class InventoryTagRoutes implements Routes {
    private final InventoryTagService tagService;
    private final ClusterInventoryTagService clusterTagService;

    @Inject
    public InventoryTagRoutes(InventoryTagService tagService, ClusterInventoryTagService clusterTagService) {
        this.tagService = tagService;
        this.clusterTagService = clusterTagService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/inventory-tags", this::list, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventory-tags", this::create, StationPermission.INVENTORY_EDIT);
        routes.get(prefix + "/inventory-tags/recommended", this::recommended, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-tags/items", this::itemsByTag, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventory-tags/{tagId}", this::update, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + "/inventory-tags/{tagId}", this::delete, StationPermission.INVENTORY_EDIT);
        routes.get(
                prefix + "/inventories/{inventoryId}/item-tags",
                this::inventoryItemTags,
                StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-items/{itemId}/tags", this::itemTags, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventory-items/{itemId}/tags", this::setItemTags, StationPermission.INVENTORY_EDIT);
    }

    @OpenApi(
            path = "/api/v1/inventory-tags",
            methods = HttpMethod.GET,
            summary = "The words this station puts on its things",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TagResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var counts = tagService.countItemsPerTag(session.stationId());
        ctx.json(tagService.findByStation(session.stationId()).stream()
                .map(tag -> TagResponse.of(tag, counts.getOrDefault(tag.id(), 0)))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/inventory-tags",
            methods = HttpMethod.POST,
            summary = "Write a word down",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TagRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = TagResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(TagRequest.class);
        var tag = tagService.create(session.stationId(), request.name(), request.color());
        ctx.status(HttpStatus.CREATED).json(counted(session.stationId(), tag));
    }

    @OpenApi(
            path = "/api/v1/inventory-tags/{tagId}",
            methods = HttpMethod.PUT,
            pathParams = @OpenApiParam(name = "tagId", type = Integer.class, required = true),
            summary = "Rename a word or change how it looks",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TagRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TagResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(TagRequest.class);
        var tag = tagService.update(
                session.stationId(), pathInt(ctx, "tagId"), request.name(), request.color(), request.position());
        ctx.json(counted(session.stationId(), tag));
    }

    @OpenApi(
            path = "/api/v1/inventory-tags/{tagId}",
            methods = HttpMethod.DELETE,
            pathParams = @OpenApiParam(name = "tagId", type = Integer.class, required = true),
            summary = "Take a word out of use",
            tags = {"Inventory"},
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        tagService.delete(session.stationId(), pathInt(ctx, "tagId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/inventory-tags/recommended",
            methods = HttpMethod.GET,
            summary = "The words the association recommends to this station",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RecommendedTag[].class)))
    private void recommended(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(clusterTagService.recommendationsFor(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventory-tags/items",
            methods = HttpMethod.GET,
            summary = "The things carrying a word, in this station's own stock",
            tags = {"Inventory"},
            queryParams = @OpenApiParam(name = "tag", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TaggedItemSummary[].class)))
    private void itemsByTag(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(tagService.findItemsByTag(List.of(session.stationId()), ctx.queryParam("tag")));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/item-tags",
            methods = HttpMethod.GET,
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            summary = "The words every thing in one inventory wears",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ItemTagsResponse[].class)))
    private void inventoryItemTags(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var byItem = tagService.findTagsInInventory(session.stationId(), pathInt(ctx, "inventoryId"));
        ctx.json(byItem.entrySet().stream()
                .map(entry -> new ItemTagsResponse(entry.getKey(), entry.getValue()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{itemId}/tags",
            methods = HttpMethod.GET,
            pathParams = @OpenApiParam(name = "itemId", type = Integer.class, required = true),
            summary = "The words one thing wears",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryTag[].class)))
    private void itemTags(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(tagService.findTagsForItem(session.stationId(), pathInt(ctx, "itemId")));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{itemId}/tags",
            methods = HttpMethod.PUT,
            pathParams = @OpenApiParam(name = "itemId", type = Integer.class, required = true),
            summary = "Say which words a thing wears",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ItemTagsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryTag[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setItemTags(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ItemTagsRequest.class);
        ctx.json(tagService.setItemTags(session.stationId(), pathInt(ctx, "itemId"), request.names()));
    }

    private TagResponse counted(int stationId, InventoryTag tag) {
        return TagResponse.of(tag, tagService.countItemsPerTag(stationId).getOrDefault(tag.id(), 0));
    }

    /**
     * A tag with the number of things wearing it, which is what tells a list of words which of them
     * are doing any work.
     *
     * @param id       the tag
     * @param name     the word as the station spelled it
     * @param color    the badge colour, or {@code null}
     * @param position where it sits in the list
     * @param itemCount how many things wear it
     */
    @OpenApiName("InventoryTagResponse")
    public record TagResponse(int id, String name, String color, int position, int itemCount) {
        static TagResponse of(InventoryTag tag, int itemCount) {
            return new TagResponse(tag.id(), tag.name(), tag.color(), tag.position(), itemCount);
        }
    }

    /**
     * @param name     the word
     * @param color    optional hex colour for the badge
     * @param position where it should sit
     */
    @OpenApiName("InventoryTagRequest")
    public record TagRequest(String name, String color, int position) {}

    /**
     * @param names the words a thing should wear, as somebody typed them
     */
    public record ItemTagsRequest(List<String> names) {}

    /**
     * @param itemId the thing
     * @param tags   the words it wears
     */
    public record ItemTagsResponse(int itemId, List<InventoryTag> tags) {}
}
