/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.system.service.RequirementsService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

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

    private void getRequirements(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null || session.stationId() == null) {
            ctx.json(new RequirementsService.RequirementsResponse(List.of(), List.of(), false));
            return;
        }
        ctx.json(requirementsService.getRequirements(
                session.member().id(),
                session.stationId(),
                session.permissions().stream().map(Enum::name).toList()));
    }
}
