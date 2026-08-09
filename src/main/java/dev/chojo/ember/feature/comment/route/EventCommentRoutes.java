/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
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

import java.time.LocalDate;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * HTTP route definitions for event comments.
 * Provides endpoints for CRUD operations on comments attached to events.
 */
@Singleton
public class EventCommentRoutes implements Routes {
    private final CommentService commentService;
    private final EventCrudService crudService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public EventCommentRoutes(
            CommentService commentService,
            EventCrudService crudService,
            MemberIdentityFactory memberIdentityFactory,
            MemberNameResolver memberNameResolver) {
        this.commentService = commentService;
        this.crudService = crudService;
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
        String dateParam = ctx.queryParam("date");
        String scope = ctx.queryParam("scope");
        // Default behaviour stays "everything for the event" so existing callers don't
        // change shape. Date-scoped reads opt in via either ?date=YYYY-MM-DD (most common
        // for occurrence deep-links) or ?scope=date&date=none (whole-event-only).
        List<Comment> comments;
        if (dateParam != null || "date".equals(scope)) {
            LocalDate eventDate = null;
            if (dateParam != null && !dateParam.isBlank() && !"none".equalsIgnoreCase(dateParam)) {
                try {
                    eventDate = LocalDate.parse(dateParam);
                } catch (Exception e) {
                    throw new BadRequestResponse("date must be ISO yyyy-MM-dd or 'none'");
                }
            }
            comments = commentService.findByEventAndDate(eventId, eventDate);
        } else {
            comments = commentService.findByEvent(eventId);
        }
        ctx.json(comments.stream().map(this::toResponse).toList());
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
            throw new BadRequestResponse("content is required");
        }
        var author = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        String eventName = crudService.findById(eventId).map(StationEvent::name).orElse("");
        var comment = commentService.create(
                session.stationId(),
                eventId,
                request.parentId(),
                author,
                session.account().fullName().trim(),
                request.content(),
                eventName,
                request.eventDate());
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
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var authorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        if (comment.author() == null || !comment.author().sameMember(authorIdentity)) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        var request = ctx.bodyAsClass(UpdateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        commentService.update(commentId, request.content());
        var updated = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
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
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var authorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        boolean isAuthor = comment.author() != null && comment.author().sameMember(authorIdentity);
        boolean canModerate = session.hasPermission(StationPermission.EVENT_MANAGER);
        if (!isAuthor && !canModerate) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        if (commentService.delete(commentId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
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
