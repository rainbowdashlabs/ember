/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unauthenticated, read-only routes for the public knowledgebase.
 * Only serves content from the station itself (no federated content).
 */
@Singleton
public class PublicKnowledgeBaseRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(PublicKnowledgeBaseRoutes.class);

    private final KnowledgeBaseService kbService;
    private final StationService stationService;
    private final StationRepository stationRepository;
    private final ImageService imageService;

    @Inject
    public PublicKnowledgeBaseRoutes(
            KnowledgeBaseService kbService,
            StationService stationService,
            StationRepository stationRepository,
            ImageService imageService) {
        this.kbService = kbService;
        this.stationService = stationService;
        this.stationRepository = stationRepository;
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

    private void getInfo(Context ctx) {
        var station = resolveStation(ctx);
        ctx.json(Map.of(
                "stationName", station.name(),
                "stationUid", station.uid().toString()));
    }

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

    private void getFile(Context ctx) {
        var station = resolveStation(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = kbService.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != station.id()) throw new NotFoundResponse();
        requirePubliclyVisible(station, null, id);
        ctx.json(file);
    }

    private void getFileContent(Context ctx) {
        var station = resolveStation(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = kbService.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != station.id()) throw new NotFoundResponse();
        requirePubliclyVisible(station, null, id);

        switch (file.fileType()) {
            case MARKDOWN, TEXT -> {
                var content = kbService.getMarkdownContent(id).orElse("");
                ctx.contentType("text/plain").result(content);
            }
            case YOUTUBE -> ctx.json(Map.of("youtubeUrl", file.youtubeUrl() != null ? file.youtubeUrl() : ""));
            case LINK -> ctx.json(Map.of("linkUrl", file.linkUrl() != null ? file.linkUrl() : ""));
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

    private void getMarkdownHtml(Context ctx) {
        var station = resolveStation(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = kbService.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != station.id()) throw new NotFoundResponse();
        requirePubliclyVisible(station, null, id);
        if (file.fileType() != KbFileType.MARKDOWN) throw new BadRequestResponse("Not a markdown file");

        var markdown = kbService.getMarkdownContent(id).orElse("");
        var html = kbService.renderMarkdown(markdown);
        ctx.json(Map.of("html", html, "markdown", markdown));
    }

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
                .map(r -> Map.of("file", r.file(), "snippet", r.snippet()))
                .toList());
    }

    private void getFolderIcon(Context ctx) {
        resolveStation(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(128);
        imageService
                .read(ImageCategory.KB_ICONS, String.valueOf(id), size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    private void getKbImage(Context ctx) {
        resolveStation(ctx);
        String imageId = ctx.pathParam("imageId");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(1024);
        imageService
                .read(ImageCategory.KB_IMAGES, imageId, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    private void listTags(Context ctx) {
        var station = resolveStation(ctx);
        ctx.json(kbService.findTagsByStation(station.id()));
    }

    public record PublicBrowseResponse(KbFolder currentFolder, List<KbFolder> folders, List<KbFile> files) {}
}
