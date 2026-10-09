/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.accountlink.entity.LinkPrompt;
import dev.chojo.ember.feature.accountlink.service.LinkAnswerService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The signed-in person's side of the requests stations send to link their account to a member: the
 * list the prompt after sign-in shows, the request a mailed link opens, and the answer.
 */
@Singleton
public class AccountLinkRoutes implements Routes {
    private final LinkAnswerService answers;

    @Inject
    public AccountLinkRoutes(LinkAnswerService answers) {
        this.answers = answers;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/account/link-requests", this::waiting, StationPermission.LOGIN);
        routes.get(prefix + "/account/link-requests/by-token/{token}", this::opened, StationPermission.LOGIN);
        routes.post(prefix + "/account/link-requests/{uid}/accept", this::accept, StationPermission.LOGIN);
        routes.post(prefix + "/account/link-requests/{uid}/decline", this::decline, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/account/link-requests",
            methods = HttpMethod.GET,
            summary = "The link requests waiting for the signed-in account",
            tags = {"Account links"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LinkPrompt[].class)))
    private void waiting(Context ctx) {
        ctx.json(answers.waitingFor(UserSession.from(ctx).accountId()));
    }

    /**
     * The request a mailed link opens. Only for a session of the account it names, and it answers
     * nothing: the person accepts or declines on the screen it opens.
     */
    @OpenApi(
            path = "/api/v1/account/link-requests/by-token/{token}",
            methods = HttpMethod.GET,
            summary = "The link request a mailed link opens",
            description =
                    "Answers only for a signed-in session of the account the request names, and only while it waits. It never accepts the request.",
            tags = {"Account links"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LinkPrompt.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("a link request is opened by the account it names, whichever station asked")
    private void opened(Context ctx) {
        ctx.json(answers.opened(UserSession.from(ctx).accountId(), ctx.pathParam("token")));
    }

    @OpenApi(
            path = "/api/v1/account/link-requests/{uid}/accept",
            methods = HttpMethod.POST,
            summary = "Link the signed-in account to the member a station asked about",
            tags = {"Account links"},
            pathParams = @OpenApiParam(name = "uid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("a link request is answered by the account it names, whichever station asked")
    private void accept(Context ctx) {
        answers.accept(
                UserSession.from(ctx).accountId(), pathUuid(ctx, "uid"), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new MessageResponse("Account linked"));
    }

    @OpenApi(
            path = "/api/v1/account/link-requests/{uid}/decline",
            methods = HttpMethod.POST,
            summary = "Refuse a station's request to link the signed-in account",
            tags = {"Account links"},
            pathParams = @OpenApiParam(name = "uid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("a link request is answered by the account it names, whichever station asked")
    private void decline(Context ctx) {
        answers.decline(UserSession.from(ctx).accountId(), pathUuid(ctx, "uid"));
        ctx.json(new MessageResponse("Link declined"));
    }
}
