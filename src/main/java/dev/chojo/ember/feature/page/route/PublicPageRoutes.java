/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.insights.service.PageHitRecorder;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.page.service.PublicSiteService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class PublicPageRoutes implements Routes {
    private final PageService pageService;
    private final PublicSiteService site;

    @Inject
    public PublicPageRoutes(PageService pageService, PublicSiteService site) {
        this.pageService = pageService;
        this.site = site;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/pages/{stationUid}", this::listPages);
        routes.get(prefix + "/public/pages/{stationUid}/landing", this::getLandingPage);
        routes.get(prefix + "/public/pages/{stationUid}/page/<pagePath>", this::getPage);
        routes.get(prefix + "/public/pages/{stationUid}/partners", this::listPartners);
    }

    /**
     * Returns partner station metadata. Without query params: all active federation partners.
     * When {@code uids} is supplied (comma-separated UUIDs), returns resolved metadata for each
     * requested UID - first from the host's federation partners, then falling back to the public
     * discovery cache so cells can reference any publicly discoverable station.
     *
     * <p>Behind the same switch as the pages it serves: it exists for the cells on them, so a
     * station that has closed its pages has closed this too.
     */
    private void listPartners(Context ctx) {
        ctx.json(site.partners(resolveOpenStation(ctx), ctx.queryParam("uids")));
    }

    private void listPages(Context ctx) {
        int stationId = resolveOpenStation(ctx);
        var pages = pageService.listListedPages(stationId);
        ctx.json(pages.stream()
                .map(p -> PublicPageSummary.from(p, pageService.getPagePath(p)))
                .toList());
    }

    private void getPage(Context ctx) {
        int stationId = resolveOpenStation(ctx);
        String pagePath = ctx.pathParam("pagePath");

        var page = pageService.getPageByPath(stationId, pagePath).orElseThrow(Refusal.PUBLIC_PAGE_NOT_HERE::raise);

        var rendered = pageService.getPageRendered(page.id()).orElseThrow(Refusal.PUBLIC_PAGE_NOT_RENDERED::raise);
        ctx.attribute(PageHitRecorder.ATTR_PAGE_HIT_PAGE_ID, page.id());
        ctx.json(rendered);
    }

    private void getLandingPage(Context ctx) {
        int stationId = resolveOpenStation(ctx);
        var page = pageService.getLandingPage(stationId).orElseThrow(Refusal.PUBLIC_LANDING_PAGE_NOT_HERE::raise);
        ctx.attribute(PageHitRecorder.ATTR_PAGE_HIT_PAGE_ID, page.id());
        ctx.json(page);
    }

    /**
     * The station whose public site this is, where it has one. A page reached by its own link is
     * not part of that site and is served by {@link SharedPageRoutes}.
     */
    private int resolveOpenStation(Context ctx) {
        return site.openStation(ctx.pathParam("stationUid"));
    }

    record PublicPageSummary(
            int id,
            String publicUid,
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
                    page.publicUid() != null ? page.publicUid().toString() : null,
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
