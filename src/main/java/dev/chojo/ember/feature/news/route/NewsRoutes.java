/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP route definitions for the news feature.
 * Provides endpoints for CRUD operations on news articles and comments,
 * with role-based access control, notification dispatch, and federation support.
 */
@Singleton
public class NewsRoutes implements Routes {
    private final NewsService newsService;
    private final NewsFederationService newsFederationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final StationRepository stationRepository;
    private final EventFederationRepository eventFederationRepository;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public NewsRoutes(
            NewsService newsService,
            NewsFederationService newsFederationService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository,
            EventFederationRepository eventFederationRepository,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory) {
        this.newsService = newsService;
        this.newsFederationService = newsFederationService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
        this.eventFederationRepository = eventFederationRepository;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/news", this::list, StationPermission.LOGIN);
        routes.get(prefix + "/news/{id}", this::get, StationPermission.LOGIN);
        routes.post(prefix + "/news", this::create, StationPermission.NEWS_EDIT);
        routes.put(prefix + "/news/{id}", this::update, StationPermission.NEWS_EDIT);
        routes.delete(prefix + "/news/{id}", this::delete, StationPermission.NEWS_EDIT);
        routes.get(prefix + "/news/{id}/comments", this::listComments, StationPermission.LOGIN);
        routes.post(prefix + "/news/{id}/comments", this::createComment, StationPermission.LOGIN);
        routes.put(prefix + "/news/comments/{commentId}", this::updateComment, StationPermission.LOGIN);
        routes.delete(prefix + "/news/comments/{commentId}", this::deleteComment, StationPermission.LOGIN);

        // View tracking — anyone can record a view; only editors can read the seen/unseen lists.
        routes.post(prefix + "/news/{id}/view", this::recordView, StationPermission.LOGIN);
        routes.get(prefix + "/news/{id}/views", this::listViewers, StationPermission.NEWS_EDIT);

        // Federation sharing management
        routes.get(prefix + "/news/{id}/federation", this::getFederationShare, StationPermission.NEWS_FEDERATE);
        routes.put(prefix + "/news/{id}/federation", this::setFederationShare, StationPermission.NEWS_FEDERATE);
        routes.delete(prefix + "/news/{id}/federation", this::removeFederationShare, StationPermission.NEWS_FEDERATE);

        // Federated (user-facing, bearer token auth)
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

        // Remote (server-to-server, RSA signature auth)
        routes.get(prefix + "/remote/news", this::remoteListNews);
        routes.get(prefix + "/remote/news/{newsId}", this::remoteGetNews);
        routes.get(prefix + "/remote/news/{newsId}/comments", this::remoteListComments);
        routes.post(prefix + "/remote/news/{newsId}/comments", this::remoteCreateComment);
        routes.put(prefix + "/remote/news/comments/{commentId}", this::remoteUpdateComment);
        routes.delete(prefix + "/remote/news/comments/{commentId}", this::remoteDeleteComment);

        // Public blog
        routes.get(prefix + "/public/station/{stationUid}/blog", this::publicBlogList);
        routes.get(prefix + "/public/station/{stationUid}/blog/{blogId}", this::publicBlogDetail);
    }

