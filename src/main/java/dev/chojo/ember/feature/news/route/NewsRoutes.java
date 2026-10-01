/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEnclosureImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.route.SaveBlocksRequest;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsAttachment;
import dev.chojo.ember.feature.news.entity.NewsViewer;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.news.service.PublicBlogService;
import dev.chojo.ember.util.Markdown;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Provides endpoints for CRUD operations on news articles and comments, view tracking,
 * federation sharing configuration and the station's public blog.
 * The federated consumer endpoints live in {@link FederatedNewsRoutes}, the server-to-server
 * endpoints in {@link RemoteNewsRoutes}.
 */
@Singleton
public class NewsRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(NewsRoutes.class);
    private static final int SUMMARY_LENGTH = 200;
    private static final int SEARCH_LIMIT = 50;

    private final NewsService newsService;
    private final CommentService commentService;
    private final NewsAttachmentService attachmentService;
    private final NewsFederationService newsFederationService;
    private final PublicBlogService publicBlogs;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;
    private final EmailService emailService;

    @Inject
    public NewsRoutes(
            NewsService newsService,
            CommentService commentService,
            NewsAttachmentService attachmentService,
            NewsFederationService newsFederationService,
            PublicBlogService publicBlogs,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory,
            EmailService emailService) {
        this.newsService = newsService;
        this.commentService = commentService;
        this.attachmentService = attachmentService;
        this.newsFederationService = newsFederationService;
        this.publicBlogs = publicBlogs;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
        this.emailService = emailService;
    }

    private static NewsSearchResult toSearchResult(News news) {
        return new NewsSearchResult(news.publicUid(), news.title(), summaryOf(news), news.publishedAt());
    }

    /** The opening words of an entry, its markup taken off, for a picker row or a news block. */
    private static String summaryOf(News news) {
        String text = Markdown.toPlainText(news.contentMarkdown() != null ? news.contentMarkdown() : "");
        return text.length() > SUMMARY_LENGTH
                ? text.substring(0, SUMMARY_LENGTH).trim() + "…"
                : text.trim();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/news", this::list, StationPermission.LOGIN);
        routes.get(
                prefix + "/news/search",
                this::search,
                StationPermission.PAGE_EDIT,
                StationPermission.NEWS_EDIT,
                StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/news/embed/{newsUid}", this::memberNewsTeaser, StationPermission.LOGIN);
        routes.get(prefix + "/news/{id}", this::get, StationPermission.LOGIN);
        routes.post(prefix + "/news", this::create, StationPermission.NEWS_EDIT);
        routes.put(prefix + "/news/{id}", this::update, StationPermission.NEWS_EDIT);
        routes.delete(prefix + "/news/{id}", this::delete, StationPermission.NEWS_EDIT);
        routes.get(prefix + "/news/{id}/comments", this::listComments, StationPermission.LOGIN);
        routes.post(prefix + "/news/{id}/comments", this::createComment, StationPermission.LOGIN);
        routes.put(prefix + "/news/comments/{commentId}", this::updateComment, StationPermission.LOGIN);
        routes.delete(prefix + "/news/comments/{commentId}", this::deleteComment, StationPermission.LOGIN);

        routes.put(prefix + "/news/{id}/blocks", this::saveBlocks, StationPermission.NEWS_EDIT);
        routes.post(prefix + "/news/{id}/blocks/enable", this::enableBlocks, StationPermission.NEWS_EDIT);

        routes.post(
                prefix + "/news/attachments/{attachmentId}/label",
                this::relabelAttachment,
                StationPermission.NEWS_EDIT);
        routes.delete(prefix + "/news/attachments/{attachmentId}", this::detachAttachment, StationPermission.NEWS_EDIT);
        routes.post(prefix + "/news/{id}/attachments", this::attachFile, StationPermission.NEWS_EDIT);
        routes.put(prefix + "/news/{id}/attachments/order", this::reorderAttachments, StationPermission.NEWS_EDIT);

        routes.post(prefix + "/news/{id}/view", this::recordView, StationPermission.LOGIN);
        routes.get(prefix + "/news/{id}/view-count", this::getViewCount, StationPermission.NEWS_EDIT);
        routes.get(prefix + "/news/{id}/views", this::listViewers, StationPermission.NEWS_EDIT);

        routes.get(prefix + "/news/{id}/federation", this::getFederationShare, StationPermission.NEWS_FEDERATE);
        routes.put(prefix + "/news/{id}/federation", this::setFederationShare, StationPermission.NEWS_FEDERATE);
        routes.delete(prefix + "/news/{id}/federation", this::removeFederationShare, StationPermission.NEWS_FEDERATE);

        routes.get(prefix + "/public/station/{stationUid}/blog", this::publicBlogList);
        routes.get(prefix + "/public/station/{stationUid}/blog/{blogId}", this::publicBlogDetail);
        routes.get(prefix + "/public/station/{stationUid}/news-teaser/{newsUid}", this::publicNewsTeaser);
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
                    session.stationId(), session.member().id(), false, offset, limit);
        }
        int memberId = session.member().id();
        ctx.json(newsList.stream()
                .map(n -> toResponse(n, session.hasPermission(StationPermission.NEWS_MANAGER), memberId, false))
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
        var news = requireReadable(ctx, id);
        ctx.json(toResponse(news, session.hasPermission(StationPermission.NEWS_MANAGER), memberId));
    }

    /**
     * The entry behind an id, if the caller may read it, and a 404 otherwise.
     */
    private News requireReadable(Context ctx, int id) {
        return newsService.requireReadable(UserSession.from(ctx), id);
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
            throw Refusal.NEWS_NEEDS_A_TITLE.raise();
        }
        boolean rich = request.contentMode() == ContentMode.RICH;
        requireBody(rich, request.contentMarkdown());
        var authorIdentity = memberIdentityFactory.fromMemberId(session.member().id());
        var news = newsService.create(
                session.stationId(),
                request.title(),
                request.contentMarkdown(),
                authorIdentity,
                request.userTypes() != null ? request.userTypes() : List.of(),
                request.groupIds() != null ? request.groupIds() : List.of(),
                request.tagIds() != null ? request.tagIds() : List.of(),
                request.memberIds() != null ? request.memberIds() : List.of());
        if (rich) {
            newsService.switchToRich(news.id());
        }
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
                            throw Refusal.NEWS_NOT_HERE_ON_UPDATE.raise();
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
            throw Refusal.NEWS_NOT_DELETED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/news/{id}/blocks",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SaveBlocksRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsResponse.class)))
    private void saveBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        var request = ctx.bodyAsClass(SaveBlocksRequest.class);
        var session = UserSession.from(ctx);
        var saved =
                newsService.saveBlocks(id, request.toRowData()).orElseThrow(Refusal.NEWS_NOT_HERE_ON_BLOCK_SAVE::raise);
        ctx.json(toResponse(saved, true, session.member().id()));
    }

    /**
     * Turns a plain entry into one built from blocks. What the author already wrote becomes a
     * single markdown block, which they then split up as they like.
     */
    @OpenApi(
            path = "/api/v1/news/{id}/blocks/enable",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsResponse.class)))
    private void enableBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        var switched = newsService.switchToRich(id).orElseThrow(Refusal.NEWS_NOT_HERE_ON_BLOCK_SWITCH::raise);
        ctx.json(toResponse(switched, true, session.member().id()));
    }

    /**
     * Resolves an attachment and asserts the entry it hangs off belongs to the caller's station,
     * so an attachment id from one station cannot be relabelled or detached from another.
     */
    private NewsAttachment requireOwnedAttachment(Context ctx, int attachmentId) {
        var attachment = attachmentService.find(attachmentId).orElseThrow(Refusal.NEWS_ATTACHMENT_NOT_HERE::raise);
        requireOwnedOrNotFound(ctx, attachment.newsId(), newsService::findById, News::stationId);
        return attachment;
    }

    @OpenApi(
            path = "/api/v1/news/{id}/attachments",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsAttachmentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = NewsAttachment.class)))
    private void attachFile(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        var request = ctx.bodyAsClass(NewsAttachmentRequest.class);
        if (request.fileId() == null) throw Refusal.NEWS_ATTACHMENT_FILE_NOT_NAMED.raise();
        ctx.status(HttpStatus.CREATED)
                .json(attachmentService.attach(id, session.stationId(), request.fileId(), request.label()));
    }

    @OpenApi(
            path = "/api/v1/news/attachments/{attachmentId}/label",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsAttachmentRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void relabelAttachment(Context ctx) {
        int attachmentId = pathInt(ctx, "attachmentId");
        requireOwnedAttachment(ctx, attachmentId);
        var request = ctx.bodyAsClass(NewsAttachmentRequest.class);
        if (!attachmentService.relabel(attachmentId, request.label()))
            throw Refusal.NEWS_ATTACHMENT_NOT_RELABELLED.raise();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/news/{id}/attachments/order",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsAttachmentOrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorderAttachments(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, newsService::findById, News::stationId);
        var request = ctx.bodyAsClass(NewsAttachmentOrderRequest.class);
        attachmentService.reorder(id, request.attachmentIds() != null ? request.attachmentIds() : List.of());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/news/attachments/{attachmentId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void detachAttachment(Context ctx) {
        int attachmentId = pathInt(ctx, "attachmentId");
        requireOwnedAttachment(ctx, attachmentId);
        if (!attachmentService.detach(attachmentId)) throw Refusal.NEWS_ATTACHMENT_NOT_DETACHED.raise();
        ctx.status(HttpStatus.NO_CONTENT);
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
        return toResponse(news, includeRestrictions, viewerMemberId, true);
    }

    /**
     * @param withBlocks whether to load the blocks of a rich entry. A listing leaves them out: a
     *                   list row shows a summary, and loading every entry's blocks to build one
     *                   would ask the database once per row for something nobody reads.
     */
    private NewsResponse toResponse(News news, boolean includeRestrictions, int viewerMemberId, boolean withBlocks) {
        var resolved = news.author() != null ? memberNameResolver.resolveDisplay(news.author()) : null;
        String authorName = resolved != null && resolved.name() != null ? resolved.name() : "";
        // The instance is nobody's member, so there is no identity to resolve and no avatar to
        // draw. Its own name stands in, and the badge beside it says where the entry came from.
        if (news.systemEntry()) {
            authorName = NewsService.SYSTEM_AUTHOR_NAME;
        }
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
        int commentCount = commentService.count(CommentEntityType.NEWS, news.id());
        int viewCount = newsService.countViews(news.id());
        var attachments = attachmentService.list(news.id());
        boolean blocksWanted = withBlocks && news.contentMode() == ContentMode.RICH;
        List<ContentRow> rows = blocksWanted ? newsService.loadBlocks(news) : List.of();
        List<ContentRow> describedRows = blocksWanted ? newsService.describedBlocks(news) : List.of();
        boolean viewedByMe = newsService.hasViewed(news.id(), viewerMemberId);
        return new NewsResponse(
                news.id(),
                news.systemEntry() ? null : news.stationId(),
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
                news.restricted(),
                news.publicBlog(),
                viewCount,
                viewedByMe,
                attachments,
                news.contentMode(),
                rows,
                describedRows,
                news.systemEntry());
    }

    @OpenApi(
            path = "/api/v1/news/search",
            methods = HttpMethod.GET,
            summary = "Search the station's news for the news block picker",
            description = "The caller's station's entries that every reader of the scope may read, newest first,"
                    + " whose title contains the query, case-insensitive. scope=PUBLIC (the default, for a page)"
                    + " offers published, unrestricted entries on the public blog; scope=MEMBERS (for a news or"
                    + " wiki article) offers every published, unrestricted entry. An empty query returns the"
                    + " newest entries. At most limit entries are returned, and more says whether there are"
                    + " further ones to ask for with a larger limit.",
            tags = {"News"},
            queryParams = {
                @OpenApiParam(name = "q"),
                @OpenApiParam(name = "limit", type = Integer.class),
                @OpenApiParam(name = "scope", type = BlockAudience.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsSearchPage.class)))
    private void search(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String q = ctx.queryParam("q");
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(5);
        int limit = Math.clamp(requested, 1, SEARCH_LIMIT);
        var audience = BlockAudience.named(ctx.queryParam("scope"));
        var found = newsService.findOpenEntries(session.stationId(), audience, q, 0, limit + 1);
        var entries =
                found.stream().limit(limit).map(NewsRoutes::toSearchResult).toList();
        ctx.json(new NewsSearchPage(entries, found.size() > limit));
    }

    @OpenApi(
            path = "/api/v1/news/{id}/comments",
            methods = HttpMethod.GET,
            summary = "List comments for a news entry",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void listComments(Context ctx) {
        int newsId = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        var target = commentService.requireReadable(session, CommentEntityType.NEWS, newsId);
        ctx.json(commentService.list(CommentEntityType.NEWS, newsId, commentFilter(session, target)).stream()
                .map(this::toCommentResponse)
                .toList());
    }

    /**
     * Which comments a station reads under an entry. Under a system entry every station is talking
     * at once, and what one station says is its own business: a station is shown its own part of
     * the conversation. A station entry is read by that station alone, so there is nothing to
     * separate.
     */
    private static CommentFilter commentFilter(UserSession session, TargetInfo target) {
        if (!target.systemEntry()) return CommentFilter.ALL;
        return new CommentFilter.FromStation(
                Objects.requireNonNull(session.stationUid(), "a reader of a news entry is a member of a station"));
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
            throw Refusal.NEWS_COMMENT_NEEDS_TEXT.raise();
        }
        var comment = commentService.create(
                session,
                CommentEntityType.NEWS,
                newsId,
                commentWriter(session),
                new NewComment(request.parentId(), null, request.content()));
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
        var comment = commentService
                .findById(CommentEntityType.NEWS, commentId)
                .orElseThrow(Refusal.NEWS_COMMENT_NOT_HERE_ON_UPDATE::raise);
        if (!commentService.mayModify(session, commentActor(session), comment, Moderation.EDIT)) {
            throw Refusal.NEWS_COMMENT_NOT_YOURS_TO_EDIT.raise();
        }
        var request = ctx.bodyAsClass(CommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw Refusal.NEWS_COMMENT_NEEDS_TEXT_ON_UPDATE.raise();
        }
        var updated = commentService
                .update(comment, commentWriter(session), request.content())
                .orElseThrow(Refusal.NEWS_COMMENT_NOT_HERE_AFTER_UPDATE::raise);
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
        var comment = commentService
                .findById(CommentEntityType.NEWS, commentId)
                .orElseThrow(Refusal.NEWS_COMMENT_NOT_HERE_ON_DELETE::raise);
        commentService.requireSameStation(session, comment);
        if (!commentService.mayModify(session, commentActor(session), comment, Moderation.DELETE)) {
            throw Refusal.NEWS_COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        if (!commentService.delete(comment)) {
            throw Refusal.NEWS_COMMENT_NOT_DELETED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private MemberIdentity commentActor(UserSession session) {
        return memberIdentityFactory.local(session.stationId(), session.member().id());
    }

    private CommentWriter commentWriter(UserSession session) {
        return CommentWriter.local(
                commentActor(session), NameParts.of(session.account()).called());
    }

    @OpenApi(
            path = "/api/v1/news/{id}/view",
            methods = HttpMethod.POST,
            summary = "Record that the current member fully saw a news entry",
            description =
                    "Idempotent - repeated calls from the same member are silently ignored. The client fires this once the news entry is fully visible in the viewport.",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void recordView(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        // Whatever a member may read, they may be recorded as having read, which includes what the
        // instance published to every station.
        requireReadable(ctx, id);
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
        requireReadable(ctx, id);
        ctx.json(new NewsViewCountResponse(newsService.countViews(id)));
    }

    private NewsViewerEntry toViewerEntry(NewsViewer viewer) {
        var enriched = memberNameResolver.enrichDisplay(viewer.member());
        return new NewsViewerEntry(enriched != null ? enriched : viewer.member(), viewer.seenAt());
    }

    /**
     * Converts a comment to an API response, resolving the author name.
     *
     * @param comment the comment entity
     * @return the comment response DTO
     */
    private CommentResponse toCommentResponse(Comment comment) {
        return CommentResponseMapper.fromNews(memberNameResolver, comment);
    }

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

    @OpenApi(
            path = "/api/v1/news/{id}/federation",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = NewsFederationShareResponse.class)))
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

    @OpenApi(
            path = "/api/v1/news/{id}/federation",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetNewsFederationShareRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = NewsFederationShareResponse.class)))
    private void setFederationShare(Context ctx) {
        int id = requireOwnedNewsId(ctx);
        var req = ctx.bodyAsClass(SetNewsFederationShareRequest.class);
        NewsVisibilityRole visibilityRole =
                req.visibilityRole() != null ? req.visibilityRole() : NewsVisibilityRole.MEMBER;
        newsFederationService.setShare(
                id, req.scope(), visibilityRole, req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new NewsFederationShareResponse(true, req.scope(), visibilityRole, null));
    }

    @OpenApi(
            path = "/api/v1/news/{id}/federation",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void removeFederationShare(Context ctx) {
        int id = requireOwnedNewsId(ctx);
        newsFederationService.removeShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/blog",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicBlogEntry[].class)))
    private void publicBlogList(Context ctx) {
        int stationId = publicBlogs
                .openBlog(
                        ctx.pathParam("stationUid"),
                        Refusal.STATION_NOT_HERE_BEHIND_BLOG_LIST,
                        Refusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST)
                .id();
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        var entries = newsService.findPublicBlogEntries(stationId, offset, limit);
        var attachmentsByNews =
                attachmentService.listFor(entries.stream().map(News::id).toList());
        ctx.json(entries.stream()
                .map(n -> new PublicBlogEntry(
                        n.id(),
                        n.publicUid(),
                        n.title(),
                        n.contentHtml(),
                        Objects.requireNonNullElse(memberNameResolver.resolve(n.author()), ""),
                        n.publishedAt(),
                        attachmentsByNews.getOrDefault(n.id(), List.of()),
                        n.contentMode(),
                        List.of()))
                .toList());
    }

    /**
     * Renders the station's public blog as an RSS 2.0 or Atom 1.0 feed. Capped at the most
     * recent 50 entries to keep the payload bounded; readers fetch incrementally via the
     * existing JSON endpoint when they want older posts.
     */
    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/blog.rss",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/blog.atom",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void publicBlogFeed(Context ctx, String feedType) {
        var station = publicBlogs.openBlog(
                ctx.pathParam("stationUid"),
                Refusal.STATION_NOT_HERE_BEHIND_BLOG_FEED,
                Refusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_FEED);
        int stationId = station.id();
        String stationUid = station.uid().toString();
        String baseUrl = emailService.getBaseUrl();
        String blogUrl = baseUrl + "/public/station/" + stationUid + "/blog";

        var posts = newsService.findPublicBlogEntries(stationId, 0, 50);

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType(feedType);
        feed.setTitle(station.name() + " - Blog");
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
            // One enclosure per attachment, which is what a feed reader expects to be handed a
            // file. The body stays what the author wrote.
            var enclosures = new ArrayList<SyndEnclosure>();
            for (var attachment : attachmentService.list(post.id())) {
                SyndEnclosure enclosure = new SyndEnclosureImpl();
                enclosure.setUrl(attachmentService.absoluteUrl(stationId, attachment));
                enclosure.setType(attachment.mimeType());
                enclosure.setLength(attachment.fileSize());
                enclosures.add(enclosure);
            }
            if (!enclosures.isEmpty()) entry.setEnclosures(enclosures);
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
            log.warn("Failed to render the public blog feed of station {}", stationId, e);
            throw Refusal.BLOG_FEED_NOT_MADE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/blog/{blogId}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicBlogEntry.class)))
    private void publicBlogDetail(Context ctx) {
        int stationId = publicBlogs
                .openBlog(
                        ctx.pathParam("stationUid"),
                        Refusal.STATION_NOT_HERE_BEHIND_BLOG_ENTRY,
                        Refusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_ENTRY)
                .id();
        int blogId = pathInt(ctx, "blogId");
        var news = newsService.findById(blogId).orElseThrow(Refusal.PUBLIC_BLOG_ENTRY_NOT_HERE::raise);
        if (news.stationId() != stationId || !news.publicBlog() || news.publishedAt() == null || news.restricted()) {
            throw Refusal.PUBLIC_BLOG_ENTRY_NOT_HERE.raise();
        }
        var authorName = Objects.requireNonNullElse(memberNameResolver.resolve(news.author()), "");
        ctx.json(new PublicBlogEntry(
                news.id(),
                news.publicUid(),
                news.title(),
                news.contentHtml(),
                authorName,
                news.publishedAt(),
                attachmentService.list(news.id()),
                news.contentMode(),
                newsService.describedBlocks(news)));
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/news-teaser/{newsUid}",
            methods = HttpMethod.GET,
            summary = "The news entry a news block names, as the block shows it",
            description = "Answers only an entry of that station that is published, not kept to part of the"
                    + " station and on its public blog. Anything else is the same 404, so a withheld entry"
                    + " cannot be told from a missing one.",
            tags = {"News"},
            pathParams = {
                @OpenApiParam(name = "stationUid", required = true),
                @OpenApiParam(name = "newsUid", required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsTeaser.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void publicNewsTeaser(Context ctx) {
        int stationId = publicBlogs
                .openBlog(
                        ctx.pathParam("stationUid"),
                        Refusal.STATION_NOT_HERE_BEHIND_NEWS_BLOCK,
                        Refusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_NEWS_BLOCK)
                .id();
        ctx.json(teaserOf(stationId, BlockAudience.PUBLIC, pathUuid(ctx, "newsUid")));
    }

    @OpenApi(
            path = "/api/v1/news/embed/{newsUid}",
            methods = HttpMethod.GET,
            summary = "The news entry a news block in a news or wiki article names, as the block shows it",
            description = "Answers only an entry of the caller's station that every member may read: published"
                    + " and not kept to part of the station, whether or not it is on the public blog. Anything"
                    + " else is the same 404, so a withheld entry cannot be told from a missing one.",
            tags = {"News"},
            pathParams = @OpenApiParam(name = "newsUid", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NewsTeaser.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void memberNewsTeaser(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(teaserOf(session.stationId(), BlockAudience.MEMBERS, pathUuid(ctx, "newsUid")));
    }

    private NewsTeaser teaserOf(int stationId, BlockAudience audience, UUID newsUid) {
        var news = newsService
                .findOpenByUid(stationId, audience, newsUid)
                .orElseThrow(Refusal.NEWS_BLOCK_ENTRY_NOT_HERE::raise);
        return new NewsTeaser(news.id(), news.publicUid(), news.title(), summaryOf(news), news.publishedAt());
    }

    /**
     * Request body for creating or updating a news article.
     */
    /**
     * Refuses an entry with nothing in it, unless it is built from blocks.
     *
     * <p>A block entry has no written markdown to give: what it reads as is derived from the
     * blocks, and those are saved once the entry has an id to hang them off. Both the station's
     * entries and the instance's are created this way, so the rule is written down once.
     */
    static void requireBody(boolean rich, String contentMarkdown) {
        if (!rich && (contentMarkdown == null || contentMarkdown.isBlank())) {
            throw Refusal.NEWS_NEEDS_SOMETHING_WRITTEN.raise();
        }
    }

    /**
     * What an author sends. The body arrives as Markdown only: the HTML that goes with it is
     * rendered and sanitised on this side, because it is served back to every reader as markup.
     */
    public record NewsRequest(
            String title,
            String contentMarkdown,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            Boolean publicBlog,
            ContentMode contentMode) {}

    /**
     * API response representing a news article with resolved author information.
     *
     * <p>{@code rows} are the blocks as written, for the editor. {@code describedRows} are the same
     * blocks as a reader sees them, with a picture the entry says nothing about carrying the words
     * of its media file. Keeping the two apart is what stops a save from writing those words into
     * the entry.
     */
    public record NewsResponse(
            int id,
            @Nullable Integer stationId,
            String title,
            String contentMarkdown,
            String contentHtml,
            @Nullable MemberIdentity author,
            String authorName,
            @Nullable Instant publishedAt,
            Instant createdAt,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            int commentCount,
            boolean restricted,
            boolean publicBlog,
            int viewCount,
            boolean viewedByMe,
            List<NewsAttachment> attachments,
            ContentMode contentMode,
            List<ContentRow> rows,
            List<ContentRow> describedRows,
            boolean systemEntry) {}

    /**
     * Request body for creating or updating a comment.
     */
    public record CommentRequest(Integer parentId, String content) {}

    public record NewsFederationShareResponse(
            boolean shared,
            @Nullable ShareScope scope,
            @Nullable NewsVisibilityRole visibilityRole,
            @Nullable List<Integer> partnerIds) {}

    /**
     * Request body for setting news federation sharing.
     */
    public record SetNewsFederationShareRequest(
            ShareScope scope, NewsVisibilityRole visibilityRole, List<Integer> partnerIds) {}

    public record PublicBlogEntry(
            int id,
            UUID publicUid,
            String title,
            String contentHtml,
            String authorName,
            Instant publishedAt,
            List<NewsAttachment> attachments,
            ContentMode contentMode,
            List<ContentRow> rows) {}

    /**
     * Request body for attaching a file to an entry, or for renaming what a reader sees.
     */
    public record NewsAttachmentRequest(Integer fileId, String label) {}

    /**
     * Request body for writing the order the author put the attachments in.
     */
    public record NewsAttachmentOrderRequest(List<Integer> attachmentIds) {}

    /**
     * Response shape for {@code GET /api/v1/news/{id}/views} (editors only).
     */
    public record NewsViewsResponse(List<NewsViewerEntry> seen, List<NewsViewerEntry> unseen) {}

    /**
     * One viewer entry in the seen/unseen lists. {@code seenAt} is {@code null} for the
     * unseen branch, otherwise the moment of the first view.
     */
    public record NewsViewerEntry(
            MemberIdentity member, @Nullable Instant seenAt) {}

    /**
     * Lightweight response for {@code GET /api/v1/news/{id}/view-count} (editors only).
     */
    public record NewsViewCountResponse(int count) {}

    /**
     * Lightweight picker result shape for {@code GET /api/v1/news/search}. Exposes the public
     * UUID - never the internal integer id - so cell configs that reference this entry survive
     * station-transfer renumbering.
     */
    public record NewsSearchResult(UUID publicUid, String title, String summary, Instant publishedAt) {}

    /**
     * One page of the news block picker's search.
     *
     * @param entries the entries found, newest first
     * @param more    whether a larger limit would find further entries
     */
    public record NewsSearchPage(List<NewsSearchResult> entries, boolean more) {}

    /**
     * A news entry as a news block shows it.
     *
     * @param id          the entry's id, which its address on the public blog and in the station is built from
     * @param publicUid   the public id the block names it by
     * @param title       what the entry is called now
     * @param summary     the opening words of the entry, markup taken off
     * @param publishedAt when it was published
     */
    public record NewsTeaser(int id, UUID publicUid, String title, String summary, Instant publishedAt) {}
}
