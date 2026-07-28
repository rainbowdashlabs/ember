/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsFederationService.FederatedCommentAuthor;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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

    private void federatedListNews(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(newsFederationService.browseFederatedNews(session.stationId()));
    }

    private void federatedGetNews(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int newsId = pathInt(ctx, "newsId");
        ctx.json(newsFederationService.getFederatedNews(session.stationId(), stationUid, newsId));
    }

    private void federatedListComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int newsId = pathInt(ctx, "newsId");
        ctx.json(newsFederationService.listFederatedComments(session.stationId(), stationUid, newsId));
    }

    private void federatedCreateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int newsId = pathInt(ctx, "newsId");
        var req = requireContent(ctx);
        var comment = newsFederationService.createFederatedComment(
                session.stationId(), stationUid, newsId, author(session), req.parentId(), req.content());
        ctx.status(HttpStatus.CREATED).json(comment);
    }

    private void federatedUpdateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        var req = requireContent(ctx);
        ctx.json(newsFederationService.updateFederatedComment(
                session.stationId(), stationUid, commentId, author(session), req.content()));
    }

    private void federatedDeleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        newsFederationService.deleteFederatedComment(session.stationId(), stationUid, commentId, author(session));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private NewsRoutes.CommentRequest requireContent(Context ctx) {
        var req = ctx.bodyAsClass(NewsRoutes.CommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        return req;
    }

    private FederatedCommentAuthor author(UserSession session) {
        return new FederatedCommentAuthor(
                memberIdentityFactory.fromMemberId(session.member().id()),
                session.member().uid(),
                session.account().fullName().trim());
    }
}
