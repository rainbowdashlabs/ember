/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.repository.StationTrafficRepository;
import dev.chojo.ember.feature.traffic.route.AdminTrafficRoutes.HourlyTrafficResponse;
import dev.chojo.ember.feature.traffic.route.AdminTrafficRoutes.HourlyTrafficRow;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * Station-scoped traffic monitoring route. Returns hourly traffic rows scoped to the
 * caller's own station — managers see only their own traffic, never another station's.
 * Permission: {@link StationPermission#STATION_ADMINISTRATOR}.
 *
 * <p>Path follows the existing {@code /station/...} convention (station id derived from
 * the session), so it sits next to {@code /station/manage/...} and similar.
 */
@Singleton
public class StationTrafficRoutes implements Routes {

    private final StationTrafficRepository repository;

    @Inject
    public StationTrafficRoutes(StationTrafficRepository repository) {
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
        routes.get(prefix + "/station/traffic/hourly", this::hourly, StationPermission.STATION_ADMINISTRATOR);
    }

    private void hourly(Context ctx) {
        var session = UserSession.from(ctx);
        if (session.stationId() == null) {
            throw new BadRequestResponse("No station selected");
        }
        Instant from = parseInstant(ctx, "from");
        Instant to = parseInstant(ctx, "to");
        if (to.isBefore(from)) {
            throw new BadRequestResponse("`to` must be on or after `from`");
        }
        AuthBucket auth = parseOptionalAuth(ctx);

        var rows = repository.findHourly(from, to, session.stationId(), auth);
        ctx.json(new HourlyTrafficResponse(
                rows.stream().map(HourlyTrafficRow::from).toList()));
    }
}
