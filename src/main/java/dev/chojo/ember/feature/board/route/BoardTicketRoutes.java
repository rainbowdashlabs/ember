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
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.service.BoardTicketService;
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

import java.time.LocalDate;
import java.util.List;

/**
 * The ticket collection of a local board and the lifecycle of a single ticket: listing, search,
 * creation, update, deletion, lane moves, assignment and ordering. Everything hanging off a
 * ticket lives in {@link BoardTicketDetailRoutes}, {@link BoardTicketLinkRoutes},
 * {@link BoardTicketAttachmentRoutes} and {@link BoardTicketHistoryRoutes}.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class BoardTicketRoutes implements Routes {

    private final BoardTicketService ticketService;
    private final BoardRouteGuards guards;

    @Inject
    public BoardTicketRoutes(BoardTicketService ticketService, BoardRouteGuards guards) {
        this.ticketService = ticketService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/boards/{boardKey}/tickets";
        routes.get(p, this::listTickets, StationPermission.BOARD_USE);
        routes.get(p + "/search", this::searchTickets, StationPermission.BOARD_USE);
        routes.post(p, this::createTicket, StationPermission.BOARD_USE);
        routes.get(p + "/{ticketNumber}", this::getTicket, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}", this::updateTicket, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}", this::deleteTicket, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}/move", this::moveTicket, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}/assign", this::assignTicket, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}/reorder", this::reorderTickets, StationPermission.BOARD_USE);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/search",
            methods = HttpMethod.GET,
            summary = "Search tickets on a board",
            tags = {"Board Tickets"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            queryParams = @OpenApiParam(name = "q", type = String.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TicketSummary[].class)))
    private void searchTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        guards.requireViewAccess(boardId, session);
        String q = ctx.queryParam("q");
        var tickets =
                (q == null || q.isBlank()) ? ticketService.findByBoard(boardId) : ticketService.search(boardId, q);
        ctx.json(tickets.stream().map(TicketSummary::of).toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets",
            methods = HttpMethod.GET,
            summary = "List all tickets on a board",
            tags = {"Board Tickets"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TicketSummary[].class)))
    private void listTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        guards.requireViewAccess(boardId, session);
        ctx.json(ticketService.findByBoard(boardId).stream()
                .map(TicketSummary::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets",
            methods = HttpMethod.POST,
            summary = "Create a new ticket on a board",
            tags = {"Board Tickets"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        guards.requireEditAccess(boardId, session);
        var req = ctx.bodyAsClass(CreateTicketRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        var ticket = ticketService.createTicket(
                boardId,
                req.laneId(),
                req.title(),
                req.description(),
                guards.member(session, req.assignedMemberId()),
                req.priority() != null ? req.priority() : TicketPriority.MEDIUM,
                req.dueDate(),
                guards.actor(session));
        ctx.status(HttpStatus.CREATED).json(ticket);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.GET,
            summary = "Get a ticket by ID",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        respondWithTicket(ctx, guards.viewableTicketId(ctx, session));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.PUT,
            summary = "Update a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(UpdateTicketRequest.class);
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                guards.member(session, req.assignedMemberId()),
                req.priority(),
                req.dueDate(),
                guards.actor(session));
        respondWithTicket(ctx, ticketId);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.DELETE,
            summary = "Delete a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        if (ticketService.deleteTicket(ticketId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/move",
            methods = HttpMethod.PUT,
            summary = "Move a ticket to a different lane",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MoveTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(MoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        ticketService.moveTicket(ticketId, ticket.laneId(), req.toLaneId(), req.position(), guards.actor(session));
        respondWithTicket(ctx, ticketId);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder tickets within a lane",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReorderRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void reorderTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        guards.requireEditAccess(boardId, session);
        var req = ctx.bodyAsClass(ReorderRequest.class);
        ticketService.reorderTickets(req.laneId(), req.orderedIds());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/assign",
            methods = HttpMethod.PUT,
            summary = "Assign a ticket to a member",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void assignTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(AssignRequest.class);
        ticketService.assignTicket(
                ticketId,
                guards.member(session, req.assignedMemberId()),
                session.member().id());
        respondWithTicket(ctx, ticketId);
    }

    /**
     * Answers with the current state of a ticket, or 404 when it disappeared meanwhile.
     */
    private void respondWithTicket(Context ctx, int ticketId) {
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    public record CreateTicketRequest(
            int laneId,
            String title,
            String description,
            Integer assignedMemberId,
            TicketPriority priority,
            LocalDate dueDate) {}

    public record UpdateTicketRequest(
            String title, String description, Integer assignedMemberId, TicketPriority priority, LocalDate dueDate) {}

    public record MoveTicketRequest(int toLaneId, int position) {}

    public record ReorderRequest(int laneId, List<Integer> orderedIds) {}

    @OpenApiName("BoardTicketAssignRequest")
    public record AssignRequest(Integer assignedMemberId) {}
}
