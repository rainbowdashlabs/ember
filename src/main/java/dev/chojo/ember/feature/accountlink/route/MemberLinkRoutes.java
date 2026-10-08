/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.accountlink.entity.LinkState;
import dev.chojo.ember.feature.accountlink.service.AccountLinkService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The station's side of the requests it sent to link an existing account to one of its members: where
 * each stands, and sending one again.
 */
@Singleton
public class MemberLinkRoutes implements Routes {
    private final AccountLinkService links;

    @Inject
    public MemberLinkRoutes(AccountLinkService links) {
        this.links = links;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/member-links", this::list, StationPermission.MEMBER_READ);
        routes.get(prefix + "/member-links/{memberId}", this::get, StationPermission.MEMBER_READ);
        routes.post(prefix + "/member-links/{memberId}/send-again", this::sendAgain, StationPermission.MEMBER_EDIT);
    }

    @OpenApi(
            path = "/api/v1/member-links",
            methods = HttpMethod.GET,
            summary = "Where the latest link request of every member of the station stands",
            tags = {"Account links"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberLink[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(links.statesAt(session.stationId()).entrySet().stream()
                .map(entry -> new MemberLink(entry.getKey(), entry.getValue()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/member-links/{memberId}",
            methods = HttpMethod.GET,
            summary = "Where the latest link request of one member stands",
            tags = {"Account links"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberLinkResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(new MemberLinkResponse(
                links.stateOf(session.stationId(), pathInt(ctx, "memberId")).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/member-links/{memberId}/send-again",
            methods = HttpMethod.POST,
            summary = "Send a member's link request again",
            description =
                    "Sends a waiting request again with a fresh deadline, or asks anew where it ran out. Refused where the person declined, and within a day of the last time it was sent.",
            tags = {"Account links"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LinkState.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void sendAgain(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(links.sendAgain(
                session.stationId(), pathInt(ctx, "memberId"), session.member().id()));
    }

    /**
     * One member's latest link request.
     *
     * @param memberId the member
     * @param link     where the request stands
     */
    public record MemberLink(int memberId, LinkState link) {}

    /**
     * One member's latest link request.
     *
     * @param link where it stands, or null where the station never asked for this member
     */
    public record MemberLinkResponse(@Nullable LinkState link) {}
}
