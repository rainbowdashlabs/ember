/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.BoardRefusal;
import dev.chojo.ember.api.refusal.CommentRefusal;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicketFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.question.QuestionCheck;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
 * The detail panel of a local board ticket: comments, checklist, watchers and the values of the
 * board's custom fields.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class BoardTicketDetailRoutes implements Routes {

    private final BoardTicketService ticketService;
    private final BoardService boardService;
    private final CommentService commentService;
    private final MemberNameResolver memberNameResolver;
    private final BoardRouteGuards guards;

    @Inject
    public BoardTicketDetailRoutes(
            BoardTicketService ticketService,
            BoardService boardService,
            CommentService commentService,
            MemberNameResolver memberNameResolver,
            BoardRouteGuards guards) {
        this.ticketService = ticketService;
        this.boardService = boardService;
        this.commentService = commentService;
        this.memberNameResolver = memberNameResolver;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/boards/{boardKey}/tickets/{ticketNumber}";

        routes.get(p + "/comments", this::getComments, StationPermission.BOARD_USE);
        routes.post(p + "/comments", this::createComment, StationPermission.BOARD_USE);
        routes.put(p + "/comments/{commentId}", this::updateComment, StationPermission.BOARD_USE);
        routes.delete(p + "/comments/{commentId}", this::deleteComment, StationPermission.BOARD_USE);

        routes.get(p + "/checklist", this::getChecklist, StationPermission.BOARD_USE);
        routes.post(p + "/checklist", this::addChecklistItem, StationPermission.BOARD_USE);
        routes.put(p + "/checklist/reorder", this::reorderChecklist, StationPermission.BOARD_USE);
        routes.put(p + "/checklist/{itemId}", this::updateChecklistItem, StationPermission.BOARD_USE);
        routes.delete(p + "/checklist/{itemId}", this::deleteChecklistItem, StationPermission.BOARD_USE);

        routes.get(p + "/watchers", this::getWatchers, StationPermission.BOARD_USE);
        routes.post(p + "/watch", this::watchTicket, StationPermission.BOARD_USE);
        routes.delete(p + "/watch", this::unwatchTicket, StationPermission.BOARD_USE);

        routes.get(p + "/fields", this::getFieldValues, StationPermission.BOARD_USE);
        routes.put(p + "/fields/{fieldId}", this::setFieldValue, StationPermission.BOARD_USE);
        routes.delete(p + "/fields/{fieldId}", this::deleteFieldValue, StationPermission.BOARD_USE);
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void getComments(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int ticketId = guards.viewableTicketId(ctx, session);
        commentService.requireReadable(session, CommentEntityType.BOARD_TICKET, ticketId);
        ctx.json(commentService.list(CommentEntityType.BOARD_TICKET, ticketId, CommentFilter.ALL).stream()
                .map(this::toResponse)
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BoardTicketCommentRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(BoardTicketCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) throw BoardRefusal.TICKET_COMMENT_NEEDS_TEXT.raise();
        var comment = commentService.create(
                session,
                CommentEntityType.BOARD_TICKET,
                ticketId,
                writer(session),
                new NewComment(req.parentId(), null, req.content()));
        ctx.status(HttpStatus.CREATED).json(toResponse(comment));
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BoardTicketCommentRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void updateComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var comment = requireCommentOn(ctx, guards.editableTicketId(ctx, session));
        if (!commentService.mayModify(session, guards.actor(session), comment, Moderation.EDIT)) {
            throw CommentRefusal.COMMENT_NOT_YOURS_TO_CHANGE.raise();
        }
        var req = ctx.bodyAsClass(BoardTicketCommentRequest.class);
        String content = req.content();
        if (content == null || content.isBlank()) throw CommentRefusal.COMMENT_CHANGE_NEEDS_TEXT.raise();
        commentService
                .update(comment, writer(session), content)
                .orElseThrow(BoardRefusal.TICKET_COMMENT_NOT_HERE::raise);
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
        StationSession session = StationSession.from(ctx);
        var comment = requireCommentOn(ctx, guards.editableTicketId(ctx, session));
        if (!commentService.mayModify(session, guards.actor(session), comment, Moderation.DELETE)) {
            throw CommentRefusal.COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        commentService.delete(comment);
        ctx.status(HttpStatus.NO_CONTENT);
    }

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
        StationSession session = StationSession.from(ctx);
        ctx.json(ticketService.findChecklistItems(guards.viewableTicketId(ctx, session)));
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
        StationSession session = StationSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw BoardRefusal.CHECKLIST_ITEM_NEEDS_A_TITLE.raise();
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
        StationSession session = StationSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        ticketService.updateChecklistItem(
                pathInt(ctx, "itemId"),
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
        StationSession session = StationSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        ticketService.deleteChecklistItem(
                pathInt(ctx, "itemId"), ticketId, session.member().id());
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
        StationSession session = StationSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        var req = ctx.bodyAsClass(ReorderChecklistRequest.class);
        ticketService.reorderChecklistItems(ticketId, req.orderedIds());
        ctx.status(HttpStatus.OK);
    }

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
        StationSession session = StationSession.from(ctx);
        ctx.json(ticketService.findWatchers(guards.viewableTicketId(ctx, session)));
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
        StationSession session = StationSession.from(ctx);
        ticketService.watchTicket(
                guards.viewableTicketId(ctx, session), session.member().id());
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
        StationSession session = StationSession.from(ctx);
        ticketService.unwatchTicket(
                guards.viewableTicketId(ctx, session), session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

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
        StationSession session = StationSession.from(ctx);
        ctx.json(ticketService.findFieldValues(guards.viewableTicketId(ctx, session)));
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BoardFieldValue.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFieldValue(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        guards.requireEditAccess(boardId, session);
        int ticketId = guards.resolveTicketId(ctx, boardId);
        int fieldId = pathInt(ctx, "fieldId");
        var field = boardService.findFields(boardId).stream()
                .filter(f -> f.id() == fieldId)
                .findFirst()
                .orElseThrow(BoardRefusal.TICKET_FIELD_NOT_HERE::raise);
        var value = BoardFieldValue.parse(field.fieldType(), ctx.body());
        if (value == null) throw BoardRefusal.TICKET_FIELD_VALUE_NOT_ACCEPTED.raise();
        QuestionCheck.answer(field.question(), value.answer()).ifPresent(problem -> {
            throw BoardRefusal.TICKET_FIELD_VALUE_NOT_ACCEPTED.raise(problem.message());
        });
        ticketService.setFieldValue(ticketId, fieldId, value);
        ticketService.logHistory(
                ticketId, BoardTicketHistoryAction.FIELD_CHANGED, "Feld #" + fieldId, guards.actor(session));
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
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteFieldValue(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int ticketId = guards.editableTicketId(ctx, session);
        int fieldId = pathInt(ctx, "fieldId");
        int boardId = guards.resolveBoardId(ctx, session.stationId());
        boolean required = boardService.findFields(boardId).stream()
                .anyMatch(field -> field.id() == fieldId && field.config().required());
        if (required) throw BoardRefusal.TICKET_FIELD_VALUE_REQUIRED.raise();
        ticketService.deleteFieldValue(ticketId, fieldId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Reads the comment named by the {@code commentId} path parameter after asserting it belongs to
     * the addressed ticket. Whether the caller may edit that ticket is settled by the guard that
     * resolved it.
     */
    private Comment requireCommentOn(Context ctx, int ticketId) {
        return commentService
                .findById(CommentEntityType.BOARD_TICKET, pathInt(ctx, "commentId"))
                .filter(comment -> comment.targetId() == ticketId)
                .orElseThrow(BoardRefusal.TICKET_COMMENT_NOT_HERE::raise);
    }

    private CommentWriter writer(StationSession session) {
        return CommentWriter.local(
                guards.actor(session), NameParts.of(session.user().account()).called());
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponseMapper.fromBoard(memberNameResolver, comment);
    }

    public record ChecklistItemRequest(String title, Boolean checked) {}

    public record ReorderChecklistRequest(List<Integer> orderedIds) {}

    public record BoardTicketCommentRequest(Integer parentId, String content) {}
}
