/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteChecklistItemRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteCommentRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteEditCommentRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteUpdateChecklistItemRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteWatchRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.WatcherResponse;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import io.javalin.http.Context;
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
 * Server-to-server detail endpoints for a ticket on a shared board: comments, checklist items and
 * watchers.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class RemoteBoardTicketDetailRoutes implements Routes {

    private final BoardTicketService ticketService;
    private final MemberNameResolver memberNameResolver;
    private final RemoteBoardGuards guards;

    @Inject
    public RemoteBoardTicketDetailRoutes(
            BoardTicketService ticketService, MemberNameResolver memberNameResolver, RemoteBoardGuards guards) {
        this.ticketService = ticketService;
        this.memberNameResolver = memberNameResolver;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String rp = prefix + "/remote/boards/{boardKey}/tickets/{ticketNumber}";

        routes.get(rp + "/comments", this::getComments);
        routes.post(rp + "/comments", this::addComment);
        routes.put(rp + "/comments/{commentId}", this::editComment);
        routes.delete(rp + "/comments/{commentId}", this::deleteComment);

        routes.get(rp + "/checklist", this::getChecklist);
        routes.post(rp + "/checklist", this::addChecklistItem);
        routes.put(rp + "/checklist/{itemId}", this::updateChecklistItem);
        routes.delete(rp + "/checklist/{itemId}", this::deleteChecklistItem);

        routes.get(rp + "/watchers", this::getWatchers);
        routes.post(rp + "/watch", this::watchTicket);
        routes.delete(rp + "/watch", this::unwatchTicket);
    }

    private void getComments(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(ticketService.findComments(ticketId).stream()
                .map(comment -> CommentResponseMapper.fromBoard(memberNameResolver, comment))
                .toList());
    }

    private void getChecklist(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(ticketService.findChecklistItems(ticketId));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/watchers",
            methods = HttpMethod.GET,
            summary = "Get watchers for a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void getWatchers(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(new WatcherResponse(ticketService.findWatchers(ticketId), List.of()));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/comments",
            methods = HttpMethod.POST,
            summary = "Add a comment to a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteCommentRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void addComment(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteCommentRequest.class);
        var authorIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberId());
        var comment = ticketService.createComment(ticketId, req.parentId(), authorIdentity, req.content());
        guards.cacheDisplayName(partner, req.remoteMemberId(), req.displayName());
        ctx.json(comment);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Edit a comment on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteEditCommentRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void editComment(Context ctx) {
        var partner = guards.requirePartner(ctx);
        guards.writableBoardId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteEditCommentRequest.class);
        ticketService.updateComment(pathInt(ctx, "commentId"), req.content());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteComment(Context ctx) {
        var partner = guards.requirePartner(ctx);
        guards.writableBoardId(ctx, partner);
        ticketService.deleteComment(pathInt(ctx, "commentId"));
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/checklist",
            methods = HttpMethod.POST,
            summary = "Add a checklist item to a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void addChecklistItem(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteChecklistItemRequest.class);
        guards.cacheDisplayName(partner, req.remoteMemberUid(), req.displayName());
        ctx.json(ticketService.addChecklistItem(ticketId, req.title(), 0));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.PUT,
            summary = "Update a checklist item on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteUpdateChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateChecklistItem(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteUpdateChecklistItemRequest.class);
        guards.cacheDisplayName(partner, req.remoteMemberUid(), req.displayName());
        ticketService.updateChecklistItem(pathInt(ctx, "itemId"), ticketId, req.title(), req.checked(), 0);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a checklist item on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteChecklistItem(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        ticketService.deleteChecklistItem(pathInt(ctx, "itemId"), ticketId, 0);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.POST,
            summary = "Watch a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteWatchRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void watchTicket(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteWatchRequest.class);
        ticketService.addWatcher(ticketId, new MemberIdentity(partner.partnerStationId(), req.remoteMemberId()));
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.DELETE,
            summary = "Unwatch a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteWatchRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void unwatchTicket(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteWatchRequest.class);
        ticketService.removeWatcher(ticketId, new MemberIdentity(partner.partnerStationId(), req.remoteMemberId()));
        ctx.status(204);
    }
}
