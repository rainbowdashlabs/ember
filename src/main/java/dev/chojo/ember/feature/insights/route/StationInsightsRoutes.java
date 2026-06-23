/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.DimensionTotal;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.HourlyTotal;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.PageLeaderboardEntry;
import dev.chojo.ember.feature.page.repository.PageRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Station-scoped public-page analytics endpoints (concept §7.7). All routes are gated on
 * {@link StationPermission#STATION_ADMINISTRATOR}; managers see only their own station's
 * pages, never another station's.
 *
 * <p>The leaderboard endpoint returns a per-page summary (hits, bot-hits) for the requested
 * window. The drill-down endpoint returns three side-by-side aggregations for a single page:
 * hourly time series, country breakdown, and referer breakdown.
 */
@Singleton
public class StationInsightsRoutes implements Routes {

    private static final int DEFAULT_LEADERBOARD_LIMIT = 50;

    private final PageHitRepository pageHits;
    private final PageRepository pages;

    @Inject
    public StationInsightsRoutes(PageHitRepository pageHits, PageRepository pages) {
        this.pageHits = pageHits;
        this.pages = pages;
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

    private static int requireStation(Context ctx) {
        var session = UserSession.from(ctx);
        if (session.stationId() == null) {
            throw new BadRequestResponse("No station selected");
        }
        return session.stationId();
    }

    private static int parsePageId(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("pageId"));
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("pageId must be an integer");
        }
    }

    private static Instant parseInstant(Context ctx, String paramName) {
        String raw = ctx.queryParam(paramName);
        if (raw == null || raw.isBlank()) {
            throw new BadRequestResponse("Missing required query parameter: " + paramName);
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            throw new BadRequestResponse(paramName + " must be an ISO-8601 instant (e.g. 2026-06-18T00:00:00Z)");
        }
    }

    private static int parseOptionalLimit(Context ctx) {
        String raw = ctx.queryParam("limit");
        if (raw == null || raw.isBlank()) return DEFAULT_LEADERBOARD_LIMIT;
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed <= 0 || parsed > 500) {
                throw new BadRequestResponse("limit must be between 1 and 500");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("limit must be an integer");
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/insights/pages", this::leaderboard, StationPermission.STATION_ADMINISTRATOR);
        routes.get(
                prefix + "/station/insights/pages/{pageId}", this::pageDetail, StationPermission.STATION_ADMINISTRATOR);
    }

    private void leaderboard(Context ctx) {
        int stationId = requireStation(ctx);
        Instant from = parseInstant(ctx, "from");
        Instant to = parseInstant(ctx, "to");
        if (to.isBefore(from)) {
            throw new BadRequestResponse("`to` must be on or after `from`");
        }
        int limit = parseOptionalLimit(ctx);

        var rows = pageHits.leaderboard(stationId, from, to, limit);
        ctx.json(new LeaderboardResponse(rows));
    }

    private void pageDetail(Context ctx) {
        int stationId = requireStation(ctx);
        int pageId = parsePageId(ctx);
        var page = pages.findById(pageId).orElseThrow(NotFoundResponse::new);
        if (page.stationId() != stationId) {
            throw new ForbiddenResponse("Page does not belong to your station");
        }
        Instant from = parseInstant(ctx, "from");
        Instant to = parseInstant(ctx, "to");
        if (to.isBefore(from)) {
            throw new BadRequestResponse("`to` must be on or after `from`");
        }

        var raw = pageHits.findForPage(pageId, from, to);
        var hourlyNoBots = sumByHour(raw, false);
        var hourlyWithBots = sumByHour(raw, true);
        var countries = pageHits.countryTotalsForPage(pageId, from, to);
        var referrers = pageHits.refererTotalsForPage(pageId, from, to);

        ctx.json(new PageDetailResponse(hourlyNoBots, hourlyWithBots, countries, referrers));
    }

    /**
     * Wire-shape response for the per-station page leaderboard.
     */
    public record LeaderboardResponse(List<PageLeaderboardEntry> rows) {}

    /**
     * Wire-shape response for the per-page drill-down. All three breakdowns are returned
     * together so the frontend renders the detail view in a single request.
     */
    public record PageDetailResponse(
            List<HourlyTotal> hourly,
            List<HourlyTotal> hourlyWithBots,
            List<DimensionTotal> countries,
            List<DimensionTotal> referrers) {}
}
