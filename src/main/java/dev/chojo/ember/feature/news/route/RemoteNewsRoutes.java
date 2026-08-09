/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server news endpoints served to federation partners. Requests carry an RSA-signed
 * envelope instead of a user session; the consumer side that calls these endpoints lives in
 * {@link FederatedNewsRoutes}.
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

    private final NewsService newsService;
    private final NewsFederationService newsFederationService;
    private final EventFederationRepository eventFederationRepository;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public RemoteNewsRoutes(
            NewsService newsService,
            NewsFederationService newsFederationService,
            EventFederationRepository eventFederationRepository,
            MemberNameResolver memberNameResolver) {
        this.newsService = newsService;
        this.newsFederationService = newsFederationService;
        this.eventFederationRepository = eventFederationRepository;
        this.memberNameResolver = memberNameResolver;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(LIST_NEWS, this::remoteListNews)
                        .handle(GET_NEWS, this::remoteGetNews)
                        .handle(LIST_COMMENTS, this::remoteListComments)
                        .handle(CREATE_COMMENT, this::remoteCreateComment)
                        .handle(UPDATE_COMMENT, this::remoteUpdateComment)
                        .handle(DELETE_COMMENT, this::remoteDeleteComment));
    }

    private void remoteListNews(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        var newsIds = newsFederationService.findSharedNewsIds(partner.id(), partner.stationId());
        var newsList = newsIds.stream()
                .map(id -> newsService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(n -> {
                    NewsVisibilityRole visibilityRole =
                            newsFederationService.findVisibilityRole(n.id()).orElse(NewsVisibilityRole.MEMBER);
                    var authorResolved = n.author() != null ? memberNameResolver.resolveDisplay(n.author()) : null;
                    String authorName =
                            authorResolved != null && authorResolved.name() != null ? authorResolved.name() : "";
                    return new RemoteNewsSummary(
                            n.id(),
                            n.title(),
                            n.contentHtml() != null ? n.contentHtml() : "",
                            authorName,
                            n.publishedAt() != null ? n.publishedAt().toString() : "",
                            newsService.countComments(n.id()),
                            visibilityRole);
                })
                .toList();
        ctx.json(newsList);
    }

    private void remoteGetNews(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int newsId = pathInt(ctx, "newsId");
        requireSharedNews(partner, newsId);
        var news = newsService.findById(newsId).orElseThrow(NotFoundResponse::new);
        var authorResolved = news.author() != null ? memberNameResolver.resolveDisplay(news.author()) : null;
        String authorName = authorResolved != null && authorResolved.name() != null ? authorResolved.name() : "";
        NewsVisibilityRole visibilityRole =
                newsFederationService.findVisibilityRole(newsId).orElse(NewsVisibilityRole.MEMBER);
        ctx.json(new RemoteNewsDetail(
                news.id(),
                news.title(),
                news.contentMarkdown() != null ? news.contentMarkdown() : "",
                news.contentHtml() != null ? news.contentHtml() : "",
                authorName,
                news.publishedAt() != null ? news.publishedAt().toString() : "",
                newsService.countComments(newsId),
                visibilityRole));
    }

    private void remoteListComments(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int newsId = pathInt(ctx, "newsId");
        requireSharedNews(partner, newsId);
        var comments = newsService.findComments(newsId);
        ctx.json(comments.stream().map(this::toCommentResponse).toList());
    }

    private void remoteCreateComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int newsId = pathInt(ctx, "newsId");
        requireSharedNews(partner, newsId);
        var req = ctx.bodyAsClass(RemoteNewsCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var authorIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid());
        var comment = newsService.createComment(
                partner.stationId(), newsId, req.parentId(), authorIdentity, req.displayName(), req.content());
        eventFederationRepository.cacheName(partner.id(), req.remoteMemberUid(), req.displayName());
        ctx.status(HttpStatus.CREATED).json(toCommentResponse(comment));
    }

    private void remoteUpdateComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteNewsCommentUpdateRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var comment = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid());
        if (!expectedIdentity.sameMember(comment.author())) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        newsService.updateComment(commentId, req.content());
        var updated = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
        ctx.json(toCommentResponse(updated));
    }

    private void remoteDeleteComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteNewsCommentDeleteRequest.class);
        var comment = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid());
        if (!expectedIdentity.sameMember(comment.author())) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        if (newsService.deleteComment(partner.stationId(), commentId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    /**
     * Confirms the partner is allowed to see the given news item, i.e. it is in the
     * set this station shares with that partner. Guards every {@code /remote/news}
     * read/write so a partner cannot address never-federated news by enumerating ids.
     */
    private void requireSharedNews(FederationPartner partner, int newsId) {
        var newsIds = newsFederationService.findSharedNewsIds(partner.id(), partner.stationId());
        if (!newsIds.contains(newsId)) {
            throw new NotFoundResponse();
        }
    }

    private CommentResponse toCommentResponse(NewsComment comment) {
        return CommentResponseMapper.fromNews(memberNameResolver, comment);
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
