/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.station.service.FirstStationService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * The first station of a fresh instance: whether it is still missing, and founding it.
 */
@Singleton
public class FirstStationRoutes implements Routes {
    private final FirstStationService firstStationService;

    @Inject
    public FirstStationRoutes(FirstStationService firstStationService) {
        this.firstStationService = firstStationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/first-station", this::status, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/first-station", this::found, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/first-station",
            methods = HttpMethod.GET,
            summary = "Whether the instance still waits for its first station",
            tags = {"Stations"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FirstStationStatus.class)))
    private void status(Context ctx) {
        ctx.json(new FirstStationStatus(firstStationService.isNeeded()));
    }

    @OpenApi(
            path = "/api/v1/admin/first-station",
            methods = HttpMethod.POST,
            summary = "Found the instance's first station",
            tags = {"Stations"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FirstStationRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = FoundedStation.class)))
    private void found(Context ctx) {
        var request = ctx.bodyAsClass(FirstStationRequest.class);
        var session = UserSession.from(ctx);
        var station =
                firstStationService.found(request.name(), session.account().id());
        ctx.status(HttpStatus.CREATED).json(new FoundedStation(station.uid(), station.name()));
    }

    /** Whether the instance still waits for its first station. */
    public record FirstStationStatus(boolean needed) {}

    /** What the first station is to be called. */
    public record FirstStationRequest(String name) {}

    /** The station just founded, for the browser to switch to it. */
    public record FoundedStation(UUID stationUid, String name) {}
}
