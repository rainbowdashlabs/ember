/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.InsightsRefusal;
import dev.chojo.ember.feature.insights.service.PageInsightsService;
import dev.chojo.ember.feature.insights.service.PageInsightsService.LeaderboardResponse;
import dev.chojo.ember.feature.insights.service.PageInsightsService.PageDetailResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * Station-scoped public-page analytics endpoints. All routes are gated on
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

    private final PageInsightsService insights;

    @Inject
    public StationInsightsRoutes(PageInsightsService insights) {
        this.insights = insights;
    }

    private static int parsePageId(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("pageId"));
        } catch (NumberFormatException e) {
            throw InsightsRefusal.INSIGHTS_PAGE_NOT_A_NUMBER.raise();
        }
    }

    private static Instant parseInstant(Context ctx, String paramName) {
        String raw = ctx.queryParam(paramName);
        if (raw == null || raw.isBlank()) {
            throw InsightsRefusal.INSIGHTS_WINDOW_MISSING.raise();
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            throw InsightsRefusal.INSIGHTS_WINDOW_NOT_A_MOMENT.raise(raw);
        }
    }

    private static int parseOptionalLimit(Context ctx) {
        String raw = ctx.queryParam("limit");
        if (raw == null || raw.isBlank()) return DEFAULT_LEADERBOARD_LIMIT;
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed <= 0 || parsed > 500) {
                throw InsightsRefusal.INSIGHTS_LIMIT_OUT_OF_RANGE.raise();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw InsightsRefusal.INSIGHTS_LIMIT_NOT_A_NUMBER.raise(raw);
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/insights/pages", this::leaderboard, StationPermission.STATION_ADMINISTRATOR);
        routes.get(
                prefix + "/station/insights/pages/{pageId}", this::pageDetail, StationPermission.STATION_ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/station/insights/pages",
            methods = HttpMethod.GET,
            summary = "The station's public pages ranked by hits in a window",
            tags = {"Insights"},
            queryParams = {
                @OpenApiParam(name = "from", type = Instant.class, required = true),
                @OpenApiParam(name = "to", type = Instant.class, required = true),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LeaderboardResponse.class)))
    private void leaderboard(Context ctx) {
        int stationId = StationSession.from(ctx).stationId();
        Instant from = parseInstant(ctx, "from");
        Instant to = parseInstant(ctx, "to");
        if (to.isBefore(from)) {
            throw InsightsRefusal.INSIGHTS_WINDOW_ENDS_BEFORE_IT_STARTS.raise();
        }
        ctx.json(insights.leaderboard(stationId, from, to, parseOptionalLimit(ctx)));
    }

    @OpenApi(
            path = "/api/v1/station/insights/pages/{pageId}",
            methods = HttpMethod.GET,
            summary = "Hourly, country and referrer breakdowns of one public page",
            tags = {"Insights"},
            pathParams = @OpenApiParam(name = "pageId", type = Integer.class, required = true),
            queryParams = {
                @OpenApiParam(name = "from", type = Instant.class, required = true),
                @OpenApiParam(name = "to", type = Instant.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PageDetailResponse.class)))
    private void pageDetail(Context ctx) {
        int stationId = StationSession.from(ctx).stationId();
        int pageId = parsePageId(ctx);
        Instant from = parseInstant(ctx, "from");
        Instant to = parseInstant(ctx, "to");
        if (to.isBefore(from)) {
            throw InsightsRefusal.INSIGHTS_PAGE_WINDOW_ENDS_BEFORE_IT_STARTS.raise();
        }
        ctx.json(insights.pageDetail(stationId, pageId, from, to));
    }
}
