/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.push.service.PushService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The account's own browsers for push messages. Every route acts on the signed-in account and, for
 * registering, on the sign-in the request comes from, so nobody reaches another account's devices.
 */
@Singleton
public class PushRoutes implements Routes {
    private final PushService push;

    @Inject
    public PushRoutes(PushService push) {
        this.push = push;
    }

    /**
     * A browser's registration as the browser's push manager hands it over.
     *
     * @param endpoint the push service's address for the browser
     * @param p256dh   the browser's public key, base64url
     * @param auth     the browser's secret, base64url
     * @param label    a short name of browser and device to list it under
     * @param renewal          whether the page registers the browser again on its own, which is taken only for a
     *                         browser this account allowed and did not remove since
     * @param previousEndpoint the address the browser was registered under before it moved, sent with a renewal
     */
    public record SubscribeRequest(
            @Nullable String endpoint,
            @Nullable String p256dh,
            @Nullable String auth,
            @Nullable String label,
            boolean renewal,
            @Nullable String previousEndpoint) {}

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/push/key", this::key, StationPermission.LOGIN);
        routes.get(prefix + "/push/subscriptions", this::devices, StationPermission.LOGIN);
        routes.put(prefix + "/push/subscriptions", this::subscribe, StationPermission.LOGIN);
        routes.delete(prefix + "/push/subscriptions/{id}", this::forget, StationPermission.LOGIN);
        routes.post(prefix + "/push/subscriptions/{id}/test", this::test, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/push/key",
            methods = HttpMethod.GET,
            summary = "The key a browser subscribes to push messages with, and whether push is on",
            tags = {"Push"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PushService.PushKey.class)))
    private void key(Context ctx) {
        ctx.json(push.key());
    }

    @OpenApi(
            path = "/api/v1/push/subscriptions",
            methods = HttpMethod.GET,
            summary = "The account's browsers that receive push messages",
            tags = {"Push"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PushService.PushDevice[].class)))
    private void devices(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(push.devices(session.accountId(), session.sessionId()));
    }

    @OpenApi(
            path = "/api/v1/push/subscriptions",
            methods = HttpMethod.PUT,
            summary = "Register or refresh this browser for push messages",
            tags = {"Push"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SubscribeRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PushService.PushDevice.class)),
                @OpenApiResponse(status = "204", description = "A renewal of a browser this account did not allow")
            })
    private void subscribe(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SubscribeRequest.class);
        push.register(
                        session.accountId(),
                        session.sessionId(),
                        request.endpoint(),
                        request.p256dh(),
                        request.auth(),
                        request.label(),
                        request.renewal(),
                        request.previousEndpoint())
                .ifPresentOrElse(ctx::json, () -> ctx.status(HttpStatus.NO_CONTENT));
    }

    @OpenApi(
            path = "/api/v1/push/subscriptions/{id}",
            methods = HttpMethod.DELETE,
            summary = "Forget one of the account's browsers",
            tags = {"Push"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void forget(Context ctx) {
        push.forget(UserSession.from(ctx).accountId(), pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/push/subscriptions/{id}/test",
            methods = HttpMethod.POST,
            summary = "Send a test push to one of the account's browsers",
            tags = {"Push"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "202"))
    private void test(Context ctx) {
        push.test(UserSession.from(ctx).accountId(), pathInt(ctx, "id"));
        ctx.status(HttpStatus.ACCEPTED);
    }
}
