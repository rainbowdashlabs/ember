/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.statistics.entity.AdminOverview;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics;
import dev.chojo.ember.feature.statistics.entity.StationStatistics;
import dev.chojo.ember.feature.statistics.service.StatisticsService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Routes for station and system-wide statistics including member counts,
 * attendance summaries, and admin dashboard metrics.
 */
@Singleton
public class StatisticsRoutes implements Routes {

    private final StatisticsService statistics;

    @Inject
    public StatisticsRoutes(StatisticsService statistics) {
        this.statistics = statistics;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/statistics", this::getStatistics, StationPermission.STATION_STATISTICS);
        routes.get(prefix + "/admin/statistics", this::getAdminStatistics, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/overview", this::getAdminOverview, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/statistics",
            methods = HttpMethod.GET,
            summary = "Get station statistics",
            tags = {"Statistics"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationStatistics.class)))
    private void getStatistics(Context ctx) {
        ctx.json(statistics.forStation(StationSession.from(ctx).stationId()));
    }

    @OpenApi(
            path = "/api/v1/admin/statistics",
            methods = HttpMethod.GET,
            summary = "Get admin-level statistics",
            tags = {"Statistics"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AdminStatistics.class)))
    private void getAdminStatistics(Context ctx) {
        ctx.json(statistics.forInstance());
    }

    @OpenApi(
            path = "/api/v1/admin/overview",
            methods = HttpMethod.GET,
            summary = "Aggregated 'needs attention' metrics for the instance admin dashboard",
            tags = {"Statistics"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AdminOverview.class)))
    private void getAdminOverview(Context ctx) {
        ctx.json(statistics.overview());
    }
}
