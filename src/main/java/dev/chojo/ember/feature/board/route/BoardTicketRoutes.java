/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardFieldValue;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicketHistory;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketKbLink;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.BoardTicketTransition;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.BoardWeblink;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
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

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class BoardTicketRoutes implements Routes {
    private final BoardTicketService ticketService;
    private final BoardService boardService;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public BoardTicketRoutes(
            BoardTicketService ticketService,
            BoardService boardService,
            FederatedBoardRepository federatedBoardRepository,
            EventFederationRepository eventFederationRepository,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory) {
        this.ticketService = ticketService;
        this.boardService = boardService;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
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

        // Links
        routes.get(p + "/{ticketNumber}/links", this::getLinks, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/links", this::createLink, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/links/{linkedId}", this::deleteLink, StationPermission.BOARD_USE);

        // Checklist
        routes.get(p + "/{ticketNumber}/checklist", this::getChecklist, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/checklist", this::addChecklistItem, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}/checklist/{itemId}", this::updateChecklistItem, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/checklist/{itemId}", this::deleteChecklistItem, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}/checklist/reorder", this::reorderChecklist, StationPermission.BOARD_USE);

        // Activity & Comments
        routes.get(p + "/{ticketNumber}/transitions", this::getTransitions, StationPermission.BOARD_USE);
        routes.get(p + "/{ticketNumber}/comments", this::getComments, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/comments", this::createComment, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}/comments/{commentId}", this::updateComment, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/comments/{commentId}", this::deleteComment, StationPermission.BOARD_USE);

        // Weblinks
        routes.get(p + "/{ticketNumber}/weblinks", this::getWeblinks, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/weblinks", this::addWeblink, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/weblinks/{weblinkId}", this::deleteWeblink, StationPermission.BOARD_USE);

        // Watchers
        routes.get(p + "/{ticketNumber}/watchers", this::getWatchers, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/watch", this::watchTicket, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/watch", this::unwatchTicket, StationPermission.BOARD_USE);

        // Field values
        routes.get(p + "/{ticketNumber}/fields", this::getFieldValues, StationPermission.BOARD_USE);
        routes.put(p + "/{ticketNumber}/fields/{fieldId}", this::setFieldValue, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/fields/{fieldId}", this::deleteFieldValue, StationPermission.BOARD_USE);

        // Attachments
        routes.get(p + "/{ticketNumber}/attachments", this::getAttachments, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/attachments", this::uploadAttachment, StationPermission.BOARD_USE);
        routes.get(
                p + "/{ticketNumber}/attachments/{attachmentId}/download",
                this::downloadAttachment,
                StationPermission.BOARD_USE);
        routes.delete(
                p + "/{ticketNumber}/attachments/{attachmentId}", this::deleteAttachment, StationPermission.BOARD_USE);

        // Labels on tickets
        routes.get(p + "/{ticketNumber}/labels", this::getTicketLabels, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/labels/{labelId}", this::addTicketLabel, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/labels/{labelId}", this::removeTicketLabel, StationPermission.BOARD_USE);

        // KB Links
        routes.get(p + "/{ticketNumber}/kb-links", this::getKbLinks, StationPermission.BOARD_USE);
        routes.post(p + "/{ticketNumber}/kb-links/{kbFileId}", this::addKbLink, StationPermission.BOARD_USE);
        routes.delete(p + "/{ticketNumber}/kb-links/{linkId}", this::removeKbLink, StationPermission.BOARD_USE);

        // History
        routes.get(p + "/{ticketNumber}/history", this::getHistory, StationPermission.BOARD_USE);

        // Activity
        routes.get(p + "/{ticketNumber}/activity", this::getActivity, StationPermission.BOARD_USE);
    }

    private int resolveBoardId(Context ctx, int stationId) {
        String boardKey = ctx.pathParam("boardKey");
        return boardService
                .findByShortKey(stationId, boardKey)
                .orElseThrow(() -> new NotFoundResponse("Board not found: " + boardKey))
                .id();
    }

    private int resolveTicketId(Context ctx, int boardId) {
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(() -> new NotFoundResponse("Ticket not found: " + ticketNumber))
                .id();
    }

    private void requireEditAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        boolean isManager = session.permissions().contains(StationPermission.BOARD_MANAGER);
        if (!boardService.canEdit(boardId, session.member().id(), isManager))
            throw new ForbiddenResponse("No edit access to this board");
    }

    private void requireViewAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        boolean isManager = session.permissions().contains(StationPermission.BOARD_MANAGER);
        if (!boardService.canView(boardId, session.member().id(), isManager))
            throw new ForbiddenResponse("No access to this board");
    }

    // -- Tickets --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        var req = ctx.bodyAsClass(CreateTicketRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        MemberIdentity assigneeIdentity = req.assignedMemberId() != null
                ? memberIdentityFactory.local(session.stationId(), req.assignedMemberId())
                : null;
        MemberIdentity creatorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        var ticket = ticketService.createTicket(
                boardId,
                req.laneId(),
                req.title(),
                req.description(),
                assigneeIdentity,
                req.priority() != null ? req.priority() : TicketPriority.MEDIUM,
                req.dueDate(),
                creatorIdentity);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(UpdateTicketRequest.class);
        MemberIdentity assigneeIdentity = req.assignedMemberId() != null
                ? memberIdentityFactory.local(session.stationId(), req.assignedMemberId())
                : null;
        MemberIdentity actorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                assigneeIdentity,
                req.priority(),
                req.dueDate(),
                actorIdentity);
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(MoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        ticketService.moveTicket(
                ticketId,
                ticket.laneId(),
                req.toLaneId(),
                req.position(),
                memberIdentityFactory.local(
                        session.stationId(), session.member().id()));
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(AssignRequest.class);
        MemberIdentity assigneeIdentity = req.assignedMemberId() != null
                ? memberIdentityFactory.local(session.stationId(), req.assignedMemberId())
                : null;
        ticketService.assignTicket(ticketId, assigneeIdentity, session.member().id());
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Links --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findLinks(ticketId));
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(LinkRequest.class);
        MemberIdentity actor = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        ticketService.linkTickets(ticketId, req.linkedTicketId(), req.linkType(), actor);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int linkedId = ctx.pathParamAsClass("linkedId", Integer.class).get();
        MemberIdentity actor = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        ticketService.unlinkTickets(ticketId, linkedId, actor);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Checklist --

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/checklist",
            methods = HttpMethod.GET,
            summary = "List checklist items for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardChecklistItem[].class)))
    private void getChecklist(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findChecklistItems(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/checklist",
            methods = HttpMethod.POST,
            summary = "Add a checklist item to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ChecklistItemRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardChecklistItem.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void addChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.addChecklistItem(
                        ticketId, req.title(), session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.PUT,
            summary = "Update a checklist item",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void updateChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        ticketService.updateChecklistItem(
                itemId,
                ticketId,
                req.title(),
                req.checked() != null && req.checked(),
                session.member().id());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a checklist item",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        ticketService.deleteChecklistItem(itemId, ticketId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/checklist/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder checklist items",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReorderChecklistRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void reorderChecklist(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(ReorderChecklistRequest.class);
        ticketService.reorderChecklistItems(ticketId, req.orderedIds());
        ctx.status(HttpStatus.OK);
    }

    // -- Activity & Comments --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findTransitions(ticketId).stream()
                .map(tr -> {
                    var resolved = memberNameResolver.resolveDisplay(tr.actor());
                    return BoardTicketTransitionResponse.from(tr, resolved.identity(), resolved.name());
                })
                .toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/comments",
            methods = HttpMethod.GET,
            summary = "List comments on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardComment[].class)))
    private void getComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findComments(ticketId).stream()
                .map(this::toCommentResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/comments",
            methods = HttpMethod.POST,
            summary = "Add a comment to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CommentRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardComment.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(CommentRequest.class);
        if (req.content() == null || req.content().isBlank()) throw new BadRequestResponse("content is required");
        MemberIdentity authorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        var comment = ticketService.createComment(ticketId, req.parentId(), authorIdentity, req.content());
        ctx.status(HttpStatus.CREATED).json(toCommentResponse(comment));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Update a comment on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CommentRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void updateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        ticketService.updateComment(commentId, req.content());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        ticketService.deleteComment(commentId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Weblinks --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findWeblinks(ticketId));
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int weblinkId = ctx.pathParamAsClass("weblinkId", Integer.class).get();
        ticketService.deleteWeblink(weblinkId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Attachments --

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments",
            methods = HttpMethod.GET,
            summary = "List attachments on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketAttachment[].class)))
    private void getAttachments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findAttachments(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments",
            methods = HttpMethod.POST,
            summary = "Upload an attachment to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicketAttachment.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("No file uploaded");
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var att = ticketService.uploadAttachment(
                    session.stationId(),
                    ticketId,
                    file.filename(),
                    file.contentType(),
                    data,
                    memberIdentityFactory.local(
                            session.stationId(), session.member().id()));
            ctx.status(HttpStatus.CREATED).json(att);
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read uploaded file");
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments/{attachmentId}/download",
            methods = HttpMethod.GET,
            summary = "Download an attachment",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void downloadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int attachmentId = ctx.pathParamAsClass("attachmentId", Integer.class).get();
        var att = ticketService.findAttachmentById(attachmentId).orElseThrow(NotFoundResponse::new);
        var path = ticketService.getAttachmentPath(session.stationId(), att);
        if (!Files.exists(path)) throw new NotFoundResponse();
        ctx.contentType(SafeInlineMime.safeContentType(att.contentType()));
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, att.originalName()));
        try {
            ctx.result(Files.newInputStream(path));
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read file");
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/attachments/{attachmentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete an attachment",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int attachmentId = ctx.pathParamAsClass("attachmentId", Integer.class).get();
        if (ticketService.deleteAttachment(session.stationId(), attachmentId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Watchers --

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/watchers",
            methods = HttpMethod.GET,
            summary = "List watchers of a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void getWatchers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findWatchers(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.POST,
            summary = "Watch a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "201"))
    private void watchTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ticketService.watchTicket(ticketId, session.member().id());
        ctx.status(HttpStatus.CREATED);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.DELETE,
            summary = "Unwatch a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void unwatchTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ticketService.unwatchTicket(ticketId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Field values --

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/fields",
            methods = HttpMethod.GET,
            summary = "List field values for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketFieldValue[].class)))
    private void getFieldValues(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findFieldValues(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/fields/{fieldId}",
            methods = HttpMethod.PUT,
            summary = "Set a field value on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFieldValue(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var field = boardService.findFields(boardId).stream()
                .filter(f -> f.id() == fieldId)
                .findFirst()
                .orElseThrow(() -> new NotFoundResponse("Field not found"));
        var value = BoardFieldValue.parse(field.fieldType(), ctx.body());
        if (value == null) throw new BadRequestResponse("Invalid value for field type " + field.fieldType());
        ticketService.setFieldValue(ticketId, fieldId, value);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.FIELD_CHANGED,
                "Feld #" + fieldId,
                memberIdentityFactory.local(
                        session.stationId(), session.member().id()));
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/tickets/{ticketNumber}/fields/{fieldId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a field value from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteFieldValue(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        ticketService.deleteFieldValue(ticketId, fieldId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Ticket Labels --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(boardService.findLabelsForTicket(ticketId));
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.addLabelToTicket(ticketId, labelId);
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_ADDED,
                label != null ? label.name() : "?",
                memberIdentityFactory.local(
                        session.stationId(), session.member().id()));
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_REMOVED,
                label != null ? label.name() : "?",
                memberIdentityFactory.local(
                        session.stationId(), session.member().id()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- KB Links --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findKbLinks(ticketId));
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        int kbFileId = ctx.pathParamAsClass("kbFileId", Integer.class).get();
        var link = ticketService.addKbLink(ticketId, kbFileId);
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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        int linkId = ctx.pathParamAsClass("linkId", Integer.class).get();
        ticketService.removeKbLink(linkId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- History --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findHistory(ticketId).stream()
                .map(h -> {
                    var resolved = memberNameResolver.resolveDisplay(h.actor());
                    return BoardTicketHistoryResponse.from(h, resolved.identity(), resolved.name());
                })
                .toList());
    }

    // -- Activity --

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
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        int ticketId = resolveTicketId(ctx, boardId);
        ctx.json(ticketService.findActivity(ticketId));
    }

    // -- Comment enrichment --

    private BoardCommentResponse toCommentResponse(BoardComment comment) {
        if (comment.deleted()) {
            return new BoardCommentResponse(
                    comment.id(), comment.ticketId(), comment.parentId(), null, "", true, comment.createdAt(), null);
        }
        var enriched = memberNameResolver.enrichDisplay(comment.author());
        return new BoardCommentResponse(
                comment.id(),
                comment.ticketId(),
                comment.parentId(),
                enriched,
                comment.content(),
                false,
                comment.createdAt(),
                comment.updatedAt());
    }

    public record BoardCommentResponse(
            int id,
            int ticketId,
            Integer parentId,
            MemberIdentity author,
            String content,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {}

    // -- Request records --

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

    public record LinkRequest(int linkedTicketId, LinkType linkType) {}

    public record ChecklistItemRequest(String title, Boolean checked) {}

    public record ReorderChecklistRequest(List<Integer> orderedIds) {}

    @OpenApiName("BoardTicketAssignRequest")
    public record AssignRequest(Integer assignedMemberId) {}

    @OpenApiName("BoardTicketCommentRequest")
    public record CommentRequest(Integer parentId, String content) {}

    public record WeblinkRequest(String url, String title) {}
}
