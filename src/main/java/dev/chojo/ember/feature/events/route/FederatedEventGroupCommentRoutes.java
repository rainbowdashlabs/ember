/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.CreateCommentRequest;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.UpdateCommentRequest;
import dev.chojo.ember.feature.events.service.EventGroupCommentFederation;
import dev.chojo.ember.feature.members.entity.NameParts;
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
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The comments on an event group a partner station shares with this one, as this station's members
 * read and write them. Every answer comes from the station holding the event group, which decides
 * whether it is shared and whose comment a member may change; nobody here removes anybody else's.
 */
@Singleton
public class FederatedEventGroupCommentRoutes implements Routes {
    private static final String COMMENTS = "/federated/{stationuid}/events/groups/{eventGroupId}/comments";

    private final EventGroupCommentFederation comments;

    @Inject
    public FederatedEventGroupCommentRoutes(EventGroupCommentFederation comments) {
        this.comments = comments;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + COMMENTS, this::list, StationPermission.USER, StationModule.EVENTS);
        routes.post(prefix + COMMENTS, this::create, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + COMMENTS + "/{commentId}", this::update, StationPermission.USER, StationModule.EVENTS);
        routes.delete(prefix + COMMENTS + "/{commentId}", this::delete, StationPermission.USER, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}/comments",
            methods = HttpMethod.GET,
            summary = "List the comments on a partner station's event group",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(
                comments.listAtPartner(session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "eventGroupId")));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}/comments",
            methods = HttpMethod.POST,
            summary = "Comment on a partner station's event group",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateCommentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)))
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CreateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw EventRefusal.FEDERATED_COMMENT_NEEDS_TEXT.raise();
        }
        var created = comments.createAtPartner(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "eventGroupId"),
                session.member().uid(),
                NameParts.of(session.user().account()).called(),
                request.parentId(),
                request.content());
        ctx.status(HttpStatus.CREATED).json(created);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Change one's own comment on a partner station's event group",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateCommentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)))
    private void update(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(UpdateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw EventRefusal.FEDERATED_COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        ctx.json(comments.updateAtPartner(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "eventGroupId"),
                pathInt(ctx, "commentId"),
                session.member().uid(),
                request.content()));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete one's own comment on a partner station's event group",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        StationSession session = StationSession.from(ctx);
        comments.deleteAtPartner(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "eventGroupId"),
                pathInt(ctx, "commentId"),
                session.member().uid());
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
