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
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KbTagService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
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

import java.io.IOException;
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
    private final KbContentService contentService;
    private final KbSearchService searchService;
    private final KbAccessService accessService;
    private final KbTagService tagService;
    private final StationService stationService;
    private final StationRepository stationRepository;
    private final KbIconService iconService;
    private final KbImageService imageService;
    private final KbFilePictureService pictureService;
    private final KbPdfExportService pdfExportService;

    @Inject
    public PublicKnowledgeBaseRoutes(
            KnowledgeBaseService kbService,
            KbContentService contentService,
            KbSearchService searchService,
            KbAccessService accessService,
            KbTagService tagService,
            StationService stationService,
            StationRepository stationRepository,
            KbIconService iconService,
            KbImageService imageService,
            KbFilePictureService pictureService,
            KbPdfExportService pdfExportService) {
        this.kbService = kbService;
        this.contentService = contentService;
        this.searchService = searchService;
        this.accessService = accessService;
        this.tagService = tagService;
        this.stationService = stationService;
        this.stationRepository = stationRepository;
        this.iconService = iconService;
        this.imageService = imageService;
        this.pictureService = pictureService;
        this.pdfExportService = pdfExportService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/public/kb/{stationUid}";

        routes.get(base + "/info", this::getInfo);
        routes.get(base + "/browse", this::browse);
        routes.get(base + "/files/{id}", this::getFile);
        routes.get(base + "/files/{id}/content", this::getFileContent);
        routes.get(base + "/files/{id}/picture", this::getFilePicture);
        routes.get(base + "/files/{id}/html", this::getMarkdownHtml);
        routes.get(base + "/files/{id}/pdf", this::getFilePdf);
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
        if (!accessService.isPubliclyVisible(station.publicKbMode(), folderId, fileId)) {
            throw new NotFoundResponse();
        }
    }

    /**
     * A file of a public knowledge base together with the station publishing it, which is what a
     * route serving that file needs to know.
     */
    private record PublicFile(Station station, KbFile file) {}

    /**
     * Resolves the public station, loads the {@code id} file, and confirms it belongs to that
     * station and is publicly visible, returning both. Answers 404 for a missing, cross-station, or
     * non-public file.
     *
     * <p>Every route serving a single file goes through here, so that what may leave the station is
     * decided in one place: a second copy of the rule is a second thing to keep right, and the copy
     * left behind is the one that serves a file nobody published.
     */
    private PublicFile resolvePublicFile(Context ctx) {
        var station = resolveStation(ctx);
        int id = pathInt(ctx, "id");
        var file = kbService.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != station.id()) throw new NotFoundResponse();
        requirePubliclyVisible(station, null, id);
        return new PublicFile(station, file);
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
        ctx.json(new PublicKbInfo(station.name(), station.uid().toString(), StationFormat.timezoneNameOf(station)));
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
                .filter(f -> accessService.isPubliclyVisible(station.publicKbMode(), f.id(), null))
                .toList();
        var files = kbService.findFiles(station.id(), folderId).stream()
                .filter(f -> accessService.isPubliclyVisible(station.publicKbMode(), null, f.id()))
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
        ctx.json(resolvePublicFile(ctx).file());
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
        var file = resolvePublicFile(ctx).file();
        int id = file.id();

        switch (file.fileType()) {
            case MARKDOWN, TEXT -> {
                var content = contentService.getMarkdownContent(id).orElse("");
                ctx.contentType("text/plain").result(content);
            }
            case YOUTUBE -> ctx.json(new YoutubeContentResponse(file.youtubeUrl() != null ? file.youtubeUrl() : ""));
            case LINK -> ctx.json(new LinkContentResponse(file.linkUrl() != null ? file.linkUrl() : ""));
            case PDF, IMAGE, OTHER -> {
                var contentType = contentService.getFileContentType(id);
                var content = contentService.getFileContent(id);
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

    /**
     * The picture of a public file, so a tile on the public knowledge base shows what the file is
     * rather than the icon of its kind, exactly as the station's own listing does.
     *
     * <p>Kept behind the same door as the file's own bytes, and opened by the same rule: a picture
     * of a sheet is still the sheet, so only a file the station has published has one here. A file
     * with no picture answers 404, and the tile draws its icon instead.
     */
    @OpenApi(
            path = "/api/v1/public/kb/{stationUid}/files/{id}/picture",
            methods = HttpMethod.GET,
            summary = "Get the picture of a public knowledge base file",
            tags = {"Public Knowledge Base"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getFilePicture(Context ctx) {
        var file = resolvePublicFile(ctx).file();
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(256);
        var picture = pictureService
                .read(file.stationId(), file.id(), file.mimeType(), size)
                .orElseThrow(NotFoundResponse::new);
        ctx.contentType(picture.contentType());
        ctx.header("Cache-Control", "public, max-age=300");
        ctx.result(picture.data());
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
        var file = resolvePublicFile(ctx).file();
        if (file.fileType() != KbFileType.MARKDOWN) throw new BadRequestResponse("Not a markdown file");

        var markdown = contentService.getMarkdownContent(file.id()).orElse("");
        var html = contentService.renderMarkdown(markdown);
        ctx.json(new MarkdownHtmlResponse(html, markdown));
    }

    private void getFilePdf(Context ctx) {
        var published = resolvePublicFile(ctx);
        var file = published.file();
        if (!KbPdfExportService.isExportable(file.fileType())) {
            throw new BadRequestResponse("Only markdown and text files can be rendered as PDF");
        }
        try {
            byte[] pdf = pdfExportService.renderPublic(file, published.station());
            ctx.contentType("application/pdf");
            ctx.header(
                    "Content-Disposition",
                    SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, file.name() + ".pdf"));
            ctx.result(pdf);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerErrorResponse("Failed to render PDF");
        } catch (IOException e) {
            log.warn("Failed to render public file {} as PDF", file.id(), e);
            throw new InternalServerErrorResponse("Failed to render PDF");
        }
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
        var results = searchService.searchWithSnippets(station.id(), query);
        ctx.json(results.stream()
                .filter(r -> accessService.isPubliclyVisible(
                        station.publicKbMode(), null, r.file().id()))
                .limit(KbSearchService.RESULT_LIMIT)
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
        ctx.json(tagService.findTagsByStation(station.id()));
    }

    public record PublicBrowseResponse(KbFolder currentFolder, List<KbFolder> folders, List<KbFile> files) {}

    /**
     * What a public wiki says about the station behind it.
     *
     * <p>The timezone is what a date in an article is written on. Neither the server that renders the
     * page nor the browser that renders it again stands where the reader does, so a date put on
     * whichever clock wrote it comes out differently in the two copies; the station's clock is the one
     * both can be told to use.
     */
    public record PublicKbInfo(String stationName, String stationUid, String stationTimezone) {}

    public record YoutubeContentResponse(String youtubeUrl) {}

    public record LinkContentResponse(String linkUrl) {}

    public record MarkdownHtmlResponse(String html, String markdown) {}

    public record SearchResultItem(KbFile file, String snippet) {}
}
