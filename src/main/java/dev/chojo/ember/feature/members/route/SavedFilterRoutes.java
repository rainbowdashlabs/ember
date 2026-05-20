/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.members.repository.SavedFilterRepository;
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

/**
 * Routes for saving and managing member list filter presets per user.
 */
@Singleton
public class SavedFilterRoutes implements Routes {
    private final SavedFilterRepository repository;

    @Inject
    public SavedFilterRoutes(SavedFilterRepository repository) {
        this.repository = repository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/saved-filters", this::list, Roles.LOGIN);
        routes.post(prefix + "/saved-filters", this::create, Roles.LOGIN);
        routes.delete(prefix + "/saved-filters/{id}", this::delete, Roles.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/saved-filters",
            methods = HttpMethod.GET,
            summary = "List saved filters for the current user and table type",
            tags = {"Saved Filters"},
            queryParams = @OpenApiParam(name = "tableType", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SavedFilter[].class)))
    private void list(Context ctx) {
        var session = UserSession.from(ctx);
        String tableType = ctx.queryParamAsClass("tableType", String.class).get();
        ctx.json(repository.findByAccountAndTable(session.accountId(), tableType));
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
        if (request.tableType() == null || request.name() == null || request.filterData() == null) {
            throw new BadRequestResponse("tableType, name and filterData are required");
        }
        var existing = repository.findByAccountAndTable(session.accountId(), request.tableType());
        var filter = repository.create(
                session.accountId(), request.tableType(), request.name(), request.filterData(), existing.size());
        ctx.status(HttpStatus.CREATED).json(filter);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (repository.delete(id, session.accountId())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    public record CreateFilterRequest(String tableType, String name, String filterData) {}
}
