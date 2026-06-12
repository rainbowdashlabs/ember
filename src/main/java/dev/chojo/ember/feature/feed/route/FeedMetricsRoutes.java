/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.roles.InstancePermission;
import dev.chojo.ember.feature.feed.repository.FeedMetricsRepository;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Admin routes for inspecting the personal feed observability tables.
 *
 * <p>{@code /admin/feed-metrics} returns the per-day render histogram and totals; the
 * matching frontend admin panel uses the result to chart load and latency by feed kind.
 * {@code /admin/feed-metrics/user-agents} returns the top global reader user-agents so the
 * team can see which clients are exercised in production.
 */
@Singleton
public class FeedMetricsRoutes implements Routes {
    private final FeedMetricsService service;

    @Inject
    public FeedMetricsRoutes(FeedMetricsService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/feed-metrics", this::dailyMetrics, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/feed-metrics/user-agents", this::topUserAgents, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/feed-metrics",
            methods = HttpMethod.GET,
            summary = "Get the personal feed render histogram per day",
            tags = {"Feed Metrics"},
            queryParams =
                    @OpenApiParam(
                            name = "days",
                            type = Integer.class,
                            description = "Lookback window in days, default 30"),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = FeedMetricsRepository.FeedMetricDaily[].class)))
    private void dailyMetrics(Context ctx) {
        int days = ctx.queryParamAsClass("days", Integer.class).getOrDefault(30);
        ctx.json(service.recentDailyMetrics(days));
    }

    @OpenApi(
            path = "/api/v1/admin/feed-metrics/user-agents",
            methods = HttpMethod.GET,
            summary = "Get the top feed reader user-agents (global aggregate, no per-token attribution)",
            tags = {"Feed Metrics"},
            queryParams =
                    @OpenApiParam(
                            name = "limit",
                            type = Integer.class,
                            description = "Maximum number of rows, default 50"),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = FeedMetricsRepository.FeedUserAgentStat[].class)))
    private void topUserAgents(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        List<FeedMetricsRepository.FeedUserAgentStat> stats = service.topUserAgents(limit);
        ctx.json(new UserAgentsResponse(service.totalRequests(), stats));
    }

    /** Wrapper for the user-agent endpoint so the response carries the global total too. */
    public record UserAgentsResponse(long totalRequests, List<FeedMetricsRepository.FeedUserAgentStat> userAgents) {}
}
