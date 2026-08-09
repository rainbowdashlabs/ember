/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.board.entity.BoardTicketHistory;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketTransition;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The audit trail of a local board ticket: lane transitions, the change history and the merged
 * activity stream.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class BoardTicketHistoryRoutes implements Routes {

    private final BoardTicketService ticketService;
    private final MemberNameResolver memberNameResolver;
    private final BoardRouteGuards guards;

    @Inject
    public BoardTicketHistoryRoutes(
            BoardTicketService ticketService, MemberNameResolver memberNameResolver, BoardRouteGuards guards) {
        this.ticketService = ticketService;
        this.memberNameResolver = memberNameResolver;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/boards/{boardKey}/tickets/{ticketNumber}";
        routes.get(p + "/transitions", this::getTransitions, StationPermission.BOARD_USE);
        routes.get(p + "/history", this::getHistory, StationPermission.BOARD_USE);
        routes.get(p + "/activity", this::getActivity, StationPermission.BOARD_USE);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/transitions",
            methods = HttpMethod.GET,
            summary = "List lane transitions for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketTransition[].class)))
    private void getTransitions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(ticketService.findTransitions(guards.viewableTicketId(ctx, session)).stream()
                .map(transition -> {
                    var resolved = memberNameResolver.resolveDisplay(transition.actor());
                    return BoardTicketTransitionResponse.from(transition, resolved.identity(), resolved.name());
                })
                .toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/history",
            methods = HttpMethod.GET,
            summary = "List history entries for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketHistory[].class)))
    private void getHistory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(ticketService.findHistory(guards.viewableTicketId(ctx, session)).stream()
                .map(entry -> {
                    var resolved = memberNameResolver.resolveDisplay(entry.actor());
                    return BoardTicketHistoryResponse.from(entry, resolved.identity(), resolved.name());
                })
                .toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/activity",
            methods = HttpMethod.GET,
            summary = "List activity entries for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void getActivity(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(ticketService.findActivity(guards.viewableTicketId(ctx, session)));
    }
}
