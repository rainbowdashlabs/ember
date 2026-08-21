/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterContentService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.service.NewsFederationService;
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

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * What a cluster tells its stations: its news and its calendar.
 *
 * <p>Thin on purpose. The content is ordinary station content on the cluster's own station, so every one of
 * these calls the service a station screen already calls, with the cluster's station id. It reaches the
 * member stations over the federation pairs the cluster made, which carry every capability, so there is no
 * sharing step here: cluster content is shared with the whole cluster by definition.
 */
@Singleton
public class ClusterContentRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterContentService contentService;
    private final NewsService newsService;
    private final NewsFederationService newsFederationService;
    private final EventCrudService eventService;
    private final EventFederationService eventFederationService;

    @Inject
    public ClusterContentRoutes(
            ClusterService clusterService,
            ClusterContentService contentService,
            NewsService newsService,
            NewsFederationService newsFederationService,
            EventCrudService eventService,
            EventFederationService eventFederationService) {
        this.clusterService = clusterService;
        this.contentService = contentService;
        this.newsService = newsService;
        this.newsFederationService = newsFederationService;
        this.eventService = eventService;
        this.eventFederationService = eventFederationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/news", this::listNews, ClusterPermission.USER);
        routes.post(prefix + "/cluster/news", this::createNews, ClusterPermission.CLUSTER_NEWS_EDIT);
        routes.delete(prefix + "/cluster/news/{newsId}", this::deleteNews, ClusterPermission.CLUSTER_NEWS_EDIT);
        routes.get(prefix + "/cluster/events", this::listEvents, ClusterPermission.USER);
        routes.post(prefix + "/cluster/events", this::createEvent, ClusterPermission.CLUSTER_EVENT_EDIT);
        routes.delete(prefix + "/cluster/events/{eventId}", this::deleteEvent, ClusterPermission.CLUSTER_EVENT_EDIT);
        routes.get(prefix + "/cluster/knowledge/folders", this::listFolders, ClusterPermission.USER);
        routes.post(
                prefix + "/cluster/knowledge/folders", this::createFolder, ClusterPermission.CLUSTER_KNOWLEDGE_EDIT);
        routes.get(prefix + "/cluster/knowledge/files", this::listFiles, ClusterPermission.USER);
        routes.post(prefix + "/cluster/knowledge/files", this::createArticle, ClusterPermission.CLUSTER_KNOWLEDGE_EDIT);
        routes.delete(
                prefix + "/cluster/knowledge/files/{fileId}",
                this::deleteArticle,
                ClusterPermission.CLUSTER_KNOWLEDGE_EDIT);
    }

    @OpenApi(
            path = "/api/v1/cluster/knowledge/folders",
            methods = HttpMethod.GET,
            summary = "The cluster's knowledge folders",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterKbFolder[].class)))
    private void listFolders(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(contentService.findFolders(cluster.id()).stream()
                .map(folder -> new ClusterKbFolder(folder.id(), folder.name(), folder.description()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/knowledge/folders",
            methods = HttpMethod.POST,
            summary = "Add a knowledge folder, shared with the whole cluster",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterKbFolderRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterKbFolder.class)))
    private void createFolder(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ClusterKbFolderRequest.class);
        var folder = contentService.createFolder(
                cluster.id(), request.parentId(), request.name(), request.description(), session.accountId());
        ctx.status(HttpStatus.CREATED).json(new ClusterKbFolder(folder.id(), folder.name(), folder.description()));
    }

    @OpenApi(
            path = "/api/v1/cluster/knowledge/files",
            methods = HttpMethod.GET,
            summary = "The cluster's knowledge articles",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterKbFile[].class)))
    private void listFiles(Context ctx) {
        Cluster cluster = requireActive(ctx);
        Integer folderId = ctx.queryParam("folderId") == null
                ? null
                : Integer.valueOf(
                        ctx.queryParamAsClass("folderId", Integer.class).get());
        ctx.json(contentService.findFiles(cluster.id(), folderId).stream()
                .map(file -> new ClusterKbFile(file.id(), file.name(), file.description(), file.folderId()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/knowledge/files",
            methods = HttpMethod.POST,
            summary = "Write a knowledge article, shared with the whole cluster",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterKbFileRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterKbFile.class)))
    private void createArticle(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ClusterKbFileRequest.class);
        var file = contentService.createArticle(
                cluster.id(),
                request.folderId(),
                request.name(),
                request.description(),
                request.content(),
                session.accountId());
        ctx.status(HttpStatus.CREATED)
                .json(new ClusterKbFile(file.id(), file.name(), file.description(), file.folderId()));
    }

    @OpenApi(
            path = "/api/v1/cluster/knowledge/files/{fileId}",
            pathParams = @OpenApiParam(name = "fileId", type = Integer.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Remove a knowledge article",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void deleteArticle(Context ctx) {
        Cluster cluster = requireActive(ctx);
        contentService.deleteArticle(cluster.id(), pathInt(ctx, "fileId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/news",
            methods = HttpMethod.GET,
            summary = "The cluster's news",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterNewsResponse[].class)))
    private void listNews(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(newsService.findByStation(contentService.homeStationOf(cluster.id()), 0, 100).stream()
                .map(ClusterContentRoutes::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/news",
            methods = HttpMethod.POST,
            summary = "Write a news article for every station of the cluster",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterNewsRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterNewsResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createNews(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ClusterNewsRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("An article needs a title");
        }

        News news = newsService.create(
                contentService.homeStationOf(cluster.id()),
                request.title().trim(),
                request.contentMarkdown() != null ? request.contentMarkdown() : "",
                request.contentHtml() != null ? request.contentHtml() : "",
                contentService.authorIdentity(cluster.id(), session.accountId()),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        // Written once and read by the stations over the connection they already have. Without the share
        // the entry sits on the cluster's own station and reaches nobody, which is not what writing for a
        // cluster means.
        newsFederationService.setShare(news.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());
        ctx.status(HttpStatus.CREATED).json(toResponse(news));
    }

    @OpenApi(
            path = "/api/v1/cluster/news/{newsId}",
            pathParams = @OpenApiParam(name = "newsId", type = Integer.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Remove a news article",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void deleteNews(Context ctx) {
        Cluster cluster = requireActive(ctx);
        int newsId = pathInt(ctx, "newsId");
        requireOwn(newsService.findById(newsId).map(News::stationId).orElse(null), cluster);
        newsService.delete(newsId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/events",
            methods = HttpMethod.GET,
            summary = "The cluster's calendar",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterEventResponse[].class)))
    private void listEvents(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(eventService.findByStation(contentService.homeStationOf(cluster.id())).stream()
                .map(ClusterContentRoutes::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/events",
            methods = HttpMethod.POST,
            summary = "Put an appointment in every station's calendar",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterEventRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterEventResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createEvent(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterEventRequest.class);
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestResponse("An appointment needs a name");
        }

        StationEvent event = eventService.create(
                contentService.homeStationOf(cluster.id()),
                request.name().trim(),
                request.description() != null ? request.description() : "",
                StationEvent.EventType.ONE_TIME,
                null,
                request.startTime(),
                request.endTime(),
                null,
                request.requiresRegistration(),
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        // Shared for the same reason the news is: an appointment nobody under the cluster can see is an
        // appointment the cluster did not make
        eventFederationService.setShare(event.id(), ShareScope.ALL_PARTNERS, List.of());
        ctx.status(HttpStatus.CREATED).json(toResponse(event));
    }

    @OpenApi(
            path = "/api/v1/cluster/events/{eventId}",
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Remove an appointment",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void deleteEvent(Context ctx) {
        Cluster cluster = requireActive(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireOwn(eventService.findById(eventId).map(StationEvent::stationId).orElse(null), cluster);
        eventService.delete(eventId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Refuses to touch content that lives somewhere other than this cluster's own station, so a cluster
     * cannot reach into a station's news list through its own routes.
     */
    private void requireOwn(Integer stationId, Cluster cluster) {
        if (stationId == null || stationId != cluster.homeStationId()) {
            throw new NotFoundResponse("No such entry");
        }
    }

    private static ClusterNewsResponse toResponse(News news) {
        return new ClusterNewsResponse(news.id(), news.title(), news.contentMarkdown(), news.createdAt());
    }

    private static ClusterEventResponse toResponse(StationEvent event) {
        return new ClusterEventResponse(
                event.id(), event.name(), event.description(), event.startTime(), event.endTime());
    }

    public record ClusterNewsRequest(String title, String contentMarkdown, String contentHtml) {}

    public record ClusterNewsResponse(int id, String title, String contentMarkdown, Instant createdAt) {}

    public record ClusterEventRequest(
            String name, String description, Instant startTime, Instant endTime, boolean requiresRegistration) {}

    public record ClusterEventResponse(int id, String name, String description, Instant startTime, Instant endTime) {}

    public record ClusterKbFolderRequest(Integer parentId, String name, String description) {}

    public record ClusterKbFolder(int id, String name, String description) {}

    public record ClusterKbFileRequest(Integer folderId, String name, String description, String content) {}

    public record ClusterKbFile(int id, String name, String description, Integer folderId) {}
}
