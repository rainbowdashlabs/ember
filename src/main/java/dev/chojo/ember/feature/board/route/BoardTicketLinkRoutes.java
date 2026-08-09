/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.entity.BoardTicketKbLink;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.BoardWeblink;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Everything a local board ticket points at: links to other tickets, plain weblinks, knowledge
 * base links and the labels applied to the ticket.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class BoardTicketLinkRoutes implements Routes {

    private final BoardTicketService ticketService;
    private final BoardService boardService;
    private final BoardRouteGuards guards;

    @Inject
    public BoardTicketLinkRoutes(BoardTicketService ticketService, BoardService boardService, BoardRouteGuards guards) {
        this.ticketService = ticketService;
        this.boardService = boardService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/boards/{boardKey}/tickets/{ticketNumber}";

        routes.get(p + "/links", this::getLinks, StationPermission.BOARD_USE);
        routes.post(p + "/links", this::createLink, StationPermission.BOARD_USE);
        routes.delete(p + "/links/{linkedId}", this::deleteLink, StationPermission.BOARD_USE);

        routes.get(p + "/weblinks", this::getWeblinks, StationPermission.BOARD_USE);
        routes.post(p + "/weblinks", this::addWeblink, StationPermission.BOARD_USE);
        routes.delete(p + "/weblinks/{weblinkId}", this::deleteWeblink, StationPermission.BOARD_USE);

        routes.get(p + "/labels", this::getTicketLabels, StationPermission.BOARD_USE);
        routes.post(p + "/labels/{labelId}", this::addTicketLabel, StationPermission.BOARD_USE);
        routes.delete(p + "/labels/{labelId}", this::removeTicketLabel, StationPermission.BOARD_USE);

        routes.get(p + "/kb-links", this::getKbLinks, StationPermission.BOARD_USE);
        routes.post(p + "/kb-links/{kbFileId}", this::addKbLink, StationPermission.BOARD_USE);
        routes.delete(p + "/kb-links/{linkId}", this::removeKbLink, StationPermission.BOARD_USE);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/links",
            methods = HttpMethod.GET,
            summary = "List links for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketLink[].class)))
    private void getLinks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(ticketService.findLinks(guards.viewableTicketId(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/links",
            methods = HttpMethod.POST,
            summary = "Create a link between tickets",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LinkRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicketLink[].class)))
    private void createLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(LinkRequest.class);
        ticketService.linkTickets(ticketId, req.linkedTicketId(), req.linkType(), guards.actor(session));
        ctx.status(HttpStatus.CREATED).json(ticketService.findLinks(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/links/{linkedId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a link between tickets",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "linkedId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        ticketService.unlinkTickets(ticketId, pathInt(ctx, "linkedId"), guards.actor(session));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/weblinks",
            methods = HttpMethod.GET,
            summary = "List weblinks on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardWeblink[].class)))
    private void getWeblinks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(ticketService.findWeblinks(guards.viewableTicketId(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/weblinks",
            methods = HttpMethod.POST,
            summary = "Add a weblink to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WeblinkRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardWeblink.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void addWeblink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(WeblinkRequest.class);
        if (req.url() == null || req.url().isBlank()) throw new BadRequestResponse("url is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.addWeblink(ticketId, req.url(), req.title() != null ? req.title() : ""));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/weblinks/{weblinkId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a weblink from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "weblinkId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteWeblink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        int weblinkId = pathInt(ctx, "weblinkId");
        if (ticketService.findWeblinks(ticketId).stream().noneMatch(w -> w.id() == weblinkId)) {
            throw new NotFoundResponse();
        }
        ticketService.deleteWeblink(weblinkId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/labels",
            methods = HttpMethod.GET,
            summary = "List labels on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardLabel[].class)))
    private void getTicketLabels(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(boardService.findLabelsForTicket(guards.viewableTicketId(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
            methods = HttpMethod.POST,
            summary = "Add a label to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardLabel[].class)))
    private void addTicketLabel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        guards.requireEditAccess(boardId, session);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        int labelId = pathInt(ctx, "labelId");
        boardService.addLabelToTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId, BoardTicketHistoryAction.LABEL_ADDED, labelName(boardId, labelId), guards.actor(session));
        ctx.status(HttpStatus.CREATED).json(boardService.findLabelsForTicket(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a label from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void removeTicketLabel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        guards.requireEditAccess(boardId, session);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        int labelId = pathInt(ctx, "labelId");
        String name = labelName(boardId, labelId);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(ticketId, BoardTicketHistoryAction.LABEL_REMOVED, name, guards.actor(session));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/kb-links",
            methods = HttpMethod.GET,
            summary = "List knowledge base links on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketKbLink[].class)))
    private void getKbLinks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(ticketService.findKbLinks(guards.viewableTicketId(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/kb-links/{kbFileId}",
            methods = HttpMethod.POST,
            summary = "Link a knowledge base file to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "kbFileId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicketKbLink.class)),
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketKbLink[].class))
            })
    private void addKbLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var link = ticketService.addKbLink(ticketId, pathInt(ctx, "kbFileId"));
        if (link != null) ctx.status(HttpStatus.CREATED).json(link);
        else ctx.status(HttpStatus.OK).json(ticketService.findKbLinks(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/kb-links/{linkId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a knowledge base link from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "linkId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void removeKbLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        guards.requireEditAccess(guards.resolveBoardId(ctx, session.stationId()), session);
        ticketService.removeKbLink(pathInt(ctx, "linkId"));
        ctx.status(HttpStatus.NO_CONTENT);
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

    public record LinkRequest(int linkedTicketId, LinkType linkType) {}

    public record WeblinkRequest(String url, String title) {}
}
