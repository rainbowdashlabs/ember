/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository.PublicPartnerSummary;
import dev.chojo.ember.feature.insights.service.PageHitRecorder;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Singleton
public class PublicPageRoutes implements Routes {
    private final PageService pageService;
    private final StationRepository stationRepository;
    private final FederationRepository federationRepository;
    private final DiscoveryStationCacheRepository discoveryCacheRepository;

    @Inject
    public PublicPageRoutes(
            PageService pageService,
            StationRepository stationRepository,
            FederationRepository federationRepository,
            DiscoveryStationCacheRepository discoveryCacheRepository) {
        this.pageService = pageService;
        this.stationRepository = stationRepository;
        this.federationRepository = federationRepository;
        this.discoveryCacheRepository = discoveryCacheRepository;
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
        int stationId = resolveOpenStation(ctx);
        String uidParam = ctx.queryParam("uids");
        if (uidParam == null || uidParam.isBlank()) {
            ctx.json(federationRepository.findActivePartnerSummaries(stationId));
            return;
        }

        List<String> requested = new ArrayList<>();
        var seen = new HashSet<String>();
        for (var part : uidParam.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && seen.add(trimmed)) requested.add(trimmed);
        }

        var byUid = new LinkedHashMap<String, PublicPartnerSummary>();
        for (var partner : federationRepository.findActivePartnerSummaries(stationId)) {
            byUid.put(partner.uid().toString(), partner);
        }

        var unresolved = new ArrayList<String>();
        for (var uid : requested) {
            if (!byUid.containsKey(uid)) unresolved.add(uid);
        }
        if (!unresolved.isEmpty()) {
            for (var cached : discoveryCacheRepository.findByStationUids(unresolved)) {
                var card = cached.card();
                byUid.put(
                        card.stationUid(),
                        new PublicPartnerSummary(UUID.fromString(card.stationUid()), card.name(), null, null));
            }
        }

        var ordered = new ArrayList<PublicPartnerSummary>(requested.size());
        for (var uid : requested) {
            var match = byUid.get(uid);
            if (match != null) ordered.add(match);
        }
        ctx.json(ordered);
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
     * The station whose public site this is, where it has one.
     *
     * <p>The switch that opens a station's pages to the world was consulted by the menu and by the
     * sitemap and by nothing here, so turning it off hid the way in and went on serving every page
     * to anybody who still had an address. It is asked here now.
     *
     * <p>A page reached by its own link is not part of that site and is served by
     * {@link SharedPageRoutes}, which asks nothing of the switch.
     */
    private int resolveOpenStation(Context ctx) {
        int stationId = resolveStation(ctx);
        if (!stationRepository
                .findById(stationId)
                .map(Station::publicPagesEnabled)
                .orElse(false)) {
            throw Refusal.PUBLIC_PAGES_SWITCHED_OFF.raise();
        }
        return stationId;
    }

    /**
     * The station an address names, whether it names it by identifier or by its public slug.
     */
    private int resolveStation(Context ctx) {
        return stationRepository
                .resolveAddressedId(ctx.pathParam("stationUid"))
                .orElseThrow(Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_PAGE::raise);
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