    @OpenApi(
            path = "/api/v1/news",
            methods = HttpMethod.GET,
            summary = "List news visible to the current user",
            tags = {"News"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        List<News> newsList;
        if (session.hasPermission(StationPermission.NEWS_MANAGER)) {
            newsList = newsService.findByStation(session.stationId(), offset, limit);
        } else {
            newsList = newsService.findVisibleForMember(
                    session.stationId(), session.member().id(), offset, limit);
        }
        ctx.json(newsList.stream()
                .map(n -> toResponse(n, session.hasPermission(StationPermission.NEWS_MANAGER)))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/news/{id}",
            methods = HttpMethod.GET,
            summary = "Get a news entry",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        newsService
                .findById(id)
                .ifPresentOrElse(
                        news -> ctx.json(toResponse(news, session.hasPermission(StationPermission.NEWS_MANAGER))),
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/news",
            methods = HttpMethod.POST,
            summary = "Create a news entry",
            tags = {"News"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = NewsResponse.class)))
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(NewsRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("title is required");
        }
        if (request.contentMarkdown() == null || request.contentMarkdown().isBlank()) {
            throw new BadRequestResponse("contentMarkdown is required");
        }
        var authorIdentity = memberIdentityFactory.fromMemberId(session.member().id());
        var news = newsService.create(
                session.stationId(),
                request.title(),
                request.contentMarkdown(),
                request.contentHtml() != null ? request.contentHtml() : "",
                authorIdentity,
                request.userTypes() != null ? request.userTypes() : List.of(),
                request.groupIds() != null ? request.groupIds() : List.of(),
                request.tagIds() != null ? request.tagIds() : List.of(),
                request.memberIds() != null ? request.memberIds() : List.of());
        if (request.publicBlog() != null) {
            newsService.updatePublicBlog(news.id(), request.publicBlog());
        }
        var result = newsService.findById(news.id()).orElse(news);
        ctx.status(HttpStatus.CREATED).json(toResponse(result, true));
    }

    @OpenApi(
            path = "/api/v1/news/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a news entry",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify news from another station");
        }
        var request = ctx.bodyAsClass(NewsRequest.class);
        newsService
                .update(
                        id,
                        request.title(),
                        request.contentMarkdown(),
                        request.contentHtml() != null ? request.contentHtml() : "",
                        request.userTypes() != null ? request.userTypes() : List.of(),
                        request.groupIds() != null ? request.groupIds() : List.of(),
                        request.tagIds() != null ? request.tagIds() : List.of(),
                        request.memberIds() != null ? request.memberIds() : List.of())
                .ifPresentOrElse(
                        updated -> {
                            if (request.publicBlog() != null) {
                                newsService.updatePublicBlog(updated.id(), request.publicBlog());
                            }
                            var result = newsService.findById(updated.id()).orElse(updated);
                            ctx.json(toResponse(result, true));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/news/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a news entry",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot delete news from another station");
        }
        if (newsService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    /**
     * Converts a {@link News} entity to an API response, resolving the author name and comment count.
     *
     * @param news                the news entity
     * @param includeRestrictions whether to include group restriction IDs in the response
     * @return the news response DTO
     */
    private NewsResponse toResponse(News news, boolean includeRestrictions) {
        var resolved = news.author() != null ? memberNameResolver.resolveDisplay(news.author()) : null;
        String authorName = resolved != null && resolved.name() != null ? resolved.name() : "";
        List<String> userTypes = List.of();
        List<Integer> groupIds = List.of();
        List<Integer> tagIds = List.of();
        List<Integer> memberIds = List.of();
        if (includeRestrictions) {
            var restrictions = newsService.findRestrictions(news.id());
            userTypes = restrictions.userTypes();
            groupIds = restrictions.groupIds();
            tagIds = restrictions.tagIds();
            memberIds = restrictions.memberIds();
        }
        int commentCount = newsService.countComments(news.id());
        return new NewsResponse(
                news.id(),
                news.stationId(),
                news.title(),
                news.contentMarkdown(),
                news.contentHtml(),
                resolved != null ? resolved.identity() : null,
                authorName,
                news.publishedAt(),
                news.createdAt(),
                userTypes,
                groupIds,
                tagIds,
                memberIds,
                commentCount,
                news.publicBlog());
    }

    // -- Comments --

    @OpenApi(
            path = "/api/v1/news/{id}/comments",
            methods = HttpMethod.GET,
            summary = "List comments for a news entry",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void listComments(Context ctx) {
        int newsId = ctx.pathParamAsClass("id", Integer.class).get();
        var comments = newsService.findComments(newsId);
        ctx.json(comments.stream().map(this::toCommentResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/news/{id}/comments",
            methods = HttpMethod.POST,
            summary = "Add a comment to a news entry",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CommentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)))
    private void createComment(Context ctx) {
        int newsId = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var authorIdentity = memberIdentityFactory.fromMemberId(session.member().id());
        var comment = newsService.createComment(
                session.stationId(),
                newsId,
                request.parentId(),
                authorIdentity,
                session.account().fullName().trim(),
                request.content());

        ctx.status(HttpStatus.CREATED).json(toCommentResponse(comment));
    }

    @OpenApi(
            path = "/api/v1/news/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Update own comment",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "commentId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CommentRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateComment(Context ctx) {
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
        var sessionIdentity =
                memberIdentityFactory.fromMemberId(session.member().id());
        if (!sessionIdentity.sameMember(comment.author())) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        var request = ctx.bodyAsClass(CommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        newsService.updateComment(commentId, request.content());
        var updated = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
        ctx.json(toCommentResponse(updated));
    }

    @OpenApi(
            path = "/api/v1/news/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment (own or NEWS_MANAGER)",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "commentId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteComment(Context ctx) {
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
        var sessionIdentity =
                memberIdentityFactory.fromMemberId(session.member().id());
        boolean isAuthor = sessionIdentity.sameMember(comment.author());
        boolean canModerate = session.hasPermission(StationPermission.NEWS_MANAGER);
        if (!isAuthor && !canModerate) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        if (newsService.deleteComment(session.stationId(), commentId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/news/{id}/view",
            methods = HttpMethod.POST,
            summary = "Record that the current member fully saw a news entry",
            description =
                    "Idempotent — repeated calls from the same member are silently ignored. The client fires this once the news entry is fully visible in the viewport.",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void recordView(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != session.stationId()) throw new NotFoundResponse();
        newsService.recordView(id, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/news/{id}/views",
            methods = HttpMethod.GET,
            summary = "List members who saw / have not yet seen a news entry",
            description = "Editor-only. Returns two lists: members who fully saw the news (with timestamps,"
                    + " most recent first) and members who are eligible but have not yet been observed viewing it.",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsViewsResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listViewers(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != session.stationId()) throw new NotFoundResponse();
        var summary = newsService.findViewerSummary(id, session.stationId());
        ctx.json(new NewsViewsResponse(
                summary.seen().stream().map(this::toViewerEntry).toList(),
                summary.unseen().stream().map(this::toViewerEntry).toList()));
    }

    private NewsViewerEntry toViewerEntry(dev.chojo.ember.feature.news.entity.NewsViewer viewer) {
        var enriched = memberNameResolver.enrichDisplay(viewer.member());
        return new NewsViewerEntry(enriched != null ? enriched : viewer.member(), viewer.seenAt());
    }

    /**
     * Converts a {@link NewsComment} entity to an API response, resolving the author name.
     *
     * @param comment the comment entity
     * @return the comment response DTO
     */
    private CommentResponse toCommentResponse(NewsComment comment) {
        if (comment.deleted()) {
            return new CommentResponse(
                    comment.id(), comment.newsId(), comment.parentId(), null, null, "", true, comment.createdAt());
        }

        var resolved = comment.author() != null ? memberNameResolver.resolveDisplay(comment.author()) : null;
        String authorName = resolved != null && resolved.name() != null ? resolved.name() : "";
        return new CommentResponse(
                comment.id(),
                comment.newsId(),
                comment.parentId(),
                resolved != null ? resolved.identity() : null,
                authorName,
                comment.content(),
                false,
                comment.createdAt());
    }

    // -- Federation sharing management --

    private void getFederationShare(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != session.stationId()) throw new ForbiddenResponse();
        var share = newsFederationService.findShareByNews(id);
        if (share.isEmpty()) {
            ctx.json(new NewsFederationShareResponse(false, null, null, null));
            return;
        }
        var targets = newsFederationService.findShareTargets(share.get().id());
        ctx.json(new NewsFederationShareResponse(
                true, share.get().scope(), share.get().visibilityRole(), targets));
    }

    private void setFederationShare(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != session.stationId()) throw new ForbiddenResponse();
        var req = ctx.bodyAsClass(SetNewsFederationShareRequest.class);
        newsFederationService.setShare(
                id,
                req.scope(),
                req.visibilityRole() != null ? req.visibilityRole() : "MEMBER",
                req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new NewsFederationShareResponse(
                true, req.scope(), req.visibilityRole() != null ? req.visibilityRole() : "MEMBER", null));
    }

    private void removeFederationShare(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != session.stationId()) throw new ForbiddenResponse();
        newsFederationService.removeShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Remote endpoints (server-to-server, RSA signature auth) --

    private void remoteListNews(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var newsIds = newsFederationService.findSharedNewsIds(partner.id(), partner.stationId());
        var newsList = newsIds.stream()
                .map(id -> newsService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(n -> {
                    String visibilityRole =
                            newsFederationService.findVisibilityRole(n.id()).orElse("MEMBER");
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
        var partner = requireFederationPartner(ctx);
        int newsId = ctx.pathParamAsClass("newsId", Integer.class).get();
        var newsIds = newsFederationService.findSharedNewsIds(partner.id(), partner.stationId());
        if (!newsIds.contains(newsId)) {
            throw new NotFoundResponse();
        }
        var news = newsService.findById(newsId).orElseThrow(NotFoundResponse::new);
        var authorResolved = news.author() != null ? memberNameResolver.resolveDisplay(news.author()) : null;
        String authorName = authorResolved != null && authorResolved.name() != null ? authorResolved.name() : "";
        String visibilityRole = newsFederationService.findVisibilityRole(newsId).orElse("MEMBER");
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
        requireFederationPartner(ctx);
        int newsId = ctx.pathParamAsClass("newsId", Integer.class).get();
        var comments = newsService.findComments(newsId);
        ctx.json(comments.stream().map(this::toCommentResponse).toList());
    }

    private void remoteCreateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int newsId = ctx.pathParamAsClass("newsId", Integer.class).get();
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
        var partner = requireFederationPartner(ctx);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
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
        var partner = requireFederationPartner(ctx);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
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

    // -- Federated proxy endpoints (user-facing, bearer auth) --

    private void federatedListNews(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(newsFederationService.browseFederatedNews(session.stationId()));
    }

    private void federatedGetNews(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = UUID.fromString(ctx.pathParam("stationuid"));
        int newsId = ctx.pathParamAsClass("newsId", Integer.class).get();
        var news = newsFederationService.getFederatedNews(session.stationId(), stationUid, newsId);
        ctx.json(news);
    }

    private void federatedListComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int newsId = ctx.pathParamAsClass("newsId", Integer.class).get();

        if (partner.isRemote()) {
            var result = federationHttpClient.getList(
                    partner.remoteHost(),
                    "/remote/news/" + newsId + "/comments",
                    station.id(),
                    station.federationPrivateKey(),
                    CommentResponse.class);
            ctx.json(result);
        } else {
            var comments = newsService.findComments(newsId);
            ctx.json(comments.stream().map(this::toCommentResponse).toList());
        }
    }

    private void federatedCreateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int newsId = ctx.pathParamAsClass("newsId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }

        UUID memberUid = session.member().uid();
        String displayName = session.account().fullName().trim();

        if (partner.isRemote()) {
            var body = new RemoteNewsCommentRequest(memberUid, displayName, req.parentId(), req.content());
            var result = federationHttpClient.post(
                    partner.remoteHost(),
                    "/remote/news/" + newsId + "/comments",
                    body,
                    station.id(),
                    station.federationPrivateKey(),
                    CommentResponse.class);
            if (result == null) throw new InternalServerErrorResponse("Failed to create comment on partner");
            ctx.status(HttpStatus.CREATED).json(result);
        } else {
            // Local partner: create comment with federated identity
            var authorIdentity =
                    memberIdentityFactory.fromMemberId(session.member().id());
            var comment = newsService.createComment(
                    partner.stationId(), newsId, req.parentId(), authorIdentity, displayName, req.content());
            eventFederationRepository.cacheName(partner.id(), memberUid, displayName);
            ctx.status(HttpStatus.CREATED).json(toCommentResponse(comment));
        }
    }

    private void federatedUpdateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }

        UUID memberUid = session.member().uid();

        if (partner.isRemote()) {
            var body = new RemoteNewsCommentUpdateRequest(memberUid, req.content());
            var result = federationHttpClient.put(
                    partner.remoteHost(),
                    "/remote/news/comments/" + commentId,
                    body,
                    station.id(),
                    station.federationPrivateKey(),
                    CommentResponse.class);
            if (result == null) throw new InternalServerErrorResponse("Failed to update comment on partner");
            ctx.json(result);
        } else {
            var comment = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
            var sessionIdentity =
                    memberIdentityFactory.fromMemberId(session.member().id());
            if (!sessionIdentity.sameMember(comment.author())) {
                throw new ForbiddenResponse("You can only edit your own comments");
            }
            newsService.updateComment(commentId, req.content());
            var updated = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
            ctx.json(toCommentResponse(updated));
        }
    }

    private void federatedDeleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();

        UUID memberUid = session.member().uid();

        if (partner.isRemote()) {
            boolean success = federationHttpClient.delete(
                    partner.remoteHost(),
                    "/remote/news/comments/" + commentId,
                    station.id(),
                    station.federationPrivateKey());
            if (!success) throw new InternalServerErrorResponse("Failed to delete comment on partner");
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            var comment = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
            var sessionIdentity =
                    memberIdentityFactory.fromMemberId(session.member().id());
            if (!sessionIdentity.sameMember(comment.author())) {
                throw new ForbiddenResponse("You can only delete your own comments");
            }
            if (newsService.deleteComment(partner.stationId(), commentId)) {
                ctx.status(HttpStatus.NO_CONTENT);
            } else {
                throw new NotFoundResponse();
            }
        }
    }

    // -- Federation helpers --

    private FederationPartner resolvePartner(Context ctx, int stationId) {
        var partnerUid = UUID.fromString(ctx.pathParam("stationuid"));
        return federationRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    // -- Request / Response records --

    /**
     * Request body for creating or updating a news article.
     */
    public record NewsRequest(
            String title,
            String contentMarkdown,
            String contentHtml,
            List<String> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            Boolean publicBlog) {}

    /**
     * API response representing a news article with resolved author information.
     */
    public record NewsResponse(
            int id,
            int stationId,
            String title,
            String contentMarkdown,
            String contentHtml,
            MemberIdentity author,
            String authorName,
            Instant publishedAt,
            Instant createdAt,
            List<String> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            int commentCount,
            boolean publicBlog) {}

    /**
     * Request body for creating or updating a comment.
     */
    public record CommentRequest(Integer parentId, String content) {}

    /**
     * API response representing a comment with resolved author information.
     */
    public record CommentResponse(
            int id,
            int newsId,
            Integer parentId,
            MemberIdentity author,
            String authorName,
            String content,
            boolean deleted,
            Instant createdAt) {}

    public record NewsFederationShareResponse(
            boolean shared, String scope, String visibilityRole, List<Integer> partnerIds) {}

    public record RemoteNewsSummary(
            int id,
            String title,
            String contentHtml,
            String authorName,
            String publishedAt,
            int commentCount,
            String visibilityRole) {}

    public record RemoteNewsDetail(
            int id,
            String title,
            String contentMarkdown,
            String contentHtml,
            String authorName,
            String publishedAt,
            int commentCount,
            String visibilityRole) {}

    /**
     * Request body for setting news federation sharing.
     */
    public record SetNewsFederationShareRequest(String scope, String visibilityRole, List<Integer> partnerIds) {}

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

    // --- Public Blog ---

    private int resolvePublicStation(Context ctx) {
        String param = ctx.pathParam("stationUid");
        try {
            return stationRepository
                    .findByUid(UUID.fromString(param))
                    .orElseThrow(NotFoundResponse::new)
                    .id();
        } catch (IllegalArgumentException e) {
            return stationRepository
                    .findBySlug(param)
                    .orElseThrow(NotFoundResponse::new)
                    .id();
        }
    }

    private void publicBlogList(Context ctx) {
        int stationId = resolvePublicStation(ctx);
        var station = stationRepository.findById(stationId).orElseThrow(NotFoundResponse::new);
        if (!station.publicBlogEnabled()) throw new NotFoundResponse();
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        var entries = newsService.findPublicBlogEntries(stationId, offset, limit);
        ctx.json(entries.stream()
                .map(n -> new PublicBlogEntry(
                        n.id(),
                        n.title(),
                        n.contentHtml(),
                        n.author() != null
                                ? memberNameResolver.resolveDisplay(n.author()).name()
                                : "",
                        n.publishedAt()))
                .toList());
    }

    private void publicBlogDetail(Context ctx) {
        int stationId = resolvePublicStation(ctx);
        var station = stationRepository.findById(stationId).orElseThrow(NotFoundResponse::new);
        if (!station.publicBlogEnabled()) throw new NotFoundResponse();
        int blogId = ctx.pathParamAsClass("blogId", Integer.class).get();
        var news = newsService.findById(blogId).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != stationId || !news.publicBlog() || news.publishedAt() == null || news.restricted()) {
            throw new NotFoundResponse();
        }
        var authorName = news.author() != null
                ? memberNameResolver.resolveDisplay(news.author()).name()
                : "";
        ctx.json(new PublicBlogEntry(news.id(), news.title(), news.contentHtml(), authorName, news.publishedAt()));
    }

    public record PublicBlogEntry(int id, String title, String contentHtml, String authorName, Instant publishedAt) {}

    /** Response shape for {@code GET /api/v1/news/{id}/views} (editors only). */
    public record NewsViewsResponse(List<NewsViewerEntry> seen, List<NewsViewerEntry> unseen) {}

    /**
     * One viewer entry in the seen/unseen lists. {@code seenAt} is {@code null} for the
     * unseen branch, otherwise the moment of the first view.
     */
    public record NewsViewerEntry(MemberIdentity member, Instant seenAt) {}
}
