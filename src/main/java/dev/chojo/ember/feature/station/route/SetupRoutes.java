/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.station.service.SetupService;
import dev.chojo.ember.feature.station.service.SetupService.SetupStatus;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Routes that back the first-login station-setup wizard. The wizard is administrator-only and
 * derives step completion from existing tables, so these endpoints carry no per-step state of
 * their own.
 */
@Singleton
public class SetupRoutes implements Routes {

    private final SetupService setupService;

    @Inject
    public SetupRoutes(SetupService setupService) {
        this.setupService = setupService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/setup/status", this::getStatus, StationPermission.STATION_ADMINISTRATOR);
        routes.post(prefix + "/station/setup/complete", this::complete, StationPermission.STATION_ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/station/setup/status",
            methods = HttpMethod.GET,
            summary = "Read the station setup wizard status",
            description = "Returns the per-step completion state derived from the underlying tables, plus the "
                    + "timestamp at which the wizard was finished (null while still pending).",
            tags = {"Station Setup"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SetupStatus.class)))
    private void getStatus(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(setupService.getStatus(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/station/setup/complete",
            methods = HttpMethod.POST,
            summary = "Mark the station setup wizard as finished",
            description = "Stamps the station's completion timestamp once every required step is satisfied. "
                    + "Returns 409 with the list of missing step ids when called too early. Idempotent.",
            tags = {"Station Setup"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SetupStatus.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = MissingStepsResponse.class))
            })
    private void complete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var result = setupService.complete(session.stationId());
        switch (result) {
            case COMPLETED, ALREADY_COMPLETE -> ctx.json(setupService.getStatus(session.stationId()));
            case MISSING_REQUIRED_STEPS ->
                ctx.status(HttpStatus.CONFLICT)
                        .json(new MissingStepsResponse(setupService.findMissingRequiredSteps(session.stationId())));
        }
    }

    /**
     * 409 body returned when {@link #complete(Context)} is called before every required step is
     * complete.
     *
     * @param missingSteps required step ids that are still incomplete
     */
    public record MissingStepsResponse(List<String> missingSteps) {}
}
