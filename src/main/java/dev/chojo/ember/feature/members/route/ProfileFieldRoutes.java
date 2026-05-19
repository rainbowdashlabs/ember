/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ProfileFieldRoutes implements Routes {
    private final ProfileFieldService profileFieldService;

    @Inject
    public ProfileFieldRoutes(ProfileFieldService profileFieldService) {
        this.profileFieldService = profileFieldService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // -- Field Definitions --

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Field definitions (station config) — requires MEMBER_MANAGEMENT
        routes.get(prefix + "/profile-fields", this::list, Roles.USER);
        routes.post(prefix + "/profile-fields", this::create, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/profile-fields/{id}", this::get, Roles.USER);
        routes.put(prefix + "/profile-fields/{id}", this::update, Roles.MEMBER_MANAGEMENT);
        routes.delete(prefix + "/profile-fields/{id}", this::delete, Roles.MEMBER_MANAGEMENT);

        // Field values per member — MEMBER or TEAM can read/write own, MEMBER_MANAGEMENT for any
        routes.get(prefix + "/station-members/{memberId}/profile", this::getValues, Roles.USER);
        routes.put(prefix + "/station-members/{memberId}/profile", this::setValues, Roles.USER);
    }

    @OpenApi(
            path = "/api/v1/profile-fields",
            methods = HttpMethod.GET,
            summary = "List profile field definitions for the current station",
            tags = {"Profile Fields"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileField[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(profileFieldService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/profile-fields",
            methods = HttpMethod.POST,
            summary = "Create a profile field definition",
            tags = {"Profile Fields"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProfileFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ProfileField.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ProfileFieldRequest.class);
        if (isBlank(request.name()) || isBlank(request.fieldType())) {
            throw new BadRequestResponse("name and fieldType are required");
        }
        try {
            dev.chojo.ember.feature.members.entity.ProfileFieldType.valueOf(request.fieldType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid field type: " + request.fieldType());
        }
        ctx.status(HttpStatus.CREATED)
                .json(profileFieldService.create(
                        session.stationId(),
                        request.name(),
                        request.fieldType(),
                        request.config(),
                        request.position(),
                        request.scope()));
    }

    @OpenApi(
            path = "/api/v1/profile-fields/{id}",
            methods = HttpMethod.GET,
            summary = "Get a profile field definition",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileField.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        profileFieldService.findById(id).ifPresentOrElse(field -> ctx.json(field), () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/profile-fields/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a profile field definition",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProfileFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileField.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(ProfileFieldRequest.class);
        if (isBlank(request.name()) || isBlank(request.fieldType())) {
            throw new BadRequestResponse("name and fieldType are required");
        }
        profileFieldService
                .update(
                        id,
                        request.name(),
                        request.fieldType(),
                        request.config(),
                        request.position(),
                        request.keepOnArchive() != null ? request.keepOnArchive() : false)
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    // -- Field Values --

    @OpenApi(
            path = "/api/v1/profile-fields/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a profile field definition",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (profileFieldService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/profile",
            methods = HttpMethod.GET,
            summary = "Get profile field values for a member",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileFieldValue[].class)))
    private void getValues(Context ctx) {
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        ctx.json(profileFieldService.findValues(memberId));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/profile",
            methods = HttpMethod.PUT,
            summary = "Set profile field values for a member (batch upsert)",
            description = "Provide all field values to set. Each entry is upserted.",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetValuesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileFieldValue[].class)))
    private void setValues(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var request = ctx.bodyAsClass(SetValuesRequest.class);
        boolean canEditReadonly = session.hasRole(Roles.MEMBER_MANAGEMENT);

        List<ProfileFieldService.FieldValueEntry> entries = request.values() != null
                ? request.values().stream()
                        .filter(v -> {
                            if (!canEditReadonly) {
                                var field = profileFieldService
                                        .findById(v.fieldId())
                                        .orElse(null);
                                return field == null
                                        || !ProfileFieldConfig.parse(field.config())
                                                .readonly();
                            }
                            return true;
                        })
                        .map(v -> new ProfileFieldService.FieldValueEntry(v.fieldId(), v.value()))
                        .toList()
                : List.of();
        ctx.json(profileFieldService.setValues(
                memberId, entries, session.member().id()));
    }

    // -- Request records --

    public record ProfileFieldRequest(
            String name,
            String fieldType,
            String config,
            int position,
            ProfileFieldScope scope,
            Boolean keepOnArchive) {}

    @OpenApiName("ProfileFieldValueEntry")
    public record FieldValueEntry(int fieldId, String value) {}

    public record SetValuesRequest(List<FieldValueEntry> values) {}
}
