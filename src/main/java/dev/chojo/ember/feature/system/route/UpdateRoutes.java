/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells whoever administers a station that a newer release exists.
 *
 * <p>Guarded rather than public: what version an instance runs is worth knowing to somebody looking
 * for an instance still running a version with a hole in it, so it is not answered to anonymous
 * callers or to ordinary members.
 */
@Singleton
public class UpdateRoutes implements Routes {

    private final UpdateCheckService updateCheckService;

    @Inject
    public UpdateRoutes(UpdateCheckService updateCheckService) {
        this.updateCheckService = updateCheckService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/system/update", this::status, StationPermission.STATION_ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/system/update",
            methods = HttpMethod.GET,
            summary = "Whether a newer release of Ember exists",
            tags = {"System"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = UpdateCheckService.UpdateStatus.class)))
    private void status(Context ctx) {
        ctx.json(updateCheckService.status());
    }
}
