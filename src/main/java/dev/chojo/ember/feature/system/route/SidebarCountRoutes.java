/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.feature.system.service.SidebarCountService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SidebarCountRoutes implements Routes {
    private final SidebarCountService sidebarCountService;

    @Inject
    public SidebarCountRoutes(SidebarCountService sidebarCountService) {
        this.sidebarCountService = sidebarCountService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/sidebar-counts", this::getSidebarCounts, StationPermission.LOGIN);
    }

    private void getSidebarCounts(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null || session.stationId() == null) {
            ctx.json(new SidebarCountService.SidebarCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            return;
        }
        ctx.json(sidebarCountService.getCounts(session));
    }
}
