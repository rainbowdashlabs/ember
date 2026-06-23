/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlWriteFeature;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Singleton
public class SitemapRoutes implements Routes {
    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final XmlMapper XML_MAPPER = XmlMapper.builder()
            .enable(XmlWriteFeature.WRITE_XML_DECLARATION)
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();
    private static final DateTimeFormatter W3C_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String INDEX_CACHE_KEY = "__index__";

    private final Cache<String, String> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).build();
    private final StationRepository stationRepository;
    private final PageService pageService;
    private final KnowledgeBaseService kbService;

    @Inject
    public SitemapRoutes(StationRepository stationRepository, PageService pageService, KnowledgeBaseService kbService) {
        this.stationRepository = stationRepository;
        this.pageService = pageService;
        this.kbService = kbService;
    }

    private static String formatDate(Instant instant) {
        if (instant == null) return null;
        return W3C_DATETIME.format(instant.atOffset(ZoneOffset.UTC));
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get("/sitemap.xml", this::sitemapIndex);
        routes.get("/sitemap-station-{stationUid}.xml", this::sitemapStation);
    }

    private void sitemapIndex(Context ctx) {
        var xml = cache.get(INDEX_CACHE_KEY, _ -> {
            var baseUrl = baseUrl(ctx);
            var sitemaps = new ArrayList<SitemapEntry>();
            sitemaps.add(new SitemapEntry(baseUrl + "/sitemap-static.xml", null));

            for (var station : getPublicStations()) {
                sitemaps.add(new SitemapEntry(baseUrl + "/sitemap-station-" + station.uid() + ".xml", null));
            }

            return XML_MAPPER.writeValueAsString(new SitemapIndex(sitemaps));
        });

        ctx.contentType("application/xml; charset=utf-8");
        ctx.header("Cache-Control", "public, max-age=21600");
        ctx.result(xml);
    }

    private void sitemapStation(Context ctx) {
        var stationUid = ctx.pathParam("stationUid");
        var station = stationRepository.findByUid(UUID.fromString(stationUid)).orElseThrow(NotFoundResponse::new);

        if (!hasPublicContent(station)) throw new NotFoundResponse();

        var xml = cache.get(stationUid, _ -> {
            var baseUrl = baseUrl(ctx);
            var slug = station.publicSlug() != null
                    ? station.publicSlug()
                    : station.uid().toString();
            var stationBase = baseUrl + "/public/station/" + slug;
            var urls = new ArrayList<UrlEntry>();

            Instant latestMod = null;

            if (station.publicCalendarEnabled()) {
                urls.add(new UrlEntry(stationBase + "/calendar", "0.7", "daily", null));
            }
            if (station.publicKbMode() != PublicKbMode.OFF) {
                var kbFiles = kbService.findAllPublicFiles(station.id(), station.publicKbMode());
                Instant latestKb = null;
                for (var file : kbFiles) {
                    urls.add(new UrlEntry(
                            stationBase + "/knowledge/file/" + file.id(),
                            "0.5",
                            "weekly",
                            formatDate(file.updatedAt())));
                    if (file.updatedAt() != null
                            && (latestKb == null || file.updatedAt().isAfter(latestKb))) {
                        latestKb = file.updatedAt();
                    }
                }
                urls.add(new UrlEntry(stationBase + "/knowledge", "0.6", "weekly", formatDate(latestKb)));
                if (latestKb != null) {
                    latestMod = latestKb;
                }
            }
            if (station.publicPagesEnabled()) {
                var pages = pageService.listPublishedPages(station.id());
                for (var page : pages) {
                    var path = pageService.getPagePath(page);
                    var priority = page.parentId() == null ? "0.7" : "0.6";
                    urls.add(new UrlEntry(
                            stationBase + "/page/" + path, priority, "weekly", formatDate(page.updatedAt())));
                    if (page.updatedAt() != null
                            && (latestMod == null || page.updatedAt().isAfter(latestMod))) {
                        latestMod = page.updatedAt();
                    }
                }
            }

            urls.addFirst(new UrlEntry(stationBase, "0.8", "weekly", formatDate(latestMod)));

            return XML_MAPPER.writeValueAsString(new Urlset(urls));
        });

        ctx.contentType("application/xml; charset=utf-8");
        ctx.header("Cache-Control", "public, max-age=21600");
        ctx.result(xml);
    }

    private List<Station> getPublicStations() {
        return stationRepository.findAll().stream()
                .filter(this::hasPublicContent)
                .toList();
    }

    private boolean hasPublicContent(Station station) {
        return station.publicCalendarEnabled()
                || station.publicKbMode() != PublicKbMode.OFF
                || station.publicPagesEnabled()
                || station.publicWaitlistEnabled()
                || station.publicBlogEnabled();
    }

    private String baseUrl(Context ctx) {
        return ctx.scheme() + "://" + ctx.host();
    }

    @JsonRootName(value = "sitemapindex", namespace = SITEMAP_NS)
    record SitemapIndex(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "sitemap", namespace = SITEMAP_NS)
            List<SitemapEntry> sitemaps) {}

    record SitemapEntry(
            @JacksonXmlProperty(namespace = SITEMAP_NS) String loc,
            @JacksonXmlProperty(namespace = SITEMAP_NS) String lastmod) {}

    @JsonRootName(value = "urlset", namespace = SITEMAP_NS)
    record Urlset(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "url", namespace = SITEMAP_NS)
            List<UrlEntry> urls) {}

    record UrlEntry(
            @JacksonXmlProperty(namespace = SITEMAP_NS) String loc,
            @JacksonXmlProperty(namespace = SITEMAP_NS) String priority,
            @JacksonXmlProperty(namespace = SITEMAP_NS) String changefreq,
            @JacksonXmlProperty(namespace = SITEMAP_NS) String lastmod) {}
}
