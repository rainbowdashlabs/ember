/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.service.LendingCalendarService;
import dev.chojo.ember.feature.federation.service.LendingCalendarService.LendingCalendar;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** The lending station's calendar of requests and block periods, for whoever manages lending. */
@Singleton
public class LendingCalendarRoutes implements Routes {
    private final LendingCalendarService service;

    @Inject
    public LendingCalendarRoutes(LendingCalendarService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/lending/calendar",
                this::calendar,
                StationPermission.INVENTORY_LENDING_MANAGER,
                StationModule.INVENTORY_LENDING);
    }

    @OpenApi(
            path = "/api/v1/lending/calendar",
            methods = HttpMethod.GET,
            summary = "The station's products, the requests for its gear and its block periods over some days",
            queryParams = {
                @OpenApiParam(name = "from", required = true, description = "First day, yyyy-MM-dd"),
                @OpenApiParam(name = "to", required = true, description = "Last day, yyyy-MM-dd")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LendingCalendar.class)))
    private void calendar(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(service.calendar(session.stationId(), day(ctx, "from"), day(ctx, "to"), LocalDate.now()));
    }

    private static LocalDate day(Context ctx, String name) {
        String value = ctx.queryParam(name);
        try {
            if (value == null) throw FederationRefusal.LENDING_DAY_NOT_A_DAY.raise();
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw FederationRefusal.LENDING_DAY_NOT_A_DAY.raise();
        }
    }
}
