/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterInventoryTag;
import dev.chojo.ember.feature.cluster.service.ClusterInventoryTagService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The words an association recommends to the stations under it.
 *
 * <p>These stand beside what a station calls its own things and never replace them, so nothing here
 * writes into a station's stock.
 */
@Singleton
public class ClusterInventoryTagRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterInventoryTagService tagService;

    @Inject
    public ClusterInventoryTagRoutes(ClusterService clusterService, ClusterInventoryTagService tagService) {
        this.clusterService = clusterService;
        this.tagService = tagService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/inventory-tags", this::list, ClusterPermission.CLUSTER_INVENTORY_READ);
        routes.post(prefix + "/cluster/inventory-tags", this::create, ClusterPermission.CLUSTER_INVENTORY_EDIT);
        routes.put(prefix + "/cluster/inventory-tags/{tagId}", this::update, ClusterPermission.CLUSTER_INVENTORY_EDIT);
        routes.delete(
                prefix + "/cluster/inventory-tags/{tagId}", this::delete, ClusterPermission.CLUSTER_INVENTORY_EDIT);
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory-tags",
            methods = HttpMethod.GET,
            summary = "The words this association recommends",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterInventoryTag[].class)))
    private void list(Context ctx) {
        ctx.json(tagService.findByCluster(requireActive(ctx).id()));
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory-tags",
            methods = HttpMethod.POST,
            summary = "Recommend a word",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterTagRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterInventoryTag.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterTagRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(tagService.create(cluster.id(), request.name(), request.color(), request.stationGroupId()));
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory-tags/{tagId}",
            methods = HttpMethod.PUT,
            pathParams = @OpenApiParam(name = "tagId", type = Integer.class, required = true),
            summary = "Change a recommendation",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterTagRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterInventoryTag.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterTagRequest.class);
        ctx.json(tagService.update(
                cluster.id(),
                pathInt(ctx, "tagId"),
                request.name(),
                request.color(),
                request.position(),
                request.stationGroupId()));
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory-tags/{tagId}",
            methods = HttpMethod.DELETE,
            pathParams = @OpenApiParam(name = "tagId", type = Integer.class, required = true),
            summary = "Withdraw a recommendation",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        Cluster cluster = requireActive(ctx);
        tagService.delete(cluster.id(), pathInt(ctx, "tagId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    /**
     * @param name           the word
     * @param color          optional hex colour for the badge
     * @param position       where it should sit
     * @param stationGroupId the group of stations it is meant for, or {@code null} for all of them
     */
    public record ClusterTagRequest(String name, String color, int position, Integer stationGroupId) {}
}
