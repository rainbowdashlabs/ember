/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.system.service.InstallPresetService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * The two ends of the install page: keeping what somebody clicked together, and handing it back to
 * the script under a short code.
 *
 * <p>Both are public, because the point is to run before anything exists to log in to.
 */
@Singleton
public class InstallRoutes implements Routes {

    private final InstallPresetService presets;

    @Inject
    public InstallRoutes(InstallPresetService presets) {
        this.presets = presets;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/public/install", this::createPreset);
        routes.get(prefix + "/public/install/{code}", this::readPreset);
    }

    @OpenApi(
            path = "/api/v1/public/install",
            methods = HttpMethod.POST,
            summary = "Keep a set of installer answers and return the code that fetches them",
            tags = {"Install"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PresetResponse.class)))
    private void createPreset(Context ctx) {
        var answers = ctx.bodyAsClass(PresetRequest.class);
        if (answers.options() == null || answers.options().isEmpty()) {
            throw new BadRequestResponse("No options given");
        }
        String code = presets.store(answers.options());
        ctx.json(new PresetResponse(code, presets.lifetime().toHours()));
    }

    /**
     * Hands the answers back as shell assignments rather than as JSON.
     *
     * <p>The caller is a shell script that has to put them into its own environment, and text it can
     * read straight into itself saves carrying a JSON parser into a one-command installer.
     */
    @OpenApi(
            path = "/api/v1/public/install/{code}",
            methods = HttpMethod.GET,
            summary = "The installer answers behind a code, as shell assignments",
            tags = {"Install"},
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "404")})
    private void readPreset(Context ctx) {
        var options = presets.find(ctx.pathParam("code"))
                .orElseThrow(() -> new NotFoundResponse("Unknown or expired install code"));
        var body = new StringBuilder();
        options.forEach(
                (key, value) -> body.append(key).append('=').append(value).append('\n'));
        ctx.contentType("text/plain; charset=utf-8").result(body.toString());
    }

    /** @param options the answers, of which only the ones the installer knows are kept */
    public record PresetRequest(Map<String, String> options) {}

    /** @param validForHours how long the code lasts, so the page can say it */
    public record PresetResponse(String code, long validForHours) {}
}
