/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.system.service.ChangelogService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Hands out what every version of this instance brought.
 *
 * <p>Public, like the page that reads it and like the version the footer already shows. What is
 * guarded is whether a <em>newer</em> release exists, which is the question that would point
 * somebody at an instance still running a version with a hole in it.
 */
@Singleton
public class ChangelogRoutes implements Routes {

    private final ChangelogService changelog;

    @Inject
    public ChangelogRoutes(ChangelogService changelog) {
        this.changelog = changelog;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/changelog", this::changelog);
    }

    @OpenApi(
            path = "/api/v1/public/changelog",
            methods = HttpMethod.GET,
            summary = "What every version of this instance brought",
            tags = {"System"},
            queryParams =
                    @OpenApiParam(
                            name = "lang",
                            description = "The language to read it in, 'de' or 'en'. Defaults to German."),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ChangelogService.ChangelogEntry[].class)))
    private void changelog(Context ctx) {
        ctx.json(changelog.all(ctx.queryParam("lang")));
    }
}
