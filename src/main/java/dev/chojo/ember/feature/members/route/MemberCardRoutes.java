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
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.MemberCard;
import dev.chojo.ember.feature.members.service.MemberCardService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The card shown when somebody at a station looks at a member's name.
 */
@Singleton
public class MemberCardRoutes implements Routes {
    private final MemberCardService cardService;

    @Inject
    public MemberCardRoutes(MemberCardService cardService) {
        this.cardService = cardService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station-members/by-uid/{uid}/card", this::card, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/station-members/by-uid/{uid}/card",
            methods = HttpMethod.GET,
            summary = "Get the short profile card of a member of the caller's station",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "uid", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberCard.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void card(Context ctx) {
        ctx.json(cardService
                .find(StationSession.from(ctx), pathUuid(ctx, "uid"))
                .orElseThrow(MemberRefusal.MEMBER_NOT_HERE_BY_UID::raise));
    }
}
