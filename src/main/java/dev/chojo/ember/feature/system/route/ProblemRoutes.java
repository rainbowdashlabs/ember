/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Admin-only routes for viewing and acknowledging application problems (errors/warnings).
 */
@Singleton
public class ProblemRoutes implements Routes {

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/problems", this::listProblems, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/problems/{id}/acknowledge", this::acknowledge, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/problems/acknowledge-all", this::acknowledgeAll, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/problems",
            methods = HttpMethod.GET,
            summary = "List application problems (errors and warnings)",
            tags = {"Problems"},
            queryParams = @OpenApiParam(name = "includeAcknowledged", type = Boolean.class),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ProblemLogAppender.Snapshot[].class)))
    private void listProblems(Context ctx) {
        boolean includeAcknowledged = "true".equals(ctx.queryParam("includeAcknowledged"));
        var appender = ProblemLogAppender.instance();
        if (appender == null) {
            ctx.json(List.of());
            return;
        }
        ctx.json(appender.getProblems(includeAcknowledged).stream()
                .map(ProblemLogAppender.ProblemEntry::snapshot)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/admin/problems/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Acknowledge a specific problem",
            tags = {"Problems"},
            pathParams = @OpenApiParam(name = "id", type = Long.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void acknowledge(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        var appender = ProblemLogAppender.instance();
        if (appender == null || !appender.acknowledge(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/problems/acknowledge-all",
            methods = HttpMethod.POST,
            summary = "Acknowledge all unacknowledged problems",
            tags = {"Problems"},
            responses = @OpenApiResponse(status = "200"))
    private void acknowledgeAll(Context ctx) {
        var appender = ProblemLogAppender.instance();
        int count = appender != null ? appender.acknowledgeAll() : 0;
        ctx.json(new AcknowledgeResult(count));
    }

    private record AcknowledgeResult(int acknowledged) {}
}
