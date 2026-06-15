/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class PublicPageRoutes implements Routes {
    private final PageService pageService;
    private final StationRepository stationRepository;
    private final FederationRepository federationRepository;

    @Inject
    public PublicPageRoutes(
            PageService pageService, StationRepository stationRepository, FederationRepository federationRepository) {
        this.pageService = pageService;
        this.stationRepository = stationRepository;
        this.federationRepository = federationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/pages/{stationUid}", this::listPages);
        routes.get(prefix + "/public/pages/{stationUid}/landing", this::getLandingPage);
        routes.get(prefix + "/public/pages/{stationUid}/images/{imageId}", this::serveImage);
        routes.get(prefix + "/public/pages/{stationUid}/page/<pagePath>", this::getPage);
        routes.get(prefix + "/public/pages/{stationUid}/partners", this::listPartners);
    }

    private void listPartners(Context ctx) {
        int stationId = resolveStation(ctx);
        ctx.json(federationRepository.findActivePartnerSummaries(stationId));
    }

    private void listPages(Context ctx) {
        int stationId = resolveStation(ctx);
        var pages = pageService.listPublishedPages(stationId);
        ctx.json(pages.stream()
                .map(p -> PublicPageSummary.from(p, pageService.getPagePath(p)))
                .toList());
    }

    private void getPage(Context ctx) {
        int stationId = resolveStation(ctx);
        String pagePath = ctx.pathParam("pagePath");

        var page = pageService
                .getPageByPath(stationId, pagePath)
                .filter(StationPage::published)
                .orElseThrow(NotFoundResponse::new);

        ctx.json(pageService.getPageRendered(page.id()).orElseThrow(NotFoundResponse::new));
    }

    private void getLandingPage(Context ctx) {
        int stationId = resolveStation(ctx);
        var page = pageService.getLandingPage(stationId).orElseThrow(NotFoundResponse::new);
        ctx.json(page);
    }

    private void serveImage(Context ctx) {
        int imageId = ctx.pathParamAsClass("imageId", Integer.class).get();
        var fileData = pageService.readImage(imageId).orElseThrow(NotFoundResponse::new);
        ctx.contentType(fileData.contentType());
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.result(fileData.data());
    }

    private int resolveStation(Context ctx) {
        String param = ctx.pathParam("stationUid");
        try {
            UUID uid = UUID.fromString(param);
            return stationRepository.resolveId(uid).orElseThrow(NotFoundResponse::new);
        } catch (IllegalArgumentException e) {
            // Not a UUID — try as public slug
            return stationRepository.findBySlug(param).map(Station::id).orElseThrow(NotFoundResponse::new);
        }
    }

    record PublicPageSummary(
            int id,
            Integer parentId,
            String title,
            String slug,
            String path,
            int sortOrder,
            String metaDescription,
            Integer ogImageId) {
        static PublicPageSummary from(StationPage page, String path) {
            return new PublicPageSummary(
                    page.id(),
                    page.parentId(),
                    page.title(),
                    page.slug(),
                    path,
                    page.sortOrder(),
                    page.metaDescription(),
                    page.ogImageId());
        }
    }
}
