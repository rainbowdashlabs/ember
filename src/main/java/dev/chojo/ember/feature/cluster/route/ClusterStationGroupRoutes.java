/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterStationGroup;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.cluster.service.ClusterStationGroupService;
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

import java.util.List;
import java.util.UUID;

/**
 * How an association files its stations.
 *
 * <p>Filing stations is station management, so the writes sit with {@code CLUSTER_STATIONS} beside the
 * station list itself. Reading is open to anybody who may act for the association, because the fields screen
 * needs the list to draw its tabs.
 */
@Singleton
public class ClusterStationGroupRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterStationGroupService groupService;

    @Inject
    public ClusterStationGroupRoutes(ClusterService clusterService, ClusterStationGroupService groupService) {
        this.clusterService = clusterService;
        this.groupService = groupService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/station-groups", this::list, ClusterPermission.USER);
        routes.post(prefix + "/cluster/station-groups", this::create, ClusterPermission.CLUSTER_STATIONS);
        routes.put(prefix + "/cluster/station-groups/{groupId}", this::rename, ClusterPermission.CLUSTER_STATIONS);
        routes.delete(prefix + "/cluster/station-groups/{groupId}", this::delete, ClusterPermission.CLUSTER_STATIONS);
        routes.get(prefix + "/cluster/station-groups/{groupId}/stations", this::listStations, ClusterPermission.USER);
        routes.put(
                prefix + "/cluster/station-groups/{groupId}/stations",
                this::setStations,
                ClusterPermission.CLUSTER_STATIONS);
    }

    @OpenApi(
            path = "/api/v1/cluster/station-groups",
            methods = HttpMethod.GET,
            summary = "How this association files its stations",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationGroupResponse[].class)))
    private void list(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(groupService.findByCluster(cluster.id()).stream()
                .map(ClusterStationGroupRoutes::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/station-groups",
            methods = HttpMethod.POST,
            summary = "File a new group of stations",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationGroupRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = StationGroupResponse.class)))
    private void create(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(StationGroupRequest.class);
        ctx.status(HttpStatus.CREATED).json(toResponse(groupService.create(cluster.id(), request.name())));
    }

    @OpenApi(
            path = "/api/v1/cluster/station-groups/{groupId}",
            pathParams = @OpenApiParam(name = "groupId", type = Integer.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Rename a group of stations",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationGroupRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void rename(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(StationGroupRequest.class);
        groupService.rename(cluster.id(), groupId(ctx), request.name());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/station-groups/{groupId}",
            pathParams = @OpenApiParam(name = "groupId", type = Integer.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Remove a group of stations, unless questions are asked of it",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        Cluster cluster = requireActive(ctx);
        groupService.delete(cluster.id(), groupId(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/station-groups/{groupId}/stations",
            pathParams = @OpenApiParam(name = "groupId", type = Integer.class, required = true),
            methods = HttpMethod.GET,
            summary = "The stations filed under one group",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = StationGroupStationResponse[].class)))
    private void listStations(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(groupService.findStations(cluster.id(), groupId(ctx)).stream()
                .map(station -> new StationGroupStationResponse(station.uid(), station.name()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/station-groups/{groupId}/stations",
            pathParams = @OpenApiParam(name = "groupId", type = Integer.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Replace the stations filed under one group",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationGroupStationsRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setStations(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(StationGroupStationsRequest.class);
        groupService.setStations(
                cluster.id(), groupId(ctx), request.stationUids() == null ? List.of() : request.stationUids());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private static StationGroupResponse toResponse(ClusterStationGroup group) {
        return new StationGroupResponse(group.id(), group.name());
    }

    private static int groupId(Context ctx) {
        return ctx.pathParamAsClass("groupId", Integer.class).get();
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    /** One way the association files its stations. */
    public record StationGroupResponse(int id, String name) {}

    /** What a group of stations is called. */
    public record StationGroupRequest(String name) {}

    /** One station in a group, in the currency the association's station API speaks. */
    public record StationGroupStationResponse(UUID stationUid, String name) {}

    /** The stations that are in the group afterwards. */
    public record StationGroupStationsRequest(List<UUID> stationUids) {}
}
