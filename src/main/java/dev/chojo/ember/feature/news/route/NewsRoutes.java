/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.entity.NewsViewer;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

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
    private final EmailService emailService;

    @Inject
    public NewsRoutes(
            NewsService newsService,
            NewsFederationService newsFederationService,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository,
            EventFederationRepository eventFederationRepository,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory,
            EmailService emailService) {
        this.newsService = newsService;
        this.newsFederationService = newsFederationService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
        this.eventFederationRepository = eventFederationRepository;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
        this.emailService = emailService;
    }

    private static NewsSearchResult toSearchResult(News news) {
        String md = news.contentMarkdown() != null ? news.contentMarkdown() : "";
        String summary = md.length() > 200 ? md.substring(0, 200).trim() + "…" : md.trim();
        return new NewsSearchResult(news.publicUid(), news.title(), summary, news.publishedAt());
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/news", this::list, StationPermission.LOGIN);
        routes.get(prefix + "/news/search", this::search, StationPermission.PAGE_EDIT);
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
        routes.get(prefix + "/news/{id}/view-count", this::getViewCount, StationPermission.NEWS_EDIT);
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
        routes.get(prefix + "/public/station/{stationUid}/blog.rss", ctx -> publicBlogFeed(ctx, "rss_2.0"));
        routes.get(prefix + "/public/station/{stationUid}/blog.atom", ctx -> publicBlogFeed(ctx, "atom_1.0"));
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
        int memberId = session.member().id();
        ctx.json(newsList.stream()
                .map(n -> toResponse(n, session.hasPermission(StationPermission.NEWS_MANAGER), memberId))
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
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        int memberId = session.member().id();
        newsService
                .findById(id)
                .ifPresentOrElse(
                        news -> ctx.json(
                                toResponse(news, session.hasPermission(StationPermission.NEWS_MANAGER), memberId)),
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
        ctx.status(HttpStatus.CREATED)
                .json(toResponse(result, true, session.member().id()));
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
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
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
                            ctx.json(toResponse(result, true, session.member().id()));
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
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        if (newsService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    /**
     * Converts a {@link News} entity to an API response, resolving the author name, comment
     * count, and view stats for the requesting member.
     *
     * @param news                the news entity
     * @param includeRestrictions whether to include group restriction IDs in the response
     * @param viewerMemberId      the member ID of the requesting user (for {@code viewedByMe})
     * @return the news response DTO
     */
    private NewsResponse toResponse(News news, boolean includeRestrictions, int viewerMemberId) {
        var resolved = news.author() != null ? memberNameResolver.resolveDisplay(news.author()) : null;
        String authorName = resolved != null && resolved.name() != null ? resolved.name() : "";
        List<StationUserType> userTypes = List.of();
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
        int viewCount = newsService.countViews(news.id());
        boolean viewedByMe = newsService.hasViewed(news.id(), viewerMemberId);
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
                news.publicBlog(),
                viewCount,
                viewedByMe);
    }

    @OpenApi(
            path = "/api/v1/news/search",
            methods = HttpMethod.GET,
            summary = "Search published, unrestricted news entries for the page-editor picker",
            description = "Returns a lightweight result shape (publicUid, title, summary, publishedAt)"
                    + " scoped to the caller's station. Backs the NEWS_TEASER cell picker. Empty"
                    + " query returns the most recent entries so the picker has something to show"
                    + " on first focus.",
            tags = {"News"},
            queryParams = {@OpenApiParam(name = "q"), @OpenApiParam(name = "limit", type = Integer.class)},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsSearchResult[].class)))
    private void search(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String q = ctx.queryParam("q");
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(5);
        int limit = Math.clamp(requested, 1, 20);
        var results = newsService.findPublicBlogEntries(session.stationId(), q, 0, limit).stream()
                .map(NewsRoutes::toSearchResult)
                .toList();
        ctx.json(results);
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
        int newsId = pathInt(ctx, "id");
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
        int newsId = pathInt(ctx, "id");
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
        int commentId = pathInt(ctx, "commentId");
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
        int commentId = pathInt(ctx, "commentId");
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
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
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
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        var summary = newsService.findViewerSummary(id, session.stationId());
        ctx.json(new NewsViewsResponse(
                summary.seen().stream().map(this::toViewerEntry).toList(),
                summary.unseen().stream().map(this::toViewerEntry).toList()));
    }

    @OpenApi(
            path = "/api/v1/news/{id}/view-count",
            methods = HttpMethod.GET,
            summary = "Get the current view count for a news entry",
            description = "Editor-only. Lightweight endpoint for the badge to refresh after a view is recorded.",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsViewCountResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getViewCount(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        ctx.json(new NewsViewCountResponse(newsService.countViews(id)));
    }

    private NewsViewerEntry toViewerEntry(NewsViewer viewer) {
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
        return CommentResponseMapper.fromNews(memberNameResolver, comment);
    }

    // -- Federation sharing management --

    /**
     * Reads the news id path parameter and confirms the news belongs to the caller's
     * station, throwing {@code 404} when it is absent or belongs to another station.
     *
     * @param ctx the request context
     * @return the owned news id
     */
    private int requireOwnedNewsId(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        return id;
    }

    private void getFederationShare(Context ctx) {
        int id = requireOwnedNewsId(ctx);
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
        int id = requireOwnedNewsId(ctx);
        var req = ctx.bodyAsClass(SetNewsFederationShareRequest.class);
        NewsVisibilityRole visibilityRole =
                req.visibilityRole() != null ? req.visibilityRole() : NewsVisibilityRole.MEMBER;
        newsFederationService.setShare(
                id, req.scope(), visibilityRole, req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new NewsFederationShareResponse(true, req.scope(), visibilityRole, null));
    }

    private void removeFederationShare(Context ctx) {
        int id = requireOwnedNewsId(ctx);
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

    private void remoteGetNews(Context ctx) {
        var partner = requireFederationPartner(ctx);
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
        var partner = requireFederationPartner(ctx);
        int newsId = pathInt(ctx, "newsId");
        requireSharedNews(partner, newsId);
        var comments = newsService.findComments(newsId);
        ctx.json(comments.stream().map(this::toCommentResponse).toList());
    }

    private void remoteCreateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
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
        var partner = requireFederationPartner(ctx);
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
        var partner = requireFederationPartner(ctx);
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

    // -- Federated proxy endpoints (user-facing, bearer auth) --

    private void federatedListNews(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(newsFederationService.browseFederatedNews(session.stationId()));
    }

    private void federatedGetNews(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int newsId = pathInt(ctx, "newsId");
        var news = newsFederationService.getFederatedNews(session.stationId(), stationUid, newsId);
        ctx.json(news);
    }

    private void federatedListComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int newsId = pathInt(ctx, "newsId");

        if (partner.isRemote()) {
            var result = federationHttpClient.getList(
                    partner.remoteHost(),
                    "/remote/news/" + newsId + "/comments",
                    partner.partnerStationId(),
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
        int newsId = pathInt(ctx, "newsId");
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
                    partner.partnerStationId(),
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
        int commentId = pathInt(ctx, "commentId");
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
                    partner.partnerStationId(),
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
        int commentId = pathInt(ctx, "commentId");

        if (partner.isRemote()) {
            boolean success = federationHttpClient.delete(
                    partner.remoteHost(),
                    "/remote/news/comments/" + commentId,
                    partner.partnerStationId(),
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
        var partnerUid = pathUuid(ctx, "stationuid");
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
                        n.publicUid(),
                        n.title(),
                        n.contentHtml(),
                        n.author() != null
                                ? memberNameResolver.resolveDisplay(n.author()).name()
                                : "",
                        n.publishedAt()))
                .toList());
    }

    /**
     * Renders the station's public blog as an RSS 2.0 or Atom 1.0 feed. Capped at the most
     * recent 50 entries to keep the payload bounded; readers fetch incrementally via the
     * existing JSON endpoint when they want older posts.
     */
    private void publicBlogFeed(Context ctx, String feedType) {
        int stationId = resolvePublicStation(ctx);
        var station = stationRepository.findById(stationId).orElseThrow(NotFoundResponse::new);
        if (!station.publicBlogEnabled()) throw new NotFoundResponse();
        String stationUid = station.uid().toString();
        String baseUrl = emailService.getBaseUrl();
        String blogUrl = baseUrl + "/public/station/" + stationUid + "/blog";

        var posts = newsService.findPublicBlogEntries(stationId, 0, 50);

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType(feedType);
        feed.setTitle(station.name() + " — Blog");
        feed.setDescription(station.name() + " public blog");
        feed.setLink(blogUrl);
        feed.setLanguage(station.locale());
        if ("atom_1.0".equals(feedType)) {
            feed.setUri("urn:ember:blog:" + stationUid);
        }

        var entries = new ArrayList<SyndEntry>(posts.size());
        for (var post : posts) {
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(post.title());
            entry.setLink(blogUrl + "/" + post.id());
            entry.setUri("urn:ember:blog:" + stationUid + ":" + post.publicUid());
            if (post.publishedAt() != null) {
                var date = Date.from(post.publishedAt());
                entry.setPublishedDate(date);
                entry.setUpdatedDate(date);
            }
            if (post.author() != null) {
                entry.setAuthor(memberNameResolver.resolveDisplay(post.author()).name());
            }
            SyndContent content = new SyndContentImpl();
            content.setType("text/html");
            content.setValue(post.contentHtml());
            entry.setContents(List.of(content));
            entries.add(entry);
        }
        feed.setEntries(entries);

        String contentType = "atom_1.0".equals(feedType) ? "application/atom+xml" : "application/rss+xml";
        try {
            var output = new SyndFeedOutput();
            ctx.contentType(contentType + "; charset=utf-8");
            ctx.header("Cache-Control", "public, max-age=3600");
            ctx.result(output.outputString(feed));
        } catch (Exception e) {
            throw new InternalServerErrorResponse("Failed to render blog feed");
        }
    }

    private void publicBlogDetail(Context ctx) {
        int stationId = resolvePublicStation(ctx);
        var station = stationRepository.findById(stationId).orElseThrow(NotFoundResponse::new);
        if (!station.publicBlogEnabled()) throw new NotFoundResponse();
        int blogId = pathInt(ctx, "blogId");
        var news = newsService.findById(blogId).orElseThrow(NotFoundResponse::new);
        if (news.stationId() != stationId || !news.publicBlog() || news.publishedAt() == null || news.restricted()) {
            throw new NotFoundResponse();
        }
        var authorName = news.author() != null
                ? memberNameResolver.resolveDisplay(news.author()).name()
                : "";
        ctx.json(new PublicBlogEntry(
                news.id(), news.publicUid(), news.title(), news.contentHtml(), authorName, news.publishedAt()));
    }

    /**
     * Request body for creating or updating a news article.
     */
    public record NewsRequest(
            String title,
            String contentMarkdown,
            String contentHtml,
            List<StationUserType> userTypes,
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
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            int commentCount,
            boolean publicBlog,
            int viewCount,
            boolean viewedByMe) {}

    /**
     * Request body for creating or updating a comment.
     */
    public record CommentRequest(Integer parentId, String content) {}

    public record NewsFederationShareResponse(
            boolean shared, ShareScope scope, NewsVisibilityRole visibilityRole, List<Integer> partnerIds) {}

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

    // --- Public Blog ---

    /**
     * Request body for setting news federation sharing.
     */
    public record SetNewsFederationShareRequest(
            ShareScope scope, NewsVisibilityRole visibilityRole, List<Integer> partnerIds) {}

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

    public record PublicBlogEntry(
            int id, UUID publicUid, String title, String contentHtml, String authorName, Instant publishedAt) {}

    /**
     * Response shape for {@code GET /api/v1/news/{id}/views} (editors only).
     */
    public record NewsViewsResponse(List<NewsViewerEntry> seen, List<NewsViewerEntry> unseen) {}

    /**
     * One viewer entry in the seen/unseen lists. {@code seenAt} is {@code null} for the
     * unseen branch, otherwise the moment of the first view.
     */
    public record NewsViewerEntry(MemberIdentity member, Instant seenAt) {}

    /**
     * Lightweight response for {@code GET /api/v1/news/{id}/view-count} (editors only).
     */
    public record NewsViewCountResponse(int count) {}

    /**
     * Lightweight picker result shape for {@code GET /api/v1/news/search}. Exposes the public
     * UUID — never the internal integer id — so cell configs that reference this entry survive
     * station-transfer renumbering.
     */
    public record NewsSearchResult(UUID publicUid, String title, String summary, Instant publishedAt) {}
}
