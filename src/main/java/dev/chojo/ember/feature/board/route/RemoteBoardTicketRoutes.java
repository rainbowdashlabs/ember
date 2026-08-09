/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteCreateTicketRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteMoveTicketRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteReorderRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteUpdateTicketRequest;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import io.javalin.http.Context;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Server-to-server ticket endpoints on a shared board: the ticket list, search, the lifecycle of
 * a single ticket and its read-only audit trail. Requests carry an RSA-signed envelope instead of
 * a user session.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class RemoteBoardTicketRoutes implements Routes {

    private static final String TICKETS = RemoteBoardRoutes.TICKETS_PATH;

    public static final FederationEndpoint LIST_TICKETS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKETS, TicketSummary.class);
    public static final FederationEndpoint SEARCH_TICKETS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKETS + "/search", TicketSummary.class);
    public static final FederationEndpoint CREATE_TICKET = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE, TICKETS, RemoteCreateTicketRequest.class, BoardTicket.class);
    public static final FederationEndpoint REORDER_TICKETS = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE, TICKETS + "/reorder", RemoteReorderRequest.class, Void.class);
    public static final FederationEndpoint GET_TICKET =
            FederationEndpoint.get(FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}", BoardTicket.class);
    public static final FederationEndpoint UPDATE_TICKET = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE,
            TICKETS + "/{ticketNumber}",
            RemoteUpdateTicketRequest.class,
            BoardTicket.class);
    public static final FederationEndpoint DELETE_TICKET = FederationEndpoint.delete(
            FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}", Void.class, Void.class);
    public static final FederationEndpoint MOVE_TICKET = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE,
            TICKETS + "/{ticketNumber}/move",
            RemoteMoveTicketRequest.class,
            BoardTicket.class);
    public static final FederationEndpoint GET_TRANSITIONS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE,
            TICKETS + "/{ticketNumber}/transitions",
            BoardTicketTransitionResponse.class);
    public static final FederationEndpoint GET_HISTORY = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}/history", BoardTicketHistoryResponse.class);
    public static final FederationEndpoint GET_ATTACHMENTS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}/attachments", BoardTicketAttachment.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            LIST_TICKETS,
            SEARCH_TICKETS,
            CREATE_TICKET,
            REORDER_TICKETS,
            GET_TICKET,
            UPDATE_TICKET,
            DELETE_TICKET,
            MOVE_TICKET,
            GET_TRANSITIONS,
            GET_HISTORY,
            GET_ATTACHMENTS);

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;
    private final RemoteBoardGuards guards;

    @Inject
    public RemoteBoardTicketRoutes(
            BoardService boardService,
            BoardTicketService ticketService,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory,
            RemoteBoardGuards guards) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(LIST_TICKETS, this::listTickets)
                        .handle(SEARCH_TICKETS, this::searchTickets)
                        .handle(CREATE_TICKET, this::createTicket)
                        .handle(REORDER_TICKETS, this::reorderTickets)
                        .handle(GET_TICKET, this::getTicket)
                        .handle(UPDATE_TICKET, this::updateTicket)
                        .handle(DELETE_TICKET, this::deleteTicket)
                        .handle(MOVE_TICKET, this::moveTicket)
                        .handle(GET_TRANSITIONS, this::getTransitions)
                        .handle(GET_HISTORY, this::getHistory)
                        .handle(GET_ATTACHMENTS, this::getAttachments));
    }

    private void listTickets(Context ctx) {
        var partner = guards.requirePartner(ctx);
        ctx.json(summaries(ticketService.findByBoard(guards.viewableBoardId(ctx, partner))));
    }

    private void searchTickets(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.viewableBoardId(ctx, partner);
        String q = ctx.queryParam("q");
        ctx.json(summaries(
                (q == null || q.isBlank()) ? ticketService.findByBoard(boardId) : ticketService.search(boardId, q)));
    }

    private void getTicket(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void getTransitions(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(ticketService.findTransitions(ticketId).stream()
                .map(transition -> {
                    var resolved = memberNameResolver.resolveDisplay(transition.actor());
                    return BoardTicketTransitionResponse.from(transition, resolved.identity(), resolved.name());
                })
                .toList());
    }

    private void getHistory(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(ticketService.findHistory(ticketId).stream()
                .map(entry -> {
                    var resolved = memberNameResolver.resolveDisplay(entry.actor());
                    return BoardTicketHistoryResponse.from(entry, resolved.identity(), resolved.name());
                })
                .toList());
    }

    private void getAttachments(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.viewableTicketId(ctx, partner);
        ctx.json(ticketService.findAttachments(ticketId));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets",
            methods = HttpMethod.POST,
            summary = "Create a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteCreateTicketRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void createTicket(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.writableBoardId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteCreateTicketRequest.class);
        boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        int laneId = req.laneId() != null
                ? req.laneId()
                : boardService.findLanes(boardId).getFirst().id();
        var ticket = ticketService.createTicket(
                boardId,
                laneId,
                req.title(),
                req.description(),
                null,
                req.priority() != null ? TicketPriority.valueOf(req.priority()) : TicketPriority.MEDIUM,
                req.dueDate() != null ? LocalDate.parse(req.dueDate()) : null,
                new MemberIdentity(partner.partnerStationId(), req.remoteMemberId()));
        ctx.json(ticket);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.PUT,
            summary = "Update a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteUpdateTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateTicket(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.writableBoardId(ctx, partner);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteUpdateTicketRequest.class);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        MemberIdentity assigneeIdentity = req.assignedMemberId() != null
                ? memberIdentityFactory.local(board.stationId(), req.assignedMemberId())
                : null;
        guards.cacheDisplayName(partner, req.remoteMemberUid(), req.displayName());
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                assigneeIdentity,
                req.priority() != null ? TicketPriority.valueOf(req.priority()) : null,
                req.dueDate() != null ? LocalDate.parse(req.dueDate()) : null,
                guards.remoteActor(partner, req.remoteMemberUid()));
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.DELETE,
            summary = "Delete a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteTicket(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        ticketService.deleteTicket(ticketId);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/move",
            methods = HttpMethod.PUT,
            summary = "Move a ticket to a different lane on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteMoveTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveTicket(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int ticketId = guards.writableTicketId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteMoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        guards.cacheDisplayName(partner, req.remoteMemberUid(), req.displayName());
        ticketService.moveTicket(
                ticketId,
                ticket.laneId(),
                req.toLaneId(),
                req.position(),
                guards.remoteActor(partner, req.remoteMemberUid()));
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder tickets in a lane on a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteReorderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorderTickets(Context ctx) {
        var partner = guards.requirePartner(ctx);
        guards.writableBoardId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteReorderRequest.class);
        ticketService.reorderTickets(req.laneId(), req.orderedIds());
        ctx.status(204);
    }

    /**
     * Maps tickets to summaries and fills in the display name of a federated assignee.
     */
    private List<TicketSummary> summaries(List<BoardTicket> tickets) {
        return tickets.stream()
                .map(TicketSummary::of)
                .map(t -> t.assignee() != null ? t.withAssignee(memberNameResolver.enrichDisplay(t.assignee())) : t)
                .toList();
    }
}
