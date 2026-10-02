/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * What the instance tells anybody who asks, before they have signed in: where its demo is, whether it is
 * one, and which version it runs.
 */
@Singleton
public class PublicConfigRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(PublicConfigRoutes.class);

    private final Api api;
    private final Demo demo;
    private final String version;

    @Inject
    public PublicConfigRoutes(Api api, Demo demo) {
        this.api = api;
        this.demo = demo;
        this.version = readVersion();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/config", this::config);
    }

    @OpenApi(
            path = "/api/v1/public/config",
            methods = HttpMethod.GET,
            summary = "Tell where the demo is, whether this is one, and which version runs",
            tags = {"Public"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicConfigResponse.class)))
    private void config(Context ctx) {
        ctx.json(new PublicConfigResponse(
                api.demoUrl() != null ? api.demoUrl() : "", demo.enabled() || demo.dev(), version));
    }

    private String readVersion() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("version")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            log.warn("Failed to read version resource", e);
        }
        return "unknown";
    }

    /**
     * The public configuration of the instance.
     *
     * @param demoUrl where the demo instance is, or empty where none is named
     * @param demo    whether this is a demo or a development instance
     * @param version the version this instance runs, or {@code unknown} where the build did not say
     */
    public record PublicConfigResponse(String demoUrl, boolean demo, String version) {}
}
