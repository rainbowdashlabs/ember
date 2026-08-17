/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.entity.TrafficBucket;
import dev.chojo.ember.feature.traffic.repository.StationTrafficRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

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

    private final StationTrafficRepository repository;

    @Inject
    public AdminTrafficRoutes(StationTrafficRepository repository) {
        this.repository = repository;
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

    private static Integer parseOptionalInt(Context ctx, String paramName) {
        String raw = ctx.queryParam(paramName);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse(paramName + " must be an integer");
        }
    }

    private static AuthBucket parseOptionalAuth(Context ctx) {
        String raw = ctx.queryParam("auth");
        if (raw == null || raw.isBlank()) return null;
        try {
            return AuthBucket.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("auth must be one of: AUTHENTICATED, UNAUTHENTICATED, FEDERATION");
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
            throw new BadRequestResponse("`to` must be on or after `from`");
        }
        Integer stationId = parseOptionalInt(ctx, "stationId");
        AuthBucket auth = parseOptionalAuth(ctx);

        List<TrafficBucket> buckets = repository.findHourly(from, to, stationId, auth);
        ctx.json(new HourlyTrafficResponse(
                buckets.stream().map(HourlyTrafficRow::from).toList()));
    }

    /**
     * Wire-shape response payload for the hourly endpoint.
     */
    public record HourlyTrafficRow(
            Instant hour, Integer stationId, AuthBucket auth, long ingressBytes, long egressBytes, long requests) {
        static HourlyTrafficRow from(TrafficBucket b) {
            return new HourlyTrafficRow(
                    b.hour(), b.stationId(), b.auth(), b.ingressBytes(), b.egressBytes(), b.requests());
        }
    }

    /**
     * Container response so additional aggregations can be added without bumping the API
     * version (e.g. summary totals once phase 14 introduces the egress cap).
     */
    public record HourlyTrafficResponse(List<HourlyTrafficRow> rows) {}
}
