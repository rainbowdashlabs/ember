/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
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

@Singleton
public class PageRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(PageRoutes.class);

    private final PageService pageService;

    @Inject
    public PageRoutes(PageService pageService) {
        this.pageService = pageService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/pages", this::list, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages", this::create, StationPermission.PAGE_EDIT);
        routes.put(prefix + "/pages/landing", this::setLandingPage, StationPermission.PAGE_MANAGER);
        routes.get(prefix + "/pages/{pid}", this::get, StationPermission.PAGE_EDIT);
        routes.put(prefix + "/pages/{pid}", this::save, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/{pid}/duplicate", this::duplicate, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/{pid}", this::delete, StationPermission.PAGE_MANAGER);
        routes.put(prefix + "/pages/{pid}/publish", this::togglePublish, StationPermission.PAGE_MANAGER);
        routes.post(prefix + "/pages/{pid}/images", this::uploadImage, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/{pid}/images/{imageId}", this::deleteImage, StationPermission.PAGE_EDIT);
    }

    private void list(Context ctx) {
        var session = UserSession.from(ctx);
        var pages = pageService.listPages(session.stationId());
        var landingPageId = pageService.getLandingPageId(session.stationId()).orElse(null);
        ctx.json(new PagesListResponse(pages, landingPageId));
    }

    private void create(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreatePageRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("title is required");
        }
        try {
            var page = pageService.create(
                    session.stationId(),
                    request.title(),
                    request.parentId(),
                    session.member().id());
            ctx.status(HttpStatus.CREATED).json(page);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void get(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        var page = pageService.getPage(pid).orElseThrow(NotFoundResponse::new);
        ctx.json(page);
    }

    private void save(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        var request = ctx.bodyAsClass(SavePageRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("title is required");
        }
        if (request.slug() == null || request.slug().isBlank()) {
            throw new BadRequestResponse("slug is required");
        }

        List<PageService.RowData> rows = request.rows() == null
                ? List.of()
                : request.rows().stream()
                        .map(r -> new PageService.RowData(
                                r.sortOrder(),
                                r.cells() == null
                                        ? List.of()
                                        : r.cells().stream()
                                                .map(c -> new PageService.CellData(
                                                        c.sortOrder(),
                                                        c.widthPercent() != null ? c.widthPercent() : 100.0,
                                                        CellContentType.valueOf(c.contentType()),
                                                        c.content() != null ? c.content() : "",
                                                        c.parsedConfig()))
                                                .toList()))
                        .toList();

        try {
            if (!pageService.savePage(
                    pid,
                    request.title(),
                    request.slug(),
                    request.parentId(),
                    request.metaDescription(),
                    request.ogImageId(),
                    rows)) {
                throw new NotFoundResponse();
            }
            ctx.json(pageService.getPage(pid).orElseThrow());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void duplicate(Context ctx) {
        var session = UserSession.from(ctx);
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        try {
            var copy = pageService.duplicatePage(pid, session.member().id());
            ctx.status(HttpStatus.CREATED).json(copy);
        } catch (Exception e) {
            throw new NotFoundResponse();
        }
    }

    private void delete(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        if (!pageService.deletePage(pid)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void togglePublish(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        var request = ctx.bodyAsClass(PublishRequest.class);
        if (!pageService.setPublished(pid, request.published())) {
            throw new NotFoundResponse();
        }
        ctx.json(pageService.getPage(pid).orElseThrow());
    }

    private void setLandingPage(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(LandingPageRequest.class);
        try {
            pageService.setLandingPage(session.stationId(), request.pageId());
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void uploadImage(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");

        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var image = pageService.uploadImage(pid, file.filename(), file.contentType(), data);
            ctx.status(HttpStatus.CREATED).json(image);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to upload page image", e);
            throw new BadRequestResponse("Failed to upload image");
        }
    }

    private void deleteImage(Context ctx) {
        int imageId = ctx.pathParamAsClass("imageId", Integer.class).get();
        if (!pageService.deleteImage(imageId)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // Response records
    record PagesListResponse(List<StationPage> pages, Integer landingPageId) {}

    // Request records
    record CreatePageRequest(String title, Integer parentId) {}

    record SavePageRequest(
            String title,
            String slug,
            Integer parentId,
            String metaDescription,
            Integer ogImageId,
            List<RowRequest> rows) {}

    record RowRequest(int sortOrder, List<CellRequest> cells) {}

    record CellRequest(int sortOrder, Double widthPercent, String contentType, String content, Object config) {
        CellConfig parsedConfig() {
            if (config == null) return CellContentType.valueOf(contentType).emptyConfig();
            try {
                String json = CellConfig.MAPPER.writeValueAsString(config);
                return CellConfig.parse(CellContentType.valueOf(contentType), json);
            } catch (Exception e) {
                return CellContentType.valueOf(contentType).emptyConfig();
            }
        }
    }

    record PublishRequest(boolean published) {}

    record LandingPageRequest(Integer pageId) {}
}
