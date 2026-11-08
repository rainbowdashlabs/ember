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
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The bundles a station keeps its profile questions in: which there are, writing one, renaming it,
 * ordering its questions and dissolving it. Which bundle a question is in is said where the question is
 * written.
 */
@Singleton
public class ProfileFieldSetRoutes implements Routes {
    private final ProfileFieldService fieldService;

    @Inject
    public ProfileFieldSetRoutes(ProfileFieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/profile-field-sets", this::list, StationPermission.USER);
        routes.post(prefix + "/profile-field-sets", this::create, StationPermission.MEMBER_FIELDS);
        routes.put(prefix + "/profile-field-sets/{id}", this::update, StationPermission.MEMBER_FIELDS);
        routes.delete(prefix + "/profile-field-sets/{id}", this::dissolve, StationPermission.MEMBER_FIELDS);
        routes.put(prefix + "/profile-field-sets/{id}/order", this::order, StationPermission.MEMBER_FIELDS);
    }

    @OpenApi(
            path = "/api/v1/profile-field-sets",
            methods = HttpMethod.GET,
            summary = "The groups this station keeps its profile fields in",
            tags = {"Profile Fields"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FieldSet[].class)))
    private void list(Context ctx) {
        ctx.json(fieldService.findSets(StationSession.from(ctx).stationId()));
    }

    @OpenApi(
            path = "/api/v1/profile-field-sets",
            methods = HttpMethod.POST,
            summary = "Write down a new, empty group of profile fields",
            tags = {"Profile Fields"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldSetRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = FieldSet.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(FieldSetRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(fieldService.createSet(
                        StationSession.from(ctx).stationId(), request.name(), request.nameAsSection()));
    }

    @OpenApi(
            path = "/api/v1/profile-field-sets/{id}",
            methods = HttpMethod.PUT,
            summary = "Rename a group of profile fields, or change whether its name is a heading",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldSetRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        var request = ctx.bodyAsClass(FieldSetRequest.class);
        fieldService.updateSet(
                StationSession.from(ctx).stationId(), pathInt(ctx, "id"), request.name(), request.nameAsSection());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/profile-field-sets/{id}",
            methods = HttpMethod.DELETE,
            summary = "Dissolve a group of profile fields, keeping its fields",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void dissolve(Context ctx) {
        fieldService.dissolveSet(StationSession.from(ctx).stationId(), pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/profile-field-sets/{id}/order",
            methods = HttpMethod.PUT,
            summary = "Put the fields of a group in order",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldSetOrderRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void order(Context ctx) {
        var request = ctx.bodyAsClass(FieldSetOrderRequest.class);
        fieldService.orderSet(
                StationSession.from(ctx).stationId(),
                pathInt(ctx, "id"),
                Objects.requireNonNullElse(request.fieldIds(), List.of()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @param name          what the group is called
     * @param nameAsSection whether the forms show the name as a heading above its fields
     */
    public record FieldSetRequest(@Nullable String name, boolean nameAsSection) {}

    /** @param fieldIds the group's fields in the order they should stand */
    public record FieldSetOrderRequest(@Nullable List<Integer> fieldIds) {}
}
