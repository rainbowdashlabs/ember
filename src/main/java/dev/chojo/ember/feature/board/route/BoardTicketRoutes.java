/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardFieldValue;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicketHistory;
import dev.chojo.ember.feature.board.entity.BoardTicketKbLink;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.BoardTicketTransition;
import dev.chojo.ember.feature.board.entity.BoardWeblink;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
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
import java.time.LocalDate;
import java.util.List;

@Singleton
public class BoardTicketRoutes implements Routes {
    private final BoardTicketService ticketService;
    private final BoardService boardService;

    @Inject
    public BoardTicketRoutes(BoardTicketService ticketService, BoardService boardService) {
        this.ticketService = ticketService;
        this.boardService = boardService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/boards/{boardId}/tickets";
        routes.get(p, this::listTickets, Roles.USER);
        routes.get(p + "/search", this::searchTickets, Roles.USER);
        routes.post(p, this::createTicket, Roles.USER);
        routes.get(p + "/{ticketId}", this::getTicket, Roles.USER);
        routes.put(p + "/{ticketId}", this::updateTicket, Roles.USER);
        routes.delete(p + "/{ticketId}", this::deleteTicket, Roles.USER);
        routes.put(p + "/{ticketId}/move", this::moveTicket, Roles.USER);
        routes.put(p + "/{ticketId}/assign", this::assignTicket, Roles.USER);
        routes.put(p + "/{ticketId}/reorder", this::reorderTickets, Roles.USER);

        // Links
        routes.get(p + "/{ticketId}/links", this::getLinks, Roles.USER);
        routes.post(p + "/{ticketId}/links", this::createLink, Roles.USER);
        routes.delete(p + "/{ticketId}/links/{linkedId}", this::deleteLink, Roles.USER);

        // Checklist
        routes.get(p + "/{ticketId}/checklist", this::getChecklist, Roles.USER);
        routes.post(p + "/{ticketId}/checklist", this::addChecklistItem, Roles.USER);
        routes.put(p + "/{ticketId}/checklist/{itemId}", this::updateChecklistItem, Roles.USER);
        routes.delete(p + "/{ticketId}/checklist/{itemId}", this::deleteChecklistItem, Roles.USER);
        routes.put(p + "/{ticketId}/checklist/reorder", this::reorderChecklist, Roles.USER);

        // Activity & Comments
        routes.get(p + "/{ticketId}/transitions", this::getTransitions, Roles.USER);
        routes.get(p + "/{ticketId}/comments", this::getComments, Roles.USER);
        routes.post(p + "/{ticketId}/comments", this::createComment, Roles.USER);
        routes.put(p + "/{ticketId}/comments/{commentId}", this::updateComment, Roles.USER);
        routes.delete(p + "/{ticketId}/comments/{commentId}", this::deleteComment, Roles.USER);

        // Weblinks
        routes.get(p + "/{ticketId}/weblinks", this::getWeblinks, Roles.USER);
        routes.post(p + "/{ticketId}/weblinks", this::addWeblink, Roles.USER);
        routes.delete(p + "/{ticketId}/weblinks/{weblinkId}", this::deleteWeblink, Roles.USER);

        // Watchers
        routes.get(p + "/{ticketId}/watchers", this::getWatchers, Roles.USER);
        routes.post(p + "/{ticketId}/watch", this::watchTicket, Roles.USER);
        routes.delete(p + "/{ticketId}/watch", this::unwatchTicket, Roles.USER);

        // Field values
        routes.get(p + "/{ticketId}/fields", this::getFieldValues, Roles.USER);
        routes.put(p + "/{ticketId}/fields/{fieldId}", this::setFieldValue, Roles.USER);
        routes.delete(p + "/{ticketId}/fields/{fieldId}", this::deleteFieldValue, Roles.USER);

        // Attachments
        routes.get(p + "/{ticketId}/attachments", this::getAttachments, Roles.USER);
        routes.post(p + "/{ticketId}/attachments", this::uploadAttachment, Roles.USER);
        routes.get(p + "/{ticketId}/attachments/{attachmentId}/download", this::downloadAttachment, Roles.USER);
        routes.delete(p + "/{ticketId}/attachments/{attachmentId}", this::deleteAttachment, Roles.USER);

        // Labels on tickets
        routes.get(p + "/{ticketId}/labels", this::getTicketLabels, Roles.USER);
        routes.post(p + "/{ticketId}/labels/{labelId}", this::addTicketLabel, Roles.USER);
        routes.delete(p + "/{ticketId}/labels/{labelId}", this::removeTicketLabel, Roles.USER);

        // KB Links
        routes.get(p + "/{ticketId}/kb-links", this::getKbLinks, Roles.USER);
        routes.post(p + "/{ticketId}/kb-links/{kbFileId}", this::addKbLink, Roles.USER);
        routes.delete(p + "/{ticketId}/kb-links/{linkId}", this::removeKbLink, Roles.USER);

        // History
        routes.get(p + "/{ticketId}/history", this::getHistory, Roles.USER);

        // Activity
        routes.get(p + "/{ticketId}/activity", this::getActivity, Roles.USER);
    }

