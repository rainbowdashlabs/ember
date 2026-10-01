/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * HTTP route definitions for event comments.
 * Provides endpoints for CRUD operations on comments attached to events.
 */
@Singleton
public class EventCommentRoutes implements Routes {
    private final CommentService commentService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public EventCommentRoutes(
            CommentService commentService,
            MemberIdentityFactory memberIdentityFactory,
            MemberNameResolver memberNameResolver) {
        this.commentService = commentService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.memberNameResolver = memberNameResolver;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/{eventId}/comments", this::list, StationPermission.LOGIN);
        routes.post(prefix + "/events/{eventId}/comments", this::create, StationPermission.LOGIN);
        routes.put(prefix + "/events/comments/{commentId}", this::update, StationPermission.LOGIN);
        routes.delete(prefix + "/events/comments/{commentId}", this::delete, StationPermission.LOGIN);
    }

    /**
     * Which comments the listing asks for. Without a date or a date scope it is every comment of the
     * appointment, so existing callers keep their shape; {@code ?date=yyyy-MM-dd} narrows it to one
     * occurrence, and {@code ?scope=date} without a date or with {@code date=none} to the comments
     * on the appointment as a whole.
     */
    private static CommentFilter filterOf(Context ctx) {
        String dateParam = ctx.queryParam("date");
        if (dateParam == null && !"date".equals(ctx.queryParam("scope"))) return CommentFilter.ALL;
        if (dateParam == null || dateParam.isBlank() || "none".equalsIgnoreCase(dateParam)) {
            return new CommentFilter.Occurrence(null);
        }
        try {
            return new CommentFilter.Occurrence(LocalDate.parse(dateParam));
        } catch (DateTimeParseException notADate) {
            throw Refusal.COMMENT_DAY_NOT_A_DATE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/comments",
            methods = HttpMethod.GET,
            summary = "List comments for an event, optionally scoped to a specific occurrence",
            tags = {"Event Comments"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            queryParams = {
                @OpenApiParam(
                        name = "date",
                        description =
                                "ISO yyyy-MM-dd. When supplied, returns only comments for that occurrence of a recurring event. "
                                        + "Use 'none' to explicitly request whole-event comments (event_date IS NULL)."),
                @OpenApiParam(
                        name = "scope",
                        description =
                                "Either 'all' (default; date filter ignored) or 'date' (filters to the date param).")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void list(Context ctx) {
        int eventId = pathInt(ctx, "eventId");
        commentService.requireReadable(UserSession.from(ctx), CommentEntityType.EVENT, eventId);
        var filter = filterOf(ctx);
        ctx.json(commentService.list(CommentEntityType.EVENT, eventId, filter).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/comments",
            methods = HttpMethod.POST,
            summary = "Add a comment to an event",
            tags = {"Event Comments"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateCommentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)))
    private void create(Context ctx) {
        int eventId = pathInt(ctx, "eventId");
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw Refusal.COMMENT_NEEDS_TEXT.raise();
        }
        var comment = commentService.create(
                session,
                CommentEntityType.EVENT,
                eventId,
                writer(session),
                new NewComment(request.parentId(), request.eventDate(), request.content()));
        ctx.status(HttpStatus.CREATED).json(toResponse(comment));
    }

    @OpenApi(
            path = "/api/v1/events/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Update own comment on an event",
            tags = {"Event Comments"},
            pathParams = @OpenApiParam(name = "commentId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateCommentRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int commentId = pathInt(ctx, "commentId");
        UserSession session = UserSession.from(ctx);
        var comment = commentService
                .findById(CommentEntityType.EVENT, commentId)
                .orElseThrow(Refusal.COMMENT_NOT_HERE_ON_CHANGE::raise);
        if (!commentService.mayModify(session, actor(session), comment, Moderation.EDIT)) {
            throw Refusal.COMMENT_NOT_YOURS_TO_CHANGE.raise();
        }
        var request = ctx.bodyAsClass(UpdateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw Refusal.COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        var updated = commentService
                .update(comment, writer(session), request.content())
                .orElseThrow(Refusal.COMMENT_NOT_HERE_AFTER_CHANGE::raise);
        ctx.json(toResponse(updated));
    }

    @OpenApi(
            path = "/api/v1/events/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment (own or EVENT_MANAGER)",
            tags = {"Event Comments"},
            pathParams = @OpenApiParam(name = "commentId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int commentId = pathInt(ctx, "commentId");
        UserSession session = UserSession.from(ctx);
        var comment = commentService
                .findById(CommentEntityType.EVENT, commentId)
                .orElseThrow(Refusal.COMMENT_NOT_HERE_ON_DELETE::raise);
        commentService.requireSameStation(session, comment);
        if (!commentService.mayModify(session, actor(session), comment, Moderation.DELETE)) {
            throw Refusal.COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        if (!commentService.delete(comment)) {
            throw Refusal.COMMENT_NOT_DELETED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private MemberIdentity actor(UserSession session) {
        return memberIdentityFactory.local(session.stationId(), session.member().id());
    }

    private CommentWriter writer(UserSession session) {
        return CommentWriter.local(
                actor(session), NameParts.of(session.account()).called());
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponseMapper.fromEvent(memberNameResolver, comment);
    }

    /**
     * Request body for creating a comment.
     *
     * @param eventDate Occurrence date (ISO {@code yyyy-MM-dd}) for date-scoped comments on
     *                  recurring events; {@code null} for whole-event comments.
     */
    public record CreateCommentRequest(Integer parentId, String content, LocalDate eventDate) {}

    /**
     * Request body for updating a comment.
     */
    public record UpdateCommentRequest(String content) {}
}
