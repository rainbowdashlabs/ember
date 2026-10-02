/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.MemberGroupSet;
import dev.chojo.ember.feature.members.service.MemberGroupSetService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
 * Routes for the sets of groups a station keeps, each allowing a member in only one of its groups.
 * Which groups belong to a set is written on the group.
 */
@Singleton
public class MemberGroupSetRoutes implements Routes {
    private final MemberGroupSetService setService;

    @Inject
    public MemberGroupSetRoutes(MemberGroupSetService setService) {
        this.setService = setService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/group-sets", this::list, StationPermission.LOGIN);
        routes.post(prefix + "/group-sets", this::create, StationPermission.MEMBER_MANAGE_GROUP);
        routes.put(prefix + "/group-sets/{id}", this::rename, StationPermission.MEMBER_MANAGE_GROUP);
        routes.delete(prefix + "/group-sets/{id}", this::delete, StationPermission.MEMBER_MANAGE_GROUP);
    }

    @OpenApi(
            path = "/api/v1/group-sets",
            methods = HttpMethod.GET,
            summary = "List the sets of groups of the current station",
            tags = {"Member Groups"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroupSet[].class)))
    private void list(Context ctx) {
        ctx.json(setService.findByStation(StationSession.from(ctx).stationId()));
    }

    @OpenApi(
            path = "/api/v1/group-sets",
            methods = HttpMethod.POST,
            summary = "Create a set of groups",
            tags = {"Member Groups"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GroupSetRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberGroupSet.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(GroupSetRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(setService.create(StationSession.from(ctx).stationId(), request.name()));
    }

    @OpenApi(
            path = "/api/v1/group-sets/{id}",
            methods = HttpMethod.PUT,
            summary = "Rename a set of groups",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GroupSetRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroupSet.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void rename(Context ctx) {
        var request = ctx.bodyAsClass(GroupSetRequest.class);
        ctx.json(setService.rename(StationSession.from(ctx).stationId(), pathInt(ctx, "id"), request.name()));
    }

    @OpenApi(
            path = "/api/v1/group-sets/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a set of groups, keeping its groups and their members",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        setService.delete(StationSession.from(ctx).stationId(), pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A set's name.
     *
     * @param name the name, unique within the station
     */
    public record GroupSetRequest(String name) {}
}
