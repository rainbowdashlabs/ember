/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.system.service.ApiRequestLogger;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Admin routes for API request monitoring — response times, status codes, endpoint stats.
 */
@Singleton
public class ApiStatusRoutes implements Routes {

    private final ApiRequestLogger requestLogger;

    @Inject
    public ApiStatusRoutes(ApiRequestLogger requestLogger) {
        this.requestLogger = requestLogger;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/api-status/slowest", this::slowest, Roles.ADMIN);
        routes.get(prefix + "/admin/api-status/fastest", this::fastest, Roles.ADMIN);
        routes.get(prefix + "/admin/api-status/failing", this::failing, Roles.ADMIN);
        routes.get(prefix + "/admin/api-status/status-breakdown", this::statusBreakdown, Roles.ADMIN);
        routes.get(prefix + "/admin/api-status/hourly", this::hourly, Roles.ADMIN);
    }

    private void slowest(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(requestLogger.getSlowestEndpoints(limit));
    }

    private void fastest(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(requestLogger.getFastestEndpoints(limit));
    }

    private void failing(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(requestLogger.getMostFailingEndpoints(limit));
    }

    private void statusBreakdown(Context ctx) {
        ctx.json(requestLogger.getStatusBreakdown());
    }

    private void hourly(Context ctx) {
        ctx.json(requestLogger.getHourlyStats());
    }
}
