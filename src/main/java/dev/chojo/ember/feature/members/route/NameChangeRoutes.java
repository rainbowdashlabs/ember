/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.NameChangeView;
import dev.chojo.ember.feature.members.entity.OwnNameChange;
import dev.chojo.ember.feature.members.service.NameChangeService;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * New register names members asked for: the open requests a member manager approves or denies,
 * and the member's own request, which they can take back.
 */
@Singleton
public class NameChangeRoutes implements Routes {
    private final NameChangeService nameChanges;

    @Inject
    public NameChangeRoutes(NameChangeService nameChanges) {
        this.nameChanges = nameChanges;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/name-change-requests", this::listOpen, StationPermission.MEMBER_CHANGES);
        routes.post(prefix + "/name-change-requests/{id}/approve", this::approve, StationPermission.MEMBER_CHANGES);
        routes.post(prefix + "/name-change-requests/{id}/deny", this::deny, StationPermission.MEMBER_CHANGES);
        routes.get(prefix + "/account/name-change-request", this::own, StationPermission.LOGIN);
        routes.delete(prefix + "/account/name-change-request", this::withdraw, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/name-change-requests",
            methods = HttpMethod.GET,
            summary = "List the open name requests of the station's members",
            tags = {"Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NameChangeView[].class)))
    private void listOpen(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(nameChanges.openAt(session.stationId(), session.stationUid()));
    }

    @OpenApi(
            path = "/api/v1/name-change-requests/{id}/approve",
            methods = HttpMethod.POST,
            summary = "Approve a name request; the account takes the name",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void approve(Context ctx) {
        var session = StationSession.from(ctx);
        nameChanges.approve(session.stationId(), session.accountId(), pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/name-change-requests/{id}/deny",
            methods = HttpMethod.POST,
            summary = "Deny a name request, with an optional reason the member is shown",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DenyNameChangeRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deny(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(DenyNameChangeRequest.class);
        nameChanges.deny(session.stationId(), session.accountId(), pathInt(ctx, "id"), request.reason());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/account/name-change-request",
            methods = HttpMethod.GET,
            summary = "The name the caller asked for and that still waits",
            tags = {"Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OwnNameChangeResponse.class)))
    private void own(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(new OwnNameChangeResponse(
                nameChanges.openOf(session.accountId()).map(OwnNameChange::of).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/account/name-change-request",
            methods = HttpMethod.DELETE,
            summary = "Take back the name the caller asked for",
            tags = {"Members"},
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdraw(Context ctx) {
        nameChanges.withdraw(UserSession.from(ctx).accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @param reason why the name is turned down, shown to the member; absent or blank for none
     */
    public record DenyNameChangeRequest(@Nullable String reason) {}

    /**
     * @param pending the name that waits, or null where nothing does
     */
    public record OwnNameChangeResponse(@Nullable OwnNameChange pending) {}
}
