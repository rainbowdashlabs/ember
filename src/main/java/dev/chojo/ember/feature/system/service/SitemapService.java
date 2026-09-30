/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
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

/**
 * The sitemaps crawlers are pointed at: one index naming every station with something public, and
 * one map per station of what it publishes. Each is kept for six hours once written.
 */
@Singleton
public class SitemapService {
    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final XmlMapper XML_MAPPER = XmlMapper.builder()
            .enable(XmlWriteFeature.WRITE_XML_DECLARATION)
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();
    private static final DateTimeFormatter W3C_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String INDEX_CACHE_KEY = "__index__";

    private final Cache<String, String> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).build();
    private final StationRepository stations;
    private final PageService pageService;
    private final KnowledgeBaseService kbService;
    private final Api apiConfig;

    @Inject
    public SitemapService(
            StationRepository stations, PageService pageService, KnowledgeBaseService kbService, Api apiConfig) {
        this.stations = stations;
        this.pageService = pageService;
        this.kbService = kbService;
        this.apiConfig = apiConfig;
    }

    private static String formatDate(Instant instant) {
        if (instant == null) return null;
        return W3C_DATETIME.format(instant.atOffset(ZoneOffset.UTC));
    }

    private static Instant later(Instant current, Instant candidate) {
        if (candidate == null) return current;
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    /**
     * The index of every station's sitemap, as XML.
     */
    public String index() {
        return cache.get(INDEX_CACHE_KEY, _ -> {
            var baseUrl = baseUrl();
            var sitemaps = new ArrayList<SitemapEntry>();
            sitemaps.add(new SitemapEntry(baseUrl + "/sitemap-static.xml", null));
            for (var station : publicStations()) {
                sitemaps.add(new SitemapEntry(baseUrl + "/sitemap-station-" + station.uid() + ".xml", null));
            }
            return XML_MAPPER.writeValueAsString(new SitemapIndex(sitemaps));
        });
    }

    /**
     * One station's sitemap, as XML. A station that is not here, or publishes nothing, has none,
     * and the two are not told apart.
     */
    public String forStation(UUID stationUid) {
        var station = stations.findByUid(stationUid).orElseThrow(Refusal.SITEMAP_NOT_HERE::raise);
        if (!hasPublicContent(station)) throw Refusal.SITEMAP_NOT_HERE.raise();
        return cache.get(stationUid.toString(), _ -> stationXml(station));
    }

    private String stationXml(Station station) {
        var slug = station.publicSlug() != null
                ? station.publicSlug()
                : station.uid().toString();
        var stationBase = baseUrl() + "/public/station/" + slug;
        var urls = new ArrayList<UrlEntry>();
        if (station.publicCalendarEnabled()) {
            urls.add(new UrlEntry(stationBase + "/calendar", "0.7", "daily", null));
        }
        Instant latestMod = null;
        if (station.publicKbMode() != PublicKbMode.OFF) {
            latestMod = addKnowledgeBase(station, stationBase, urls);
        }
        if (station.publicPagesEnabled()) {
            latestMod = later(latestMod, addPages(station, stationBase, urls));
        }
        urls.addFirst(new UrlEntry(stationBase, "0.8", "weekly", formatDate(latestMod)));
        return XML_MAPPER.writeValueAsString(new Urlset(urls));
    }

    private Instant addKnowledgeBase(Station station, String stationBase, List<UrlEntry> urls) {
        Instant latest = null;
        for (var file : kbService.findAllPublicFiles(station.id(), station.publicKbMode())) {
            urls.add(new UrlEntry(
                    stationBase + "/knowledge/file/" + file.id(), "0.5", "weekly", formatDate(file.updatedAt())));
            latest = later(latest, file.updatedAt());
        }
        urls.add(new UrlEntry(stationBase + "/knowledge", "0.6", "weekly", formatDate(latest)));
        return latest;
    }

    private Instant addPages(Station station, String stationBase, List<UrlEntry> urls) {
        Instant latest = null;
        for (var page : pageService.listListedPages(station.id())) {
            var priority = page.parentId() == null ? "0.7" : "0.6";
            urls.add(new UrlEntry(
                    stationBase + "/page/" + pageService.getPagePath(page),
                    priority,
                    "weekly",
                    formatDate(page.updatedAt())));
            latest = later(latest, page.updatedAt());
        }
        return latest;
    }

    /**
     * A cluster's home station is left out: it is a shell nobody joins, with no public presence to point at.
     */
    private List<Station> publicStations() {
        return stations.findAllRegular().stream().filter(this::hasPublicContent).toList();
    }

    /**
     * Whether a crawler is told this station exists at all.
     *
     * <p>Pages are counted rather than the switch that allows them. A station whose only page is one
     * reached by its own link has nothing for a crawler to find, and naming it here would advertise
     * it on the strength of a page nobody is meant to come across.
     */
    private boolean hasPublicContent(Station station) {
        return station.publicCalendarEnabled()
                || station.publicKbMode() != PublicKbMode.OFF
                || (station.publicPagesEnabled() && pageService.hasListedPages(station.id()))
                || station.publicWaitlistEnabled()
                || station.publicBlogEnabled();
    }

    /**
     * The address a crawler is meant to follow, taken from configuration rather than from the
     * request.
     *
     * <p>The sitemap is fetched through the web server, which proxies it on rather than passing
     * the reader's own request along, so the host the backend sees is the one container calling
     * another. Reading it off the request published a sitemap pointing at an address that exists
     * only inside the deployment, which no crawler can follow. The answer is cached for six hours
     * under a key that does not name a host, so a single such request left it that way for
     * everybody until it expired.
     */
    private String baseUrl() {
        String configured = apiConfig.baseUrl();
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
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
