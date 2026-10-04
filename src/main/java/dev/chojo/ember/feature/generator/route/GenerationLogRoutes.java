/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.generator.service.GenerationLogService;
import dev.chojo.ember.feature.generator.service.GenerationLogService.GeneratedDocumentEntry;
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
 * The list of documents a station generated from templates. Reading it needs the right to read the
 * documents of members, since it says who has which document.
 */
@Singleton
public class GenerationLogRoutes implements Routes {
    private final GenerationLogService log;

    @Inject
    public GenerationLogRoutes(GenerationLogService log) {
        this.log = log;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/document-generation/log", this::list, StationPermission.DOCUMENT_READ_MEMBER);
    }

    @OpenApi(
            path = "/api/v1/document-generation/log",
            methods = HttpMethod.GET,
            summary = "The documents the station generated from a template, the newest first, one page at a time",
            tags = {"Documents"},
            queryParams = {
                @OpenApiParam(
                        name = "limit",
                        type = Integer.class,
                        description = "How many entries at most, 500 where none is given, never more than 1000"),
                @OpenApiParam(
                        name = "offset",
                        type = Integer.class,
                        description = "How many of the newest entries to pass over, none where none is given")
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = GeneratedDocumentEntry[].class)))
    private void list(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(GenerationLogService.DEFAULT_LIMIT);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        ctx.json(log.list(StationSession.from(ctx).stationId(), limit, offset));
    }
}
