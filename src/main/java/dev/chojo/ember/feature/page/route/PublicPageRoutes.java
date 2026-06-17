/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository.PublicPartnerSummary;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
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
        routes.get(prefix + "/public/pages/{stationUid}/files/{hash}", this::serveFile);
        routes.get(prefix + "/public/pages/{stationUid}/page/<pagePath>", this::getPage);
        routes.get(prefix + "/public/pages/{stationUid}/partners", this::listPartners);
    }

    /**
     * Returns partner station metadata. Without query params: all active federation partners.
     * When {@code uids} is supplied (comma-separated UUIDs), returns resolved metadata for each
     * requested UID — first from the host's federation partners, then falling back to the public
     * discovery cache so cells can reference any publicly discoverable station.
     */
    private void listPartners(Context ctx) {
        int stationId = resolveStation(ctx);
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

    private void serveFile(Context ctx) {
        int stationId = resolveStation(ctx);
        String hash = ctx.pathParam("hash");
        var fileData = pageService.readFile(stationId, hash).orElseThrow(NotFoundResponse::new);
        ctx.contentType(fileData.contentType());
        ctx.header("Cache-Control", "public, max-age=86400, immutable");
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
