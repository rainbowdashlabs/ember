/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.DimensionTotal;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.HourlyTotal;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.PageLeaderboardEntry;
import dev.chojo.ember.feature.page.repository.PageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How a station's public pages are visited: which pages are read most, and for one page when,
 * from where and by way of which other site. Only ever the station's own pages.
 */
@Singleton
public class PageInsightsService {
    private final PageHitRepository pageHits;
    private final PageRepository pages;

    @Inject
    public PageInsightsService(PageHitRepository pageHits, PageRepository pages) {
        this.pageHits = pageHits;
        this.pages = pages;
    }

    /**
     * The station's pages, most visited first, within a stretch of time.
     */
    public LeaderboardResponse leaderboard(int stationId, Instant from, Instant to, int limit) {
        return new LeaderboardResponse(pageHits.leaderboard(stationId, from, to, limit));
    }

    /**
     * One page of the station, visited hour by hour, by country and by the site a visitor came from.
     * A page of another station is answered as not being here.
     */
    public PageDetailResponse pageDetail(int stationId, int pageId, Instant from, Instant to) {
        var page = pages.findById(pageId).orElseThrow(Refusal.INSIGHTS_PAGE_NOT_HERE::raise);
        if (page.stationId() != stationId) {
            throw Refusal.INSIGHTS_PAGE_NOT_HERE.raise();
        }
        var raw = pageHits.findForPage(pageId, from, to);
        return new PageDetailResponse(
                sumByHour(raw, false),
                sumByHour(raw, true),
                pageHits.countryTotalsForPage(pageId, from, to),
                pageHits.refererTotalsForPage(pageId, from, to));
    }

    private static List<HourlyTotal> sumByHour(List<PageHitBucket> buckets, boolean includeBots) {
        Map<Instant, Long> bucketsByHour = new TreeMap<>();
        for (PageHitBucket b : buckets) {
            if (!includeBots && b.isBot()) continue;
            bucketsByHour.merge(b.hour(), b.hits(), Long::sum);
        }
        var out = new ArrayList<HourlyTotal>(bucketsByHour.size());
        for (var entry : bucketsByHour.entrySet()) {
            out.add(new HourlyTotal(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    /**
     * The station's pages, most visited first.
     */
    public record LeaderboardResponse(List<PageLeaderboardEntry> rows) {}

    /**
     * One page's visits, all three breakdowns together so the screen needs a single request.
     */
    public record PageDetailResponse(
            List<HourlyTotal> hourly,
            List<HourlyTotal> hourlyWithBots,
            List<DimensionTotal> countries,
            List<DimensionTotal> referrers) {}
}
