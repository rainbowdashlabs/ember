/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.util.DevErrorWriter;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The endpoints that only make sense while the application is being built: the frontend's error log
 * and the reset the end-to-end suite starts from.
 *
 * <p>Registered only on a development instance. The reset throws every piece of data away and has no
 * place anywhere a real station's data lives.
 */
@Singleton
public class DevRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(DevRoutes.class);

    private final Demo demo;
    private final DemoService demoService;

    @Inject
    public DevRoutes(Demo demo, DemoService demoService) {
        this.demo = demo;
        this.demoService = demoService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        if (!demo.dev()) return;
        routes.post(prefix + "/dev/errors", this::reportError);
        routes.post(prefix + "/dev/reset", this::reset);
    }

    /**
     * Writes an error the frontend reports on a development instance to the development error log.
     */
    @OpenApi(
            path = "/api/v1/dev/errors",
            methods = HttpMethod.POST,
            summary = "Write an error the frontend ran into to the development error log",
            tags = {"Development"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DevErrorReport.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reportError(Context ctx) {
        var report = ctx.bodyAsClass(DevErrorReport.class);
        DevErrorWriter.writeFrontend(
                report.source() != null ? report.source() : "unknown",
                report.message() != null ? report.message() : "",
                report.stack() != null ? report.stack() : "",
                report.context() != null ? report.context() : "");
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Throws the data away and seeds it again, so a test run starts from the state the seeder
     * describes rather than from whatever the run before it left behind.
     *
     * <p>A wipe that fails answers with the failure rather than with success, because the end-to-end
     * suite asks for this before every run and stops when it is refused. Told that a database it
     * never got was fresh, it would run its stories against whatever the run before left.
     */
    @OpenApi(
            path = "/api/v1/dev/reset",
            methods = HttpMethod.POST,
            summary = "Throw every piece of data away and seed the instance again",
            tags = {"Development"},
            responses = @OpenApiResponse(status = "204"))
    private void reset(Context ctx) {
        log.info("Dev reset requested, discarding all data and seeding again");
        demoService.resetAndSeed();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * An error the frontend ran into.
     *
     * @param source  where it was caught, such as the window or a component
     * @param message what it said
     * @param stack   where it was thrown
     * @param context the page or component it happened on
     */
    public record DevErrorReport(
            @Nullable String source,
            @Nullable String message,
            @Nullable String stack,
            @Nullable String context) {}
}
