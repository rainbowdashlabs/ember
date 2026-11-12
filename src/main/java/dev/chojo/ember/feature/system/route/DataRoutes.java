/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.members.entity.PermissionNode;
import dev.chojo.ember.feature.members.service.PermissionHierarchyService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DataRoutes implements Routes {
    private final PermissionHierarchyService hierarchies;

    @Inject
    public DataRoutes(PermissionHierarchyService hierarchies) {
        this.hierarchies = hierarchies;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/data/permissions", this::getPermissionHierarchy);
        routes.get(prefix + "/data/cluster-permissions", this::getClusterPermissionHierarchy);
    }

    @OpenApi(
            path = "/api/v1/data/permissions",
            methods = HttpMethod.GET,
            summary = "Get the station permission hierarchy",
            description = "Returns the station permissions with their direct children. No authentication required."
                    + " When the request names a station, the permissions of modules it switched off are left out.",
            tags = {"Data"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PermissionNode[].class)))
    private void getPermissionHierarchy(Context ctx) {
        UserSession session = ctx.attribute(ApiServer.ATTR_SESSION);
        ctx.json(hierarchies.stationHierarchy(session == null ? null : session.stationId()));
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
        ctx.json(hierarchies.clusterHierarchy());
    }
}
