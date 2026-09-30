/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.service.TrafficReportService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * Instance-admin traffic monitoring routes. Returns pre-aggregated hourly rows from
 * {@code station_traffic_hourly}. Permission: {@link InstancePermission#ADMINISTRATOR}.
 *
 * <p>The endpoint deliberately exposes the underlying bucket shape directly - the frontend
 * does its own grouping / stacking depending on the chart it wants to render. Phase 2 will
 * add a station-scoped sibling.
 */
@Singleton
public class AdminTrafficRoutes implements Routes {

    private final TrafficReportService traffic;

    @Inject
    public AdminTrafficRoutes(TrafficReportService traffic) {
        this.traffic = traffic;
    }

    private static Instant parseInstant(Context ctx, String paramName) {
        String raw = ctx.queryParam(paramName);
        if (raw == null || raw.isBlank()) {
            throw Refusal.TRAFFIC_SPAN_MISSING.raise(paramName);
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            throw Refusal.TRAFFIC_SPAN_NOT_A_TIME.raise(paramName);
        }
    }

    private static Integer parseOptionalInt(Context ctx, String paramName) {
        String raw = ctx.queryParam(paramName);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw Refusal.TRAFFIC_NUMBER_NOT_A_NUMBER.raise(paramName);
        }
    }

    private static AuthBucket parseOptionalAuth(Context ctx) {
        String raw = ctx.queryParam("auth");
        if (raw == null || raw.isBlank()) return null;
        try {
            return AuthBucket.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw Refusal.TRAFFIC_KIND_UNKNOWN.raise();
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/traffic/hourly", this::hourly, InstancePermission.ADMINISTRATOR);
    }

    private void hourly(Context ctx) {
        Instant from = parseInstant(ctx, "from");
        Instant to = parseInstant(ctx, "to");
        if (to.isBefore(from)) {
            throw Refusal.TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS.raise();
        }
        ctx.json(traffic.hourly(from, to, parseOptionalInt(ctx, "stationId"), parseOptionalAuth(ctx)));
    }
}
