/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Admin-only routes for viewing and acknowledging application problems (errors/warnings).
 */
@Singleton
public class ProblemRoutes implements Routes {

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/problems", this::listProblems, Roles.ADMIN);
        routes.post(prefix + "/admin/problems/{id}/acknowledge", this::acknowledge, Roles.ADMIN);
        routes.post(prefix + "/admin/problems/acknowledge-all", this::acknowledgeAll, Roles.ADMIN);
    }

    private void listProblems(Context ctx) {
        boolean includeAcknowledged = "true".equals(ctx.queryParam("includeAcknowledged"));
        var appender = ProblemLogAppender.instance();
        if (appender == null) {
            ctx.json(List.of());
            return;
        }
        ctx.json(appender.getProblems(includeAcknowledged).stream()
                .map(ProblemLogAppender.ProblemEntry::toMap)
                .toList());
    }

    private void acknowledge(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        var appender = ProblemLogAppender.instance();
        if (appender == null || !appender.acknowledge(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void acknowledgeAll(Context ctx) {
        var appender = ProblemLogAppender.instance();
        int count = appender != null ? appender.acknowledgeAll() : 0;
        ctx.json(Map.of("acknowledged", count));
    }
}
