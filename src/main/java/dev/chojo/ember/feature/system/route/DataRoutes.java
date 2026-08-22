/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

@Singleton
public class DataRoutes implements Routes {

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/data/permissions", this::getPermissionHierarchy);
        routes.get(prefix + "/data/cluster-permissions", this::getClusterPermissionHierarchy);
    }

    @OpenApi(
            path = "/api/v1/data/permissions",
            methods = HttpMethod.GET,
            summary = "Get the station permission hierarchy",
            description = "Returns all station permissions with their direct children. No authentication required.",
            tags = {"Data"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PermissionNode[].class)))
    private void getPermissionHierarchy(Context ctx) {
        List<PermissionNode> nodes = Arrays.stream(StationPermission.values())
                .map(p -> new PermissionNode(
                        p.name(), Arrays.stream(p.getChildren()).map(Enum::name).toList()))
                .toList();
        ctx.json(nodes);
    }

    /**
     * The same shape for a cluster's permissions, so the one picker the station uses can draw them too.
     *
     * <p>A separate endpoint rather than a flag on the first, because they are two enums and a caller
     * always knows which of the two it is asking about.
     */
    @OpenApi(
            path = "/api/v1/data/cluster-permissions",
            methods = HttpMethod.GET,
            summary = "Get the cluster permission hierarchy",
            description = "Returns all cluster permissions with their direct children. No authentication required.",
            tags = {"Data"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PermissionNode[].class)))
    private void getClusterPermissionHierarchy(Context ctx) {
        List<PermissionNode> nodes = Arrays.stream(ClusterPermission.values())
                .map(p -> new PermissionNode(
                        p.name(), Arrays.stream(p.getChildren()).map(Enum::name).toList()))
                .toList();
        ctx.json(nodes);
    }

    public record PermissionNode(String name, List<String> children) {}
}
