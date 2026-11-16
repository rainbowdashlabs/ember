/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.CommentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.route.CommentRouteSupport;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.CreateCommentRequest;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.UpdateCommentRequest;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
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
 * <p>Whoever may open the event group and log in on the station reads and writes them. A comment is
 * changed by its author only and removed by its author or an appointment manager, as on an
 * appointment. A comment is reached through the event group it hangs under, and one under another
 * event group reads as absent.
 */
@Singleton
public class EventGroupCommentRoutes implements Routes {
    private static final String COMMENTS = "/events/groups/{eventGroupId}/comments";

    private final CommentService commentService;
    private final CommentRouteSupport comments;

    @Inject
    public EventGroupCommentRoutes(
            CommentService commentService,
            MemberIdentityFactory memberIdentityFactory,
            MemberNameResolver memberNameResolver) {
        this.commentService = commentService;
        this.comments = new CommentRouteSupport(
                commentService,
                memberIdentityFactory,
                CommentEntityType.EVENT_GROUP,
                comment -> CommentResponseMapper.fromEventGroup(memberNameResolver, comment));
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        comments.register(
                routes,
                StationModule.EVENTS,
                prefix + COMMENTS,
                prefix + COMMENTS + "/{commentId}",
                this::list,
                this::create,
                this::update,
                this::delete);
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
        comments.list(ctx, pathInt(ctx, "eventGroupId"), CommentFilter.ALL);
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
        comments.create(ctx, pathInt(ctx, "eventGroupId"), request -> null);
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
        comments.update(ctx, readableComment(ctx, CommentRefusal.COMMENT_NOT_HERE_ON_CHANGE));
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
        comments.delete(ctx, readableComment(ctx, CommentRefusal.COMMENT_NOT_HERE_ON_DELETE));
    }

    /**
     * The comment the path names, on the event group the path names, which the reader may open. A
     * comment under any other event group is refused as missing.
     */
    private Comment readableComment(Context ctx, Refusal missing) {
        int eventGroupId = pathInt(ctx, "eventGroupId");
        var comment = comments.comment(pathInt(ctx, "commentId"), missing);
        if (comment.targetId() != eventGroupId) throw missing.raise();
        commentService.requireReadable(StationSession.from(ctx), CommentEntityType.EVENT_GROUP, eventGroupId);
        return comment;
    }
}
