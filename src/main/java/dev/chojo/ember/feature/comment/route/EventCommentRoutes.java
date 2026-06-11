/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventService;
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
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * HTTP route definitions for event comments.
 * Provides endpoints for CRUD operations on comments attached to events.
 */
@Singleton
public class EventCommentRoutes implements Routes {
    private final CommentService commentService;
    private final EventService eventService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public EventCommentRoutes(
            CommentService commentService,
            EventService eventService,
            MemberIdentityFactory memberIdentityFactory,
            MemberNameResolver memberNameResolver) {
        this.commentService = commentService;
        this.eventService = eventService;
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
            summary = "List comments for an event",
            tags = {"Event Comments"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void list(Context ctx) {
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var comments = commentService.findByEvent(eventId);
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
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var author = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        String eventName =
                eventService.findById(eventId).map(StationEvent::name).orElse("");
        var comment = commentService.create(
                session.stationId(),
                eventId,
                request.parentId(),
                author,
                session.account().fullName().trim(),
                request.content(),
                eventName);
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
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var authorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        if (comment.author() == null || !comment.author().equals(authorIdentity)) {
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
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var authorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        boolean isAuthor = comment.author() != null && comment.author().equals(authorIdentity);
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
        if (comment.deleted()) {
            return new CommentResponse(
                    comment.id(), comment.parentId(), null, null, "", true, comment.createdAt(), null);
        }

        var resolved = memberNameResolver.resolveDisplay(comment.author());
        String authorName = resolved.name() != null ? resolved.name() : "";
        return new CommentResponse(
                comment.id(),
                comment.parentId(),
                resolved.identity(),
                authorName,
                comment.content(),
                false,
                comment.createdAt(),
                comment.updatedAt());
    }

    /**
     * Request body for creating a comment.
     */
    public record CreateCommentRequest(Integer parentId, String content) {}

    /**
     * Request body for updating a comment.
     */
    public record UpdateCommentRequest(String content) {}

    /**
     * API response representing a comment with resolved author information.
     */
    @OpenApiName("EventCommentResponse")
    public record CommentResponse(
            int id,
            Integer parentId,
            MemberIdentity author,
            String authorName,
            String content,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {}
}
