/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
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
import java.util.Map;

@Singleton
public class NewsRoutes implements Routes {
    private final NewsService newsService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final NotificationService notificationService;

    @Inject
    public NewsRoutes(
            NewsService newsService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            NotificationService notificationService) {
        this.newsService = newsService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/news", this::list, Roles.LOGIN);
        routes.get(prefix + "/news/{id}", this::get, Roles.LOGIN);
        routes.post(prefix + "/news", this::create, Roles.NEWS_MANAGEMENT);
        routes.put(prefix + "/news/{id}", this::update, Roles.NEWS_MANAGEMENT);
        routes.delete(prefix + "/news/{id}", this::delete, Roles.NEWS_MANAGEMENT);
        routes.get(prefix + "/news/{id}/comments", this::listComments, Roles.LOGIN);
        routes.post(prefix + "/news/{id}/comments", this::createComment, Roles.LOGIN);
        routes.put(prefix + "/news/comments/{commentId}", this::updateComment, Roles.LOGIN);
        routes.delete(prefix + "/news/comments/{commentId}", this::deleteComment, Roles.LOGIN);
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
        if (session.hasRole(Roles.NEWS_MANAGEMENT)) {
            newsList = newsService.findByStation(session.stationId(), offset, limit);
        } else {
            newsList = newsService.findVisibleForMember(
                    session.stationId(), session.member().id(), offset, limit);
        }
        ctx.json(newsList.stream()
                .map(n -> toResponse(n, session.hasRole(Roles.NEWS_MANAGEMENT)))
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
                .ifPresentOrElse(news -> ctx.json(toResponse(news, session.hasRole(Roles.NEWS_MANAGEMENT))), () -> {
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
        var news = newsService.create(
                session.stationId(),
                request.title(),
                request.contentMarkdown(),
                request.contentHtml() != null ? request.contentHtml() : "",
                session.member().id(),
                request.groupIds() != null ? request.groupIds() : List.of());
        String authorName = session.account().fullName().trim();
        String preview = request.contentMarkdown().length() > 100
                ? request.contentMarkdown().substring(0, 100) + "..."
                : request.contentMarkdown();
        notificationService.notifyStation(
                session.stationId(),
                NotificationType.NEW_NEWS,
                NotificationData.of(
                        "notification.newNews",
                        Map.of("title", request.title(), "author", authorName, "preview", preview),
                        new NotificationData.NotificationLink("news-list")));
        ctx.status(HttpStatus.CREATED).json(toResponse(news, true));
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
        var request = ctx.bodyAsClass(NewsRequest.class);
        newsService
                .update(
                        id,
                        request.title(),
                        request.contentMarkdown(),
                        request.contentHtml() != null ? request.contentHtml() : "",
                        request.groupIds() != null ? request.groupIds() : List.of())
                .ifPresentOrElse(news -> ctx.json(toResponse(news, true)), () -> {
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
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (newsService.delete(id)) {
            // Remove notifications for this news article and its comments
            notificationService.deleteByTypeContaining(
                    NotificationType.NEW_NEWS,
                    NotificationData.of("notification.newNews", Map.of("title", news.title()))
                            .toJson());
            notificationService.deleteByTypeContaining(
                    NotificationType.NEWS_COMMENT,
                    NotificationData.of("notification.newsComment", Map.of("newsTitle", news.title()))
                            .toJson());
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private NewsResponse toResponse(News news, boolean includeRestrictions) {
        var memberOpt = stationMemberRepository.findById(news.authorId());
        Integer authorAccountId = memberOpt.map(m -> m.accountId()).orElse(null);
        String authorName = memberOpt
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        List<Integer> groupIds = includeRestrictions ? newsService.findGroupRestrictions(news.id()) : List.of();
        int commentCount = newsService.countComments(news.id());
        return new NewsResponse(
                news.id(),
                news.stationId(),
                news.title(),
                news.contentMarkdown(),
                news.contentHtml(),
                news.authorId(),
                authorAccountId,
                authorName,
                news.publishedAt(),
                news.createdAt(),
                groupIds,
                commentCount);
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
        var comment = newsService.createComment(
                newsId, request.parentId(), session.member().id(), request.content());

        // Notify news author and parent comment author about the new comment
        var news = newsService.findById(newsId).orElse(null);
        if (news != null) {
            String commenterName = session.account().fullName().trim();
            String preview =
                    request.content().length() > 100 ? request.content().substring(0, 100) + "..." : request.content();
            var data = NotificationData.of(
                    "notification.newsComment",
                    Map.of("newsTitle", news.title(), "author", commenterName, "preview", preview),
                    new NotificationData.NotificationLink("news-list"));

            // Notify the news author (unless they wrote the comment)
            if (news.authorId() != session.member().id()) {
                notificationService.notifyIfAbsent(news.authorId(), NotificationType.NEWS_COMMENT, data);
            }
            // Notify the parent comment author (if this is a reply)
            if (request.parentId() != null) {
                newsService.findCommentById(request.parentId()).ifPresent(parent -> {
                    if (parent.authorId() != session.member().id() && parent.authorId() != news.authorId()) {
                        notificationService.notifyIfAbsent(parent.authorId(), NotificationType.NEWS_COMMENT, data);
                    }
                });
            }
            // Notify all NEWS_MANAGEMENT members
            var newsMgmtIds =
                    stationMemberRepository.findMembersWithRole(session.stationId(), Roles.NEWS_MANAGEMENT).stream()
                            .map(m -> m.id())
                            .toList();
            notificationService.notifyMembersIfAbsent(
                    newsMgmtIds,
                    NotificationType.NEWS_COMMENT,
                    data,
                    session.member().id());
        }

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
        if (comment.authorId() != session.member().id()) {
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
            summary = "Delete a comment (own or NEWS_MANAGEMENT)",
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
        boolean isAuthor = comment.authorId() == session.member().id();
        boolean canModerate = session.hasRole(Roles.NEWS_MANAGEMENT);
        if (!isAuthor && !canModerate) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        if (newsService.deleteComment(commentId)) {
            // Remove notifications about this comment
            String preview =
                    comment.content().length() > 100 ? comment.content().substring(0, 100) + "..." : comment.content();
            notificationService.deleteByTypeContaining(
                    NotificationType.NEWS_COMMENT,
                    NotificationData.of("notification.newsComment", Map.of("preview", preview))
                            .toJson());
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private CommentResponse toCommentResponse(NewsComment comment) {
        var memberOpt = stationMemberRepository.findById(comment.authorId());
        Integer authorAccountId = memberOpt.map(m -> m.accountId()).orElse(null);
        String authorName = memberOpt
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        return new CommentResponse(
                comment.id(),
                comment.newsId(),
                comment.parentId(),
                comment.authorId(),
                authorAccountId,
                authorName,
                comment.content(),
                comment.createdAt());
    }

    public record NewsRequest(String title, String contentMarkdown, String contentHtml, List<Integer> groupIds) {}

    public record NewsResponse(
            int id,
            int stationId,
            String title,
            String contentMarkdown,
            String contentHtml,
            int authorId,
            Integer authorAccountId,
            String authorName,
            Instant publishedAt,
            Instant createdAt,
            List<Integer> groupIds,
            int commentCount) {}

    public record CommentRequest(Integer parentId, String content) {}

    public record CommentResponse(
            int id,
            int newsId,
            Integer parentId,
            int authorId,
            Integer authorAccountId,
            String authorName,
            String content,
            Instant createdAt) {}
}
