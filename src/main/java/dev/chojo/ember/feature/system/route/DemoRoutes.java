/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.system.service.DemoAccountService;
import dev.chojo.ember.feature.system.service.DemoAccountService.DemoAccountsResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What kind of instance this is, and on a demo or development instance the accounts its sign-in
 * page offers. Both are public: the sign-in page asks before anybody is signed in.
 *
 * <p>The accounts are registered only on a demo or development instance. Anywhere else they would
 * list every real account of the instance to whoever asks.
 */
@Singleton
public class DemoRoutes implements Routes {

    private final Demo demo;
    private final DemoAccountService accounts;

    @Inject
    public DemoRoutes(Demo demo, DemoAccountService accounts) {
        this.demo = demo;
        this.accounts = accounts;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/demo/status", this::status);
        if (demo.enabled() || demo.dev()) {
            routes.get(prefix + "/demo/accounts", this::accounts);
        }
    }

    @OpenApi(
            path = "/api/v1/demo/status",
            methods = HttpMethod.GET,
            summary = "Tell whether this is a demo or a development instance",
            tags = {"Demo"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DemoStatusResponse.class)))
    private void status(Context ctx) {
        ctx.json(new DemoStatusResponse(demo.enabled(), demo.dev()));
    }

    @OpenApi(
            path = "/api/v1/demo/accounts",
            methods = HttpMethod.GET,
            summary = "List the accounts the sign-in page offers on a demo or development instance",
            tags = {"Demo"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DemoAccountsResponse.class)))
    private void accounts(Context ctx) {
        ctx.json(accounts.accounts());
    }

    /**
     * What kind of instance this is.
     *
     * @param demo whether it is a public demo, which seeds itself and resets when it goes idle
     * @param dev  whether it is a development instance, which also exposes the endpoints that only
     *             make sense while building the application
     */
    public record DemoStatusResponse(boolean demo, boolean dev) {}
}
