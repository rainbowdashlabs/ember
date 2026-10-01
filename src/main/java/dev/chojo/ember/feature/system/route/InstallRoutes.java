/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.refusal.InstallationRefusal;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.feature.system.service.InstallPresetService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InstallPresetRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstallPresetResponse.class)))
    private void createPreset(Context ctx) {
        var answers = ctx.bodyAsClass(InstallPresetRequest.class);
        if (answers.options() == null || answers.options().isEmpty()) {
            throw SystemRefusal.INSTALL_ANSWERS_MISSING.raise();
        }
        String code = presets.store(answers.options());
        ctx.json(new InstallPresetResponse(code, presets.lifetime().toHours()));
    }

    /**
     * Hands the answers back as shell assignments rather than as JSON.
     *
     * <p>The caller is a shell script that has to put them into its own environment, and text it can
     * read straight into itself saves carrying a JSON parser into a one-command installer.
     */
    @OpenApi(
            path = "/api/v1/public/install/{code}",
            pathParams = @OpenApiParam(name = "code", type = String.class, required = true),
            methods = HttpMethod.GET,
            summary = "The installer answers behind a code, as shell assignments",
            tags = {"Install"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = "text/plain")),
                @OpenApiResponse(status = "404")
            })
    @StationFree("the installer runs before any station exists; the code is the whole of the authorisation")
    private void readPreset(Context ctx) {
        var retryAfter = presets.tryLookup(ctx.ip());
        if (retryAfter.isPresent()) {
            ctx.status(InstallationRefusal.SETUP_TOO_OFTEN.status())
                    .header("Retry-After", String.valueOf(retryAfter.get()))
                    .json(ErrorResponseWrapper.of(
                            InstallationRefusal.SETUP_TOO_OFTEN,
                            InstallationRefusal.SETUP_TOO_OFTEN.message(),
                            retryAfter.get()));
            return;
        }
        var options = presets.find(ctx.pathParam("code")).orElseThrow(SystemRefusal.INSTALL_CODE_NOT_GOOD::raise);
        var body = new StringBuilder();
        options.forEach(
                (key, value) -> body.append(key).append('=').append(value).append('\n'));
        ctx.contentType("text/plain; charset=utf-8").result(body.toString());
    }

    /** @param options the answers, of which only the ones the installer knows are kept */
    public record InstallPresetRequest(Map<String, String> options) {}

    /** @param validForHours how long the code lasts, so the page can say it */
    public record InstallPresetResponse(String code, long validForHours) {}
}
