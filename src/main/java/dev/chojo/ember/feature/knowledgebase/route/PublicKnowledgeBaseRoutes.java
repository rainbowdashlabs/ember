/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Unauthenticated, read-only routes for the public knowledgebase.
 * Only serves content from the station itself (no federated content).
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class PublicKnowledgeBaseRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(PublicKnowledgeBaseRoutes.class);

    private final KnowledgeBaseService kbService;
    private final StationService stationService;
    private final StationRepository stationRepository;
    private final KbIconService iconService;
    private final KbImageService imageService;

    @Inject
    public PublicKnowledgeBaseRoutes(
            KnowledgeBaseService kbService,
            StationService stationService,
            StationRepository stationRepository,
            KbIconService iconService,
            KbImageService imageService) {
        this.kbService = kbService;
        this.stationService = stationService;
        this.stationRepository = stationRepository;
        this.iconService = iconService;
        this.imageService = imageService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/public/kb/{stationUid}";

        routes.get(base + "/info", this::getInfo);
        routes.get(base + "/browse", this::browse);
        routes.get(base + "/files/{id}", this::getFile);
        routes.get(base + "/files/{id}/content", this::getFileContent);
        routes.get(base + "/files/{id}/html", this::getMarkdownHtml);
        routes.get(base + "/search", this::search);
        routes.get(base + "/folders/{id}/icon", this::getFolderIcon);
        routes.get(base + "/images/{imageId}", this::getKbImage);
        routes.get(base + "/tags", this::listTags);
    }

    private Station resolveStation(Context ctx) {
        String uidParam = ctx.pathParam("stationUid");
        UUID uid;
        try {
            uid = UUID.fromString(uidParam);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid station UUID for public KB: {}", uidParam, e);
            throw new BadRequestResponse("Invalid station ID");
        }
        var station = stationRepository.findByUid(uid).orElseThrow(NotFoundResponse::new);
        if (station.publicKbMode() == PublicKbMode.OFF) {
            throw new NotFoundResponse();
        }
        if (stationService.findDisabledModules(station.id()).contains(StationModule.KNOWLEDGE_BASE)) {
            throw new NotFoundResponse();
        }
        return station;
    }

    private void requirePubliclyVisible(Station station, Integer folderId, Integer fileId) {
        if (!kbService.isPubliclyVisible(station.publicKbMode(), folderId, fileId)) {
            throw new NotFoundResponse();
        }
    }

    /**
     * Resolves the public station, loads the {@code id} file, and confirms it belongs to that
     * station and is publicly visible, returning it. Answers 404 for a missing, cross-station, or
     * non-public file.
     */
    private KbFile resolvePublicFile(Context ctx) {
        var station = resolveStation(ctx);
        int id = pathInt(ctx, "id");
        var file = kbService.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != station.id()) throw new NotFoundResponse();
        requirePubliclyVisible(station, null, id);
        return file;
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/info",
            methods = HttpMethod.GET,
            summary = "Get public knowledge base info for a station",
            tags = {"Public Knowledge Base"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getInfo(Context ctx) {
        var station = resolveStation(ctx);
        ctx.json(new PublicKbInfo(station.name(), station.uid().toString()));
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/browse",
            methods = HttpMethod.GET,
            summary = "Browse public knowledge base folders and files",
            tags = {"Public Knowledge Base"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            queryParams = @OpenApiParam(name = "folderId", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicBrowseResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void browse(Context ctx) {
        var station = resolveStation(ctx);
        Integer folderId = ctx.queryParam("folderId") != null
                ? ctx.queryParamAsClass("folderId", Integer.class).get()
                : null;

        // If browsing a subfolder, verify it's publicly visible
        if (folderId != null) {
            requirePubliclyVisible(station, folderId, null);
        }

        var folders = kbService.findFolders(station.id(), folderId).stream()
                .filter(f -> kbService.isPubliclyVisible(station.publicKbMode(), f.id(), null))
                .toList();
        var files = kbService.findFiles(station.id(), folderId).stream()
                .filter(f -> kbService.isPubliclyVisible(station.publicKbMode(), null, f.id()))
                .toList();
        KbFolder currentFolder =
                folderId != null ? kbService.findFolder(folderId).orElse(null) : null;

        ctx.json(new PublicBrowseResponse(currentFolder, folders, files));
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/files/{id}",
            methods = HttpMethod.GET,
            summary = "Get a public knowledge base file",
            tags = {"Public Knowledge Base"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getFile(Context ctx) {
        ctx.json(resolvePublicFile(ctx));
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/files/{id}/content",
            methods = HttpMethod.GET,
            summary = "Get public file content",
            tags = {"Public Knowledge Base"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getFileContent(Context ctx) {
        var file = resolvePublicFile(ctx);
        int id = file.id();

        switch (file.fileType()) {
            case MARKDOWN, TEXT -> {
                var content = kbService.getMarkdownContent(id).orElse("");
                ctx.contentType("text/plain").result(content);
            }
            case YOUTUBE -> ctx.json(new YoutubeContentResponse(file.youtubeUrl() != null ? file.youtubeUrl() : ""));
            case LINK -> ctx.json(new LinkContentResponse(file.linkUrl() != null ? file.linkUrl() : ""));
            case PDF, IMAGE, OTHER -> {
                var contentType = kbService.getFileContentType(id);
                var content = kbService.getFileContent(id);
                if (content.isPresent() && contentType.isPresent()) {
                    ctx.contentType(contentType.get());
                    ctx.header("Cache-Control", "public, max-age=300");
                    ctx.result(content.get());
                } else {
                    throw new NotFoundResponse();
                }
            }
        }
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/files/{id}/html",
            methods = HttpMethod.GET,
            summary = "Get rendered HTML for a public markdown file",
            tags = {"Public Knowledge Base"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getMarkdownHtml(Context ctx) {
        var file = resolvePublicFile(ctx);
        if (file.fileType() != KbFileType.MARKDOWN) throw new BadRequestResponse("Not a markdown file");

        var markdown = kbService.getMarkdownContent(file.id()).orElse("");
        var html = kbService.renderMarkdown(markdown);
        ctx.json(new MarkdownHtmlResponse(html, markdown));
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/search",
            methods = HttpMethod.GET,
            summary = "Search the public knowledge base",
            tags = {"Public Knowledge Base"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            queryParams = @OpenApiParam(name = "q", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void search(Context ctx) {
        var station = resolveStation(ctx);
        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            ctx.json(List.of());
            return;
        }
        var results = kbService.searchWithSnippets(station.id(), query);
        ctx.json(results.stream()
                .filter(r -> kbService.isPubliclyVisible(
                        station.publicKbMode(), null, r.file().id()))
                .map(r -> new SearchResultItem(r.file(), r.snippet()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/folders/{id}/icon",
            methods = HttpMethod.GET,
            summary = "Get a public folder icon",
            tags = {"Public Knowledge Base"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "404")})
    private void getFolderIcon(Context ctx) {
        var station = resolveStation(ctx);
        int id = pathInt(ctx, "id");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(128);
        iconService
                .read(station.id(), id, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/images/{imageId}",
            methods = HttpMethod.GET,
            summary = "Get a public knowledge base image",
            tags = {"Public Knowledge Base"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "imageId", type = String.class, required = true)
            },
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "404")})
    private void getKbImage(Context ctx) {
        var station = resolveStation(ctx);
        String imageId = ctx.pathParam("imageId");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(1024);
        imageService
                .read(station.id(), imageId, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/tags",
            methods = HttpMethod.GET,
            summary = "List tags in the public knowledge base",
            tags = {"Public Knowledge Base"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void listTags(Context ctx) {
        var station = resolveStation(ctx);
        ctx.json(kbService.findTagsByStation(station.id()));
    }

    public record PublicBrowseResponse(KbFolder currentFolder, List<KbFolder> folders, List<KbFile> files) {}

    public record PublicKbInfo(String stationName, String stationUid) {}

    public record YoutubeContentResponse(String youtubeUrl) {}

    public record LinkContentResponse(String linkUrl) {}

    public record MarkdownHtmlResponse(String html, String markdown) {}

    public record SearchResultItem(KbFile file, String snippet) {}
}
