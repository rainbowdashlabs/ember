/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.system.service.MonitoringCountService;
import dev.chojo.ember.feature.system.service.MonitoringCountService.MonitoringCounts;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What is waiting for whoever runs the instance.
 *
 * <p>Everything an operator has to look at otherwise has to be found by opening the page and seeing
 * whether anything is on it. A count in the sidebar is what turns that round: nothing shown means nothing
 * to do, and a number means somebody should look.
 */
@Singleton
public class AdminMonitoringCountRoutes implements Routes {

    private final MonitoringCountService counts;

    @Inject
    public AdminMonitoringCountRoutes(MonitoringCountService counts) {
        this.counts = counts;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/monitoring-counts", this::counts, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/monitoring-counts",
            methods = HttpMethod.GET,
            summary = "What is waiting for the operator",
            tags = {"Monitoring"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MonitoringCounts.class)))
    private void counts(Context ctx) {
        ctx.json(counts.counts());
    }
}
