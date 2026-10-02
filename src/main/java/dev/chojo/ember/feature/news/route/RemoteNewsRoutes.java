/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

/**
 * Server-to-server news endpoints served to federation partners, through the serving functions of
 * {@code NewsFederationService}. Requests carry an RSA-signed envelope instead of a user session;
 * the consumer side that calls these endpoints lives in {@link FederatedNewsRoutes}.
 */
@Singleton
public class RemoteNewsRoutes implements Routes {

    public static final FederationEndpoint LIST_NEWS =
            FederationEndpoint.getList(FederationSurface.NEWS_SHARE, "/remote/news", RemoteNewsSummary.class);
    public static final FederationEndpoint GET_NEWS =
            FederationEndpoint.get(FederationSurface.NEWS_SHARE, "/remote/news/{newsId}", RemoteNewsDetail.class);
    public static final FederationEndpoint LIST_COMMENTS = FederationEndpoint.getList(
            FederationSurface.NEWS_SHARE, "/remote/news/{newsId}/comments", CommentResponse.class);
    public static final FederationEndpoint CREATE_COMMENT = FederationEndpoint.post(
            FederationSurface.NEWS_SHARE,
            "/remote/news/{newsId}/comments",
            RemoteNewsCommentRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint UPDATE_COMMENT = FederationEndpoint.put(
            FederationSurface.NEWS_SHARE,
            "/remote/news/comments/{commentId}",
            RemoteNewsCommentUpdateRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint DELETE_COMMENT = FederationEndpoint.delete(
            FederationSurface.NEWS_SHARE,
            "/remote/news/comments/{commentId}",
            RemoteNewsCommentDeleteRequest.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT =
            List.of(LIST_NEWS, GET_NEWS, LIST_COMMENTS, CREATE_COMMENT, UPDATE_COMMENT, DELETE_COMMENT);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteNewsRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(LIST_NEWS)
                .serve(GET_NEWS)
                .serve(LIST_COMMENTS)
                .serveCreated(CREATE_COMMENT)
                .serve(UPDATE_COMMENT)
                .serve(DELETE_COMMENT));
    }

    public record RemoteNewsSummary(
            int id,
            String title,
            String contentHtml,
            String authorName,
            String publishedAt,
            int commentCount,
            NewsVisibilityRole visibilityRole) {}

    public record RemoteNewsDetail(
            int id,
            String title,
            String contentMarkdown,
            String contentHtml,
            String authorName,
            String publishedAt,
            int commentCount,
            NewsVisibilityRole visibilityRole) {}

    /**
     * Request body for creating a comment from a remote federated partner.
     */
    public record RemoteNewsCommentRequest(
            UUID remoteMemberUid, String displayName, Integer parentId, String content) {}

    /**
     * Request body for updating a comment from a remote federated partner.
     */
    public record RemoteNewsCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    /**
     * Request body for deleting a comment from a remote federated partner.
     */
    public record RemoteNewsCommentDeleteRequest(UUID remoteMemberUid) {}
}
