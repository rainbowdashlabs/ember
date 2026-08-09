/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteDeleteLinkRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteLabelActionRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteLinkRequest;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server endpoints for what a ticket on a shared board points at: links to other
 * tickets of the same board and the labels applied to it.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class RemoteBoardTicketLinkRoutes implements Routes {

    private static final String TICKET = RemoteBoardRoutes.TICKET_PATH;

    public static final FederationEndpoint GET_LINKS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKET + "/links", BoardTicketLink.class);
    public static final FederationEndpoint CREATE_LINK = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE, TICKET + "/links", RemoteBoardRoutes.RemoteLinkRequest.class, Void.class);
    public static final FederationEndpoint DELETE_LINK = FederationEndpoint.delete(
            FederationSurface.BOARD_SHARE,
            TICKET + "/links/{linkedNumber}",
            RemoteBoardRoutes.RemoteDeleteLinkRequest.class,
            Void.class);
    public static final FederationEndpoint GET_TICKET_LABELS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKET + "/labels", BoardLabel.class);
    public static final FederationEndpoint ADD_TICKET_LABEL = FederationEndpoint.postList(
            FederationSurface.BOARD_SHARE,
            TICKET + "/labels/{labelId}",
            RemoteBoardRoutes.RemoteLabelActionRequest.class,
            BoardLabel.class);
    public static final FederationEndpoint REMOVE_TICKET_LABEL = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            TICKET + "/labels/{labelId}/remove",
            RemoteBoardRoutes.RemoteLabelActionRequest.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT =
            List.of(GET_LINKS, CREATE_LINK, DELETE_LINK, GET_TICKET_LABELS, ADD_TICKET_LABEL, REMOVE_TICKET_LABEL);

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final RemoteBoardGuards guards;

    @Inject
    public RemoteBoardTicketLinkRoutes(
            BoardService boardService, BoardTicketService ticketService, RemoteBoardGuards guards) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, binder -> binder.handle(GET_LINKS, this::getLinks)
                .handle(CREATE_LINK, this::createLink)
                .handle(DELETE_LINK, this::deleteLink)
                .handle(GET_TICKET_LABELS, this::getTicketLabels)
                .handle(ADD_TICKET_LABEL, this::addTicketLabel)
                .handle(REMOVE_TICKET_LABEL, this::removeTicketLabel));
    }

    private void getLinks(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(ticketService.findLinks(ticketId));
    }

    private void getTicketLabels(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    private void createLink(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.writableBoardId(ctx, partner);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteLinkRequest.class);
        guards.cacheDisplayName(partner, req.remoteMemberUid(), req.displayName());
        ticketService.linkTickets(
                ticketId,
                ticketIdOfNumber(boardId, req.linkedTicketNumber()),
                req.linkType(),
                guards.remoteActor(partner, req.remoteMemberUid()));
        ctx.status(HttpStatus.CREATED);
    }

    /**
     * Removes a link between two tickets. The request body is optional because legacy partners send
     * none, so an unparsable body leaves the action unattributed instead of failing the call.
     */
    private void deleteLink(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.writableBoardId(ctx, partner);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        int linkedTicketId = ticketIdOfNumber(boardId, pathInt(ctx, "linkedNumber"));
        MemberIdentity actorIdentity = null;
        try {
            var req = ctx.bodyAsClass(RemoteDeleteLinkRequest.class);
            if (req != null) {
                guards.cacheDisplayName(partner, req.remoteMemberUid(), req.displayName());
                actorIdentity = guards.remoteActor(partner, req.remoteMemberUid());
            }
        } catch (Exception ignored) {
        }
        ticketService.unlinkTickets(ticketId, linkedTicketId, actorIdentity);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
            methods = HttpMethod.POST,
            summary = "Add a label to a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void addTicketLabel(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.writableBoardId(ctx, partner);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        int labelId = pathInt(ctx, "labelId");
        var req = ctx.bodyAsClass(RemoteLabelActionRequest.class);
        boardService.addLabelToTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_ADDED,
                labelName(boardId, labelId),
                new MemberIdentity(partner.partnerStationId(), req.remoteMemberId()));
        guards.cacheDisplayName(partner, req.remoteMemberId(), req.displayName());
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    /**
     * Label removal is a POST to {@code .../remove} rather than a DELETE on the label path, because it
     * carries the acting member's identity in the request body and that identity is required for the
     * history entry. A second DELETE registration for the same handler was retired — it could never
     * have worked, since nothing sent the body it reads.
     */
    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/labels/{labelId}/remove",
            methods = HttpMethod.POST,
            summary = "Remove a label from a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void removeTicketLabel(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.writableBoardId(ctx, partner);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        int labelId = pathInt(ctx, "labelId");
        var req = ctx.bodyAsClass(RemoteLabelActionRequest.class);
        String name = labelName(boardId, labelId);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_REMOVED,
                name,
                new MemberIdentity(partner.partnerStationId(), req.remoteMemberId()));
        guards.cacheDisplayName(partner, req.remoteMemberId(), req.displayName());
        ctx.status(204);
    }

    /**
     * Resolves a ticket of the given board by its board-local number.
     */
    private int ticketIdOfNumber(int boardId, int ticketNumber) {
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(NotFoundResponse::new)
                .id();
    }

    /**
     * The display name of a board label for history entries, falling back to a placeholder when
     * the label no longer exists.
     */
    private String labelName(int boardId, int labelId) {
        return boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .map(BoardLabel::name)
                .orElse("?");
    }
}
