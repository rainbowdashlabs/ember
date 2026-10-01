/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsFederationService.FederatedCommentAuthor;
import dev.chojo.ember.feature.news.service.NewsFederationService.FederatedNewsData;
import dev.chojo.ember.feature.news.service.NewsFederationService.FederatedNewsItem;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Consumer endpoints for news shared by federation partners. Every handler hands the partner
 * station over to {@link NewsFederationService}, which resolves the owning station transparently
 * whether it lives on this instance or on another one. The endpoints an owning station serves to
 * its partners live in {@link RemoteNewsRoutes}.
 */
@Singleton
public class FederatedNewsRoutes implements Routes {

    private final NewsFederationService newsFederationService;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public FederatedNewsRoutes(
            NewsFederationService newsFederationService, MemberIdentityFactory memberIdentityFactory) {
        this.newsFederationService = newsFederationService;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federated/news", this::federatedListNews, StationPermission.LOGIN);
        routes.get(prefix + "/federated/{stationuid}/news/{newsId}", this::federatedGetNews, StationPermission.LOGIN);
        routes.get(
                prefix + "/federated/{stationuid}/news/{newsId}/comments",
                this::federatedListComments,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/federated/{stationuid}/news/{newsId}/comments",
                this::federatedCreateComment,
                StationPermission.LOGIN);
        routes.put(
                prefix + "/federated/{stationuid}/news/comments/{commentId}",
                this::federatedUpdateComment,
                StationPermission.LOGIN);
        routes.delete(
                prefix + "/federated/{stationuid}/news/comments/{commentId}",
                this::federatedDeleteComment,
                StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/federated/news",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederatedNewsItem[].class)))
    private void federatedListNews(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(newsFederationService.browseFederatedNews(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/news/{newsId}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederatedNewsData.class)))
    private void federatedGetNews(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int newsId = pathInt(ctx, "newsId");
        ctx.json(newsFederationService.getFederatedNews(session.stationId(), stationUid, newsId));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/news/{newsId}/comments",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void federatedListComments(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int newsId = pathInt(ctx, "newsId");
        ctx.json(newsFederationService.listFederatedComments(session.stationId(), stationUid, newsId));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/news/{newsId}/comments",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsRoutes.CommentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)))
    private void federatedCreateComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int newsId = pathInt(ctx, "newsId");
        var req = requireContent(ctx);
        var comment = newsFederationService.createFederatedComment(
                session.stationId(), stationUid, newsId, author(session), req.parentId(), req.content());
        ctx.status(HttpStatus.CREATED).json(comment);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/news/comments/{commentId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsRoutes.CommentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)))
    private void federatedUpdateComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        var req = requireContent(ctx);
        ctx.json(newsFederationService.updateFederatedComment(
                session.stationId(), stationUid, commentId, author(session), req.content()));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/news/comments/{commentId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void federatedDeleteComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        newsFederationService.deleteFederatedComment(session.stationId(), stationUid, commentId, author(session));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private NewsRoutes.CommentRequest requireContent(Context ctx) {
        var req = ctx.bodyAsClass(NewsRoutes.CommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw Refusal.FEDERATED_NEWS_COMMENT_NEEDS_TEXT.raise();
        }
        return req;
    }

    private FederatedCommentAuthor author(StationSession session) {
        return new FederatedCommentAuthor(
                memberIdentityFactory.fromMemberId(session.member().id()),
                session.member().uid(),
                NameParts.of(session.user().account()).called());
    }
}
