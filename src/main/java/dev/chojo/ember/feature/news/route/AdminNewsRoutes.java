/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.service.NewsService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * What the instance says to every station at once.
 *
 * <p>A system entry is one row belonging to no station, which every station reads in its own news
 * list. Writing, correcting and withdrawing it happen here, and nowhere else: these routes refuse
 * anything that is not a system entry, so an instance administrator cannot reach into a station's
 * own news through them, and a station cannot reach a system entry through its own.
 *
 * <p>The comments are the one place the two views differ on purpose. A station is shown what it
 * wrote; the instance is shown the whole conversation, because a question asked under a notice is
 * asked of the instance and someone has to be able to read it to answer.
 */
@Singleton
public class AdminNewsRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(AdminNewsRoutes.class);

    private final NewsService newsService;
    private final MemberNameResolver memberNameResolver;
    private final MediaLibraryService media;
    private final Api apiConfig;

    @Inject
    public AdminNewsRoutes(
            NewsService newsService, MemberNameResolver memberNameResolver, MediaLibraryService media, Api apiConfig) {
        this.newsService = newsService;
        this.memberNameResolver = memberNameResolver;
        this.media = media;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/news", this::list, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/news", this::create, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/news/{id}", this::get, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/admin/news/{id}", this::update, InstancePermission.ADMINISTRATOR);
        routes.delete(prefix + "/admin/news/{id}", this::retract, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/admin/news/{id}/blocks", this::saveBlocks, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/news/{id}/blocks/enable", this::enableBlocks, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/news/{id}/comments", this::listComments, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/media/files", this::listInstanceFiles, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/media/files", this::uploadInstanceFile, InstancePermission.ADMINISTRATOR);
        routes.delete(
                prefix + "/admin/media/files/{fileId}", this::deleteInstanceFile, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/news",
            methods = HttpMethod.GET,
            summary = "List the entries the instance published to every station",
            tags = {"Admin"},
            queryParams = {
                @OpenApiParam(name = "offset", type = Integer.class),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SystemNewsResponse[].class)))
    private void list(Context ctx) {
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        // A list row shows a summary, so the blocks of a rich entry are left unread: fetching them
        // for every row would ask the database once per row for something the list never shows.
        ctx.json(newsService.findSystem(offset, limit).stream()
                .map(news -> toResponse(news, false))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/admin/news/{id}",
            methods = HttpMethod.GET,
            summary = "Read one entry the instance published",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SystemNewsResponse.class)))
    private void get(Context ctx) {
        ctx.json(toResponse(requireSystemEntry(pathInt(ctx, "id")), true));
    }

    @OpenApi(
            path = "/api/v1/admin/news",
            methods = HttpMethod.POST,
            summary = "Publish an entry to every station",
            tags = {"Admin"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SystemNewsRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = SystemNewsResponse.class)))
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(SystemNewsRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("title is required");
        }
        boolean rich = request.contentMode() == ContentMode.RICH;
        NewsRoutes.requireBody(rich, request.contentMarkdown());
        var news = newsService.createSystem(
                request.title(),
                request.contentMarkdown() != null ? request.contentMarkdown() : "",
                request.contentHtml() != null ? request.contentHtml() : "",
                request.userTypes() != null ? request.userTypes() : List.of(),
                request.publish() == null || request.publish(),
                Boolean.TRUE.equals(request.notifyMembers()));
        if (rich) {
            newsService.switchToRich(news.id());
        }
        ctx.status(HttpStatus.CREATED).json(toResponse(requireSystemEntry(news.id()), true));
    }

    @OpenApi(
            path = "/api/v1/admin/news/{id}",
            methods = HttpMethod.PUT,
            summary = "Correct an entry the instance published",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SystemNewsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SystemNewsResponse.class)))
    private void update(Context ctx) {
        int id = requireSystemEntry(pathInt(ctx, "id")).id();
        var request = ctx.bodyAsClass(SystemNewsRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("title is required");
        }
        newsService
                .update(
                        id,
                        request.title(),
                        request.contentMarkdown() != null ? request.contentMarkdown() : "",
                        request.contentHtml() != null ? request.contentHtml() : "",
                        request.userTypes() != null ? request.userTypes() : List.of(),
                        List.of(),
                        List.of(),
                        List.of())
                .orElseThrow(NotFoundResponse::new);
        ctx.json(toResponse(requireSystemEntry(id), true));
    }

    @OpenApi(
            path = "/api/v1/admin/news/{id}",
            methods = HttpMethod.DELETE,
            summary = "Withdraw an entry from every station",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void retract(Context ctx) {
        int id = requireSystemEntry(pathInt(ctx, "id")).id();
        if (!newsService.delete(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/news/{id}/blocks",
            methods = HttpMethod.PUT,
            summary = "Save the blocks of an entry the instance published",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewsRoutes.SaveBlocksRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SystemNewsResponse.class)))
    private void saveBlocks(Context ctx) {
        int id = requireSystemEntry(pathInt(ctx, "id")).id();
        var request = ctx.bodyAsClass(NewsRoutes.SaveBlocksRequest.class);
        var saved = newsService.saveBlocks(id, request.toRowData()).orElseThrow(NotFoundResponse::new);
        ctx.json(toResponse(saved, true));
    }

    @OpenApi(
            path = "/api/v1/admin/news/{id}/blocks/enable",
            methods = HttpMethod.POST,
            summary = "Write an entry the instance published with the page editor",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SystemNewsResponse.class)))
    private void enableBlocks(Context ctx) {
        int id = requireSystemEntry(pathInt(ctx, "id")).id();
        var switched = newsService.switchToRich(id).orElseThrow(NotFoundResponse::new);
        ctx.json(toResponse(switched, true));
    }

    @OpenApi(
            path = "/api/v1/admin/news/{id}/comments",
            methods = HttpMethod.GET,
            summary = "Read every station's comments under an entry the instance published",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SystemCommentResponse[].class)))
    private void listComments(Context ctx) {
        int id = requireSystemEntry(pathInt(ctx, "id")).id();
        ctx.json(newsService.findComments(id).stream()
                .map(this::toCommentResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/admin/media/files",
            methods = HttpMethod.GET,
            summary = "List the files the instance holds",
            tags = {"Admin"},
            responses = @OpenApiResponse(status = "200"))
    private void listInstanceFiles(Context ctx) {
        ctx.json(media.listLibrary(null));
    }

    @OpenApi(
            path = "/api/v1/admin/media/files",
            methods = HttpMethod.POST,
            summary = "Take a file into the library the instance holds",
            tags = {"Admin"},
            responses = @OpenApiResponse(status = "201"))
    private void uploadInstanceFile(Context ctx) {
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > apiConfig.maxUploadSizeBytes()) throw new BadRequestResponse("File too large");
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            // No station and no member: the file belongs to the instance, and an administrator is
            // not a member of anything to record as its uploader.
            ctx.status(HttpStatus.CREATED)
                    .json(media.upload(null, null, null, file.filename(), file.contentType(), data));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to upload an instance media file", e);
            throw new BadRequestResponse("Failed to upload file");
        }
    }

    @OpenApi(
            path = "/api/v1/admin/media/files/{fileId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a file the instance holds",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "fileId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteInstanceFile(Context ctx) {
        int fileId = pathInt(ctx, "fileId");
        var file = media.findFile(fileId).orElseThrow(NotFoundResponse::new);
        // A station's file is that station's business, however much of the instance one holds.
        if (file.stationId() != null) {
            throw new NotFoundResponse();
        }
        if (!media.deleteFile(fileId)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The entry behind an id, if the instance published it, and a 404 otherwise.
     *
     * <p>These routes answer for what the instance said and for nothing else. A station's own entry
     * is its own business: an instance administrator has every right over the instance and none
     * over what one station wrote to its members.
     */
    private News requireSystemEntry(int id) {
        var news = newsService.findById(id).orElseThrow(NotFoundResponse::new);
        if (!news.systemEntry()) {
            throw new NotFoundResponse();
        }
        return news;
    }

    private SystemNewsResponse toResponse(News news, boolean withBlocks) {
        var restrictions = newsService.findRestrictions(news.id());
        List<ContentRow> rows =
                withBlocks && news.contentMode() == ContentMode.RICH ? newsService.loadBlocks(news) : List.of();
        return new SystemNewsResponse(
                news.id(),
                news.title(),
                news.contentMarkdown(),
                news.contentHtml(),
                news.publishedAt(),
                news.createdAt(),
                restrictions.userTypes(),
                newsService.countComments(news.id()),
                news.contentMode(),
                rows);
    }

    /**
     * A comment as the instance reads it. The author's identity carries the station they wrote
     * from, which is the whole point of reading every station's comments at once: knowing who is
     * asking is what makes the answer possible.
     */
    private SystemCommentResponse toCommentResponse(NewsComment comment) {
        var resolved = comment.author() != null ? memberNameResolver.resolveDisplay(comment.author()) : null;
        return new SystemCommentResponse(
                comment.id(),
                comment.newsId(),
                comment.parentId(),
                resolved != null ? resolved.identity() : null,
                resolved != null && resolved.name() != null ? resolved.name() : "",
                comment.content(),
                comment.deleted(),
                comment.createdAt());
    }

    /**
     * What an instance administrator sends when publishing or correcting an entry.
     *
     * @param userTypes  the user types that may read it, or empty for everyone. Groups, tags and
     *                   single members are things one station has, and this entry is read in all of
     *                   them, so they are not offered
     * @param publish    whether it goes out at once, defaulting to true
     * @param notifyMembers whether members are notified of it, defaulting to false. Most of what an
     *                   instance says is met when someone next looks, and waking every member of
     *                   every station for it teaches them to ignore the ones that matter
     */
    public record SystemNewsRequest(
            String title,
            String contentMarkdown,
            String contentHtml,
            List<StationUserType> userTypes,
            Boolean publish,
            Boolean notifyMembers,
            ContentMode contentMode) {}

    public record SystemNewsResponse(
            int id,
            String title,
            String contentMarkdown,
            String contentHtml,
            Instant publishedAt,
            Instant createdAt,
            List<StationUserType> userTypes,
            int commentCount,
            ContentMode contentMode,
            List<ContentRow> rows) {}

    public record SystemCommentResponse(
            int id,
            int newsId,
            Integer parentId,
            MemberIdentity author,
            String authorName,
            String content,
            boolean deleted,
            Instant createdAt) {}
}
