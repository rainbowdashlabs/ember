/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.system.service.RequirementsService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RequirementsRoutes implements Routes {
    private final RequirementsService requirementsService;

    @Inject
    public RequirementsRoutes(RequirementsService requirementsService) {
        this.requirementsService = requirementsService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/requirements", this::getRequirements, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/requirements",
            methods = HttpMethod.GET,
            summary = "What the reader still owes",
            tags = {"Requirements"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = RequirementsService.RequirementsResponse.class)))
    private void getRequirements(Context ctx) {
        var atStation = StationSession.optional(UserSession.from(ctx));
        if (atStation.isEmpty()) {
            ctx.json(RequirementsService.RequirementsResponse.none());
            return;
        }
        StationSession session = atStation.get();
        ctx.json(requirementsService.getRequirements(
                session.member().id(),
                session.stationId(),
                session.user().permissions().stream().map(Enum::name).toList()));
    }
}
