/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.members.service.SavedFilterService;
import dev.chojo.ember.feature.members.service.SavedFilterService.CreateFilterRequest;
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
 * Routes for saving and managing member list filter presets per user.
 */
@Singleton
public class SavedFilterRoutes implements Routes {
    private final SavedFilterService filters;

    @Inject
    public SavedFilterRoutes(SavedFilterService filters) {
        this.filters = filters;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/saved-filters", this::list, StationPermission.LOGIN);
        routes.post(prefix + "/saved-filters", this::create, StationPermission.LOGIN);
        routes.delete(prefix + "/saved-filters/{id}", this::delete, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/saved-filters",
            methods = HttpMethod.GET,
            summary = "List saved filters for the current user and table type",
            tags = {"Saved Filters"},
            queryParams = @OpenApiParam(name = "tableType", required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SavedFilter[].class)))
    private void list(Context ctx) {
        var session = UserSession.from(ctx);
        var tableType = FilterTableType.valueOf(
                ctx.queryParamAsClass("tableType", String.class).get().toUpperCase());
        ctx.json(filters.list(session.accountId(), tableType));
    }

    @OpenApi(
            path = "/api/v1/saved-filters",
            methods = HttpMethod.POST,
            summary = "Create a saved filter",
            tags = {"Saved Filters"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateFilterRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = SavedFilter.class)),
                @OpenApiResponse(status = "400")
            })
    private void create(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateFilterRequest.class);
        ctx.status(HttpStatus.CREATED).json(filters.create(session.accountId(), request));
    }

    @OpenApi(
            path = "/api/v1/saved-filters/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a saved filter",
            tags = {"Saved Filters"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {@OpenApiResponse(status = "204"), @OpenApiResponse(status = "404")})
    private void delete(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        filters.delete(session.accountId(), id);
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
