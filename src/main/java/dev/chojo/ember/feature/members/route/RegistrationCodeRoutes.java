/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.feature.members.service.RegistrationCodeService;
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

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

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
        ctx.json(codeService.findByStation(session.requireStationId()));
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
            throw Refusal.REGISTRATION_CODE_TEXT_MISSING.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(codeService.create(session.requireStationId(), request.code(), request.maxUses()));
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
        int id = pathInt(ctx, "id");
        int stationId = stationOf(ctx, Refusal.REGISTRATION_CODE_NOT_HERE);
        var code = codeService.findInStation(stationId, id).orElseThrow(Refusal.REGISTRATION_CODE_NOT_HERE::raise);
        ctx.json(new CodeDetail(
                code.id(),
                code.stationId(),
                code.code(),
                code.maxUses(),
                code.uses(),
                codeService.findGroupIds(stationId, id)));
    }

    /**
     * The station the caller is working in, which every code addressed by number has to belong to. An
     * instance administrator with no station chosen is answered as if the code were not there.
     */
    private static int stationOf(Context ctx, Refusal notHere) {
        Integer stationId = UserSession.from(ctx).stationId();
        if (stationId == null) throw notHere.raise();
        return stationId;
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
        int id = pathInt(ctx, "id");
        codeService.delete(stationOf(ctx, Refusal.REGISTRATION_CODE_NOT_DELETED), id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/registration-codes/{id}/groups",
            methods = HttpMethod.GET,
            summary = "Get group IDs assigned to a registration code",
            tags = {"Registration Codes"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void getGroups(Context ctx) {
        int id = pathInt(ctx, "id");
        ctx.json(codeService.findGroupIds(stationOf(ctx, Refusal.REGISTRATION_CODE_NOT_HERE_FOR_GROUPS), id));
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
        int codeId = pathInt(ctx, "id");
        var request = ctx.bodyAsClass(SetGroupsRequest.class);
        List<Integer> groupIds = request.groupIds() != null ? request.groupIds() : List.of();
        ctx.json(codeService.setGroups(
                stationOf(ctx, Refusal.REGISTRATION_CODE_NOT_HERE_TO_CHANGE_GROUPS), codeId, groupIds));
    }

    // -- Request/Response records --

    public record CreateCodeRequest(String code, int maxUses) {}

    public record CodeDetail(int id, int stationId, String code, int maxUses, int uses, List<Integer> groupIds) {}

    public record SetGroupsRequest(List<Integer> groupIds) {}
}
