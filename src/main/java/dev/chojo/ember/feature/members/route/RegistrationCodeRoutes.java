/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.feature.members.service.RegistrationCodeService;
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

/**
 * Routes for managing registration codes that allow self-registration with automatic
 * group assignment and usage limits.
 */
@Singleton
public class RegistrationCodeRoutes implements Routes {
    private final RegistrationCodeService codeService;

    @Inject
    public RegistrationCodeRoutes(RegistrationCodeService codeService) {
        this.codeService = codeService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/registration-codes", this::list, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/registration-codes", this::create, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/registration-codes/{id}", this::get, InstancePermission.ADMINISTRATOR);
        routes.delete(prefix + "/registration-codes/{id}", this::delete, InstancePermission.ADMINISTRATOR);

        routes.get(prefix + "/registration-codes/{id}/groups", this::getGroups, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/registration-codes/{id}/groups", this::setGroups, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/registration-codes",
            methods = HttpMethod.GET,
            summary = "List registration codes for the current station",
            tags = {"Registration Codes"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RegistrationCode[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(codeService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/registration-codes",
            methods = HttpMethod.POST,
            summary = "Create a registration code",
            tags = {"Registration Codes"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateCodeRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = RegistrationCode.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateCodeRequest.class);
        if (isBlank(request.code())) {
            throw new BadRequestResponse("code is required");
        }
        ctx.status(HttpStatus.CREATED).json(codeService.create(session.stationId(), request.code(), request.maxUses()));
    }

    @OpenApi(
            path = "/api/v1/registration-codes/{id}",
            methods = HttpMethod.GET,
            summary = "Get a registration code with its assigned groups",
            tags = {"Registration Codes"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CodeDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        codeService
                .findById(id)
                .ifPresentOrElse(
                        code -> {
                            var groupIds = codeService.findGroupIds(id);
                            ctx.json(new CodeDetail(
                                    code.id(), code.stationId(), code.code(), code.maxUses(), code.uses(), groupIds));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/registration-codes/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a registration code",
            tags = {"Registration Codes"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (codeService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/registration-codes/{id}/groups",
            methods = HttpMethod.GET,
            summary = "Get group IDs assigned to a registration code",
            tags = {"Registration Codes"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void getGroups(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(codeService.findGroupIds(id));
    }

    @OpenApi(
            path = "/api/v1/registration-codes/{id}/groups",
            methods = HttpMethod.PUT,
            summary = "Set groups assigned to a registration code (replaces all)",
            description =
                    "Provide the full list of group IDs. Existing assignments not in the list are removed, new ones are added.",
            tags = {"Registration Codes"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetGroupsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void setGroups(Context ctx) {
        int codeId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(SetGroupsRequest.class);
        List<Integer> groupIds = request.groupIds() != null ? request.groupIds() : List.of();
        ctx.json(codeService.setGroups(codeId, groupIds));
    }

    // -- Request/Response records --

    public record CreateCodeRequest(String code, int maxUses) {}

    public record CodeDetail(int id, int stationId, String code, int maxUses, int uses, List<Integer> groupIds) {}

    public record SetGroupsRequest(List<Integer> groupIds) {}
}
