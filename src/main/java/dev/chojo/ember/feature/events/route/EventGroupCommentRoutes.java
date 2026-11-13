/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.CommentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.CreateCommentRequest;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.UpdateCommentRequest;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.StationModule;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Comments on an event group.
 *
 * <p>Whoever may open the event group reads and writes them. A comment is changed by its author only
 * and removed by its author or an appointment manager, as on an appointment. A comment is reached
 * through the event group it hangs under, and one under another event group reads as absent.
 */
@Singleton
public class EventGroupCommentRoutes implements Routes {
    private static final String COMMENTS = "/events/groups/{eventGroupId}/comments";

    private final CommentService commentService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public EventGroupCommentRoutes(
            CommentService commentService,
            MemberIdentityFactory memberIdentityFactory,
            MemberNameResolver memberNameResolver) {
        this.commentService = commentService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.memberNameResolver = memberNameResolver;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + COMMENTS, this::list, StationPermission.USER, StationModule.EVENTS);
        routes.post(prefix + COMMENTS, this::create, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + COMMENTS + "/{commentId}", this::update, StationPermission.USER, StationModule.EVENTS);
        routes.delete(prefix + COMMENTS + "/{commentId}", this::delete, StationPermission.USER, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/comments",
            methods = HttpMethod.GET,
            summary = "List the comments on an event group",
            tags = {"Event Comments"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void list(Context ctx) {
        int eventGroupId = pathInt(ctx, "eventGroupId");
        commentService.requireReadable(StationSession.from(ctx), CommentEntityType.EVENT_GROUP, eventGroupId);
        ctx.json(commentService.list(CommentEntityType.EVENT_GROUP, eventGroupId, CommentFilter.ALL).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/comments",
            methods = HttpMethod.POST,
            summary = "Comment on an event group",
            tags = {"Event Comments"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateCommentRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        int eventGroupId = pathInt(ctx, "eventGroupId");
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CreateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw CommentRefusal.COMMENT_NEEDS_TEXT.raise();
        }
        var comment = commentService.create(
                session,
                CommentEntityType.EVENT_GROUP,
                eventGroupId,
                writer(session),
                new NewComment(request.parentId(), null, request.content()));
        ctx.status(HttpStatus.CREATED).json(toResponse(comment));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Change one's own comment on an event group",
            tags = {"Event Comments"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateCommentRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var comment = readableComment(ctx, session, CommentRefusal.COMMENT_NOT_HERE_ON_CHANGE);
        if (!commentService.mayModify(session, actor(session), comment, Moderation.EDIT)) {
            throw CommentRefusal.COMMENT_NOT_YOURS_TO_CHANGE.raise();
        }
        var request = ctx.bodyAsClass(UpdateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw CommentRefusal.COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        var updated = commentService
                .update(comment, writer(session), request.content())
                .orElseThrow(CommentRefusal.COMMENT_NOT_HERE_AFTER_CHANGE::raise);
        ctx.json(toResponse(updated));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment on an event group (own or EVENT_MANAGER)",
            tags = {"Event Comments"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var comment = readableComment(ctx, session, CommentRefusal.COMMENT_NOT_HERE_ON_DELETE);
        commentService.requireSameStation(session, comment);
        if (!commentService.mayModify(session, actor(session), comment, Moderation.DELETE)) {
            throw CommentRefusal.COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        if (!commentService.delete(comment)) {
            throw CommentRefusal.COMMENT_NOT_DELETED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The comment the path names, on the event group the path names, which the reader may open. A
     * comment under any other event group is refused as missing.
     */
    private Comment readableComment(Context ctx, StationSession session, Refusal missing) {
        int eventGroupId = pathInt(ctx, "eventGroupId");
        var comment = commentService
                .findById(CommentEntityType.EVENT_GROUP, pathInt(ctx, "commentId"))
                .filter(found -> found.targetId() == eventGroupId)
                .orElseThrow(missing::raise);
        commentService.requireReadable(session, CommentEntityType.EVENT_GROUP, eventGroupId);
        return comment;
    }

    private MemberIdentity actor(StationSession session) {
        return memberIdentityFactory.local(session.stationId(), session.member().id());
    }

    private CommentWriter writer(StationSession session) {
        return CommentWriter.local(
                actor(session), NameParts.of(session.user().account()).called());
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponseMapper.fromEventGroup(memberNameResolver, comment);
    }
}