    private void requireEditAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        if (!boardService.canEdit(boardId, session.member().id()))
            throw new ForbiddenResponse("No edit access to this board");
    }

    private void requireViewAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        if (!boardService.canView(boardId, session.member().id()))
            throw new ForbiddenResponse("No access to this board");
    }

    // -- Tickets --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/search",
            methods = HttpMethod.GET,
            summary = "Search tickets on a board",
            tags = {"Board Tickets"},
            pathParams = @OpenApiParam(name = "boardId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "q", type = String.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TicketSummary[].class)))
    private void searchTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        String q = ctx.queryParam("q");
        var tickets =
                (q == null || q.isBlank()) ? ticketService.findByBoard(boardId) : ticketService.search(boardId, q);
        ctx.json(tickets.stream().map(TicketSummary::of).toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets",
            methods = HttpMethod.GET,
            summary = "List all tickets on a board",
            tags = {"Board Tickets"},
            pathParams = @OpenApiParam(name = "boardId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TicketSummary[].class)))
    private void listTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        ctx.json(ticketService.findByBoard(boardId).stream()
                .map(TicketSummary::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets",
            methods = HttpMethod.POST,
            summary = "Create a new ticket on a board",
            tags = {"Board Tickets"},
            pathParams = @OpenApiParam(name = "boardId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        var req = ctx.bodyAsClass(CreateTicketRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        var ticket = ticketService.createTicket(
                boardId,
                req.laneId(),
                req.title(),
                req.description(),
                req.assignedMemberId(),
                req.priority() != null ? req.priority() : TicketPriority.MEDIUM,
                req.dueDate(),
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(ticket);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}",
            methods = HttpMethod.GET,
            summary = "Get a ticket by ID",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}",
            methods = HttpMethod.PUT,
            summary = "Update a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(UpdateTicketRequest.class);
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                req.assignedMemberId(),
                req.priority(),
                req.dueDate(),
                session.member().id());
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        if (ticketService.deleteTicket(ticketId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/move",
            methods = HttpMethod.PUT,
            summary = "Move a ticket to a different lane",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MoveTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(MoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        ticketService.moveTicket(
                ticketId,
                ticket.laneId(),
                req.toLaneId(),
                req.position(),
                session.member().id());
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder tickets within a lane",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReorderRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void reorderTickets(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        var req = ctx.bodyAsClass(ReorderRequest.class);
        ticketService.reorderTickets(req.laneId(), req.orderedIds());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/assign",
            methods = HttpMethod.PUT,
            summary = "Assign a ticket to a member",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicket.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void assignTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(AssignRequest.class);
        ticketService.assignTicket(
                ticketId, req.assignedMemberId(), session.member().id());
        ticketService.findById(ticketId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Links --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/links",
            methods = HttpMethod.GET,
            summary = "List links for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketLink[].class)))
    private void getLinks(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findLinks(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/links",
            methods = HttpMethod.POST,
            summary = "Create a link between tickets",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LinkRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicketLink[].class)))
    private void createLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(LinkRequest.class);
        ticketService.linkTickets(ticketId, req.linkedTicketId(), req.linkType());
        ctx.status(HttpStatus.CREATED).json(ticketService.findLinks(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/links/{linkedId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a link between tickets",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "linkedId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int linkedId = ctx.pathParamAsClass("linkedId", Integer.class).get();
        ticketService.unlinkTickets(ticketId, linkedId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Checklist --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/checklist",
            methods = HttpMethod.GET,
            summary = "List checklist items for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardChecklistItem[].class)))
    private void getChecklist(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findChecklistItems(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/checklist",
            methods = HttpMethod.POST,
            summary = "Add a checklist item to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ChecklistItemRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardChecklistItem.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void addChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.addChecklistItem(
                        ticketId, req.title(), session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/checklist/{itemId}",
            methods = HttpMethod.PUT,
            summary = "Update a checklist item",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void updateChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
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
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/checklist/{itemId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a checklist item",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteChecklistItem(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        ticketService.deleteChecklistItem(itemId, ticketId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/checklist/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder checklist items",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReorderChecklistRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void reorderChecklist(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(ReorderChecklistRequest.class);
        ticketService.reorderChecklistItems(ticketId, req.orderedIds());
        ctx.status(HttpStatus.OK);
    }

    // -- Activity & Comments --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/transitions",
            methods = HttpMethod.GET,
            summary = "List lane transitions for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketTransition[].class)))
    private void getTransitions(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findTransitions(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/comments",
            methods = HttpMethod.GET,
            summary = "List comments on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardComment[].class)))
    private void getComments(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findComments(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/comments",
            methods = HttpMethod.POST,
            summary = "Add a comment to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CommentRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardComment.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        if (req.content() == null || req.content().isBlank()) throw new BadRequestResponse("content is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.createComment(
                        ticketId, req.parentId(), session.member().id(), req.content()));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Update a comment on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CommentRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void updateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        ticketService.updateComment(commentId, req.content());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        ticketService.deleteComment(commentId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Weblinks --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/weblinks",
            methods = HttpMethod.GET,
            summary = "List weblinks on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardWeblink[].class)))
    private void getWeblinks(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findWeblinks(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/weblinks",
            methods = HttpMethod.POST,
            summary = "Add a weblink to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WeblinkRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardWeblink.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void addWeblink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(WeblinkRequest.class);
        if (req.url() == null || req.url().isBlank()) throw new BadRequestResponse("url is required");
        ctx.status(HttpStatus.CREATED)
                .json(ticketService.addWeblink(ticketId, req.url(), req.title() != null ? req.title() : ""));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/weblinks/{weblinkId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a weblink from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "weblinkId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteWeblink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int weblinkId = ctx.pathParamAsClass("weblinkId", Integer.class).get();
        ticketService.deleteWeblink(weblinkId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Attachments --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/attachments",
            methods = HttpMethod.GET,
            summary = "List attachments on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketAttachment[].class)))
    private void getAttachments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findAttachments(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/attachments",
            methods = HttpMethod.POST,
            summary = "Upload an attachment to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicketAttachment.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("No file uploaded");
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var att = ticketService.uploadAttachment(
                    ticketId,
                    file.filename(),
                    file.contentType(),
                    data,
                    session.member().id());
            ctx.status(HttpStatus.CREATED).json(att);
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read uploaded file");
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/attachments/{attachmentId}/download",
            methods = HttpMethod.GET,
            summary = "Download an attachment",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void downloadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int attachmentId = ctx.pathParamAsClass("attachmentId", Integer.class).get();
        var att = ticketService.findAttachmentById(attachmentId).orElseThrow(NotFoundResponse::new);
        var path = ticketService.getAttachmentPath(att);
        if (!Files.exists(path)) throw new NotFoundResponse();
        ctx.contentType(att.contentType());
        ctx.header("Content-Disposition", "attachment; filename=\"" + att.originalName() + "\"");
        try {
            ctx.result(Files.newInputStream(path));
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read file");
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/attachments/{attachmentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete an attachment",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int attachmentId = ctx.pathParamAsClass("attachmentId", Integer.class).get();
        if (ticketService.deleteAttachment(attachmentId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Watchers --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/watchers",
            methods = HttpMethod.GET,
            summary = "List watchers of a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void getWatchers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findWatchers(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/watch",
            methods = HttpMethod.POST,
            summary = "Watch a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "201"))
    private void watchTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.watchTicket(ticketId, session.member().id());
        ctx.status(HttpStatus.CREATED);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/watch",
            methods = HttpMethod.DELETE,
            summary = "Unwatch a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void unwatchTicket(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.unwatchTicket(ticketId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Field values --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/fields",
            methods = HttpMethod.GET,
            summary = "List field values for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketFieldValue[].class)))
    private void getFieldValues(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findFieldValues(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/fields/{fieldId}",
            methods = HttpMethod.PUT,
            summary = "Set a field value on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFieldValue(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var field = boardService.findFields(boardId).stream()
                .filter(f -> f.id() == fieldId)
                .findFirst()
                .orElseThrow(() -> new NotFoundResponse("Field not found"));
        var value = BoardFieldValue.parse(field.fieldType(), ctx.body());
        if (value == null) throw new BadRequestResponse("Invalid value for field type " + field.fieldType());
        ticketService.setFieldValue(ticketId, fieldId, value);
        ticketService.logHistory(
                ticketId, "FIELD_CHANGED", "Feld #" + fieldId, session.member().id());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/fields/{fieldId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a field value from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteFieldValue(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        ticketService.deleteFieldValue(ticketId, fieldId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Ticket Labels --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/labels",
            methods = HttpMethod.GET,
            summary = "List labels on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardLabel[].class)))
    private void getTicketLabels(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/labels/{labelId}",
            methods = HttpMethod.POST,
            summary = "Add a label to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardLabel[].class)))
    private void addTicketLabel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.addLabelToTicket(ticketId, labelId);
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        ticketService.logHistory(
                ticketId,
                "LABEL_ADDED",
                label != null ? label.name() : "?",
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(boardService.findLabelsForTicket(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/labels/{labelId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a label from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void removeTicketLabel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                "LABEL_REMOVED",
                label != null ? label.name() : "?",
                session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- KB Links --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/kb-links",
            methods = HttpMethod.GET,
            summary = "List knowledge base links on a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketKbLink[].class)))
    private void getKbLinks(Context ctx) {
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findKbLinks(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/kb-links/{kbFileId}",
            methods = HttpMethod.POST,
            summary = "Link a knowledge base file to a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "kbFileId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = BoardTicketKbLink.class)),
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketKbLink[].class))
            })
    private void addKbLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int kbFileId = ctx.pathParamAsClass("kbFileId", Integer.class).get();
        var link = ticketService.addKbLink(ticketId, kbFileId);
        if (link != null) ctx.status(HttpStatus.CREATED).json(link);
        else ctx.status(HttpStatus.OK).json(ticketService.findKbLinks(ticketId));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/kb-links/{linkId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a knowledge base link from a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true),
                @OpenApiParam(name = "linkId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void removeKbLink(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireEditAccess(boardId, session);
        int linkId = ctx.pathParamAsClass("linkId", Integer.class).get();
        ticketService.removeKbLink(linkId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- History --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/history",
            methods = HttpMethod.GET,
            summary = "List history entries for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardTicketHistory[].class)))
    private void getHistory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findHistory(ticketId));
    }

    // -- Activity --

    @OpenApi(
            path = "/api/v1/boards/{boardId}/tickets/{ticketId}/activity",
            methods = HttpMethod.GET,
            summary = "List activity entries for a ticket",
            tags = {"Board Tickets"},
            pathParams = {
                @OpenApiParam(name = "boardId", type = Integer.class, required = true),
                @OpenApiParam(name = "ticketId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void getActivity(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireViewAccess(boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findActivity(ticketId));
    }

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
