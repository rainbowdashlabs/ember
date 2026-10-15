/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.SessionUse;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.service.MessageSignals;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import io.javalin.http.sse.SseHandler;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The stream of message signals for the signed-in account, as server-sent events.
 *
 * <p>The stream carries no text of any message, only which conversation has news and what kind,
 * so the cookie is all it needs. It never keeps the session alive: an open tab would otherwise
 * hold a session for as long as it stays open. A page that cannot keep the stream open asks the
 * normal routes on a timer instead.
 */
@Singleton
public class MessageEventRoutes implements Routes {
    private final MessageSignals signals;

    @Inject
    public MessageEventRoutes(MessageSignals signals) {
        this.signals = signals;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/messages/events", this::events, StationPermission.LOGIN, SessionUse.PASSIVE);
    }

    @OpenApi(
            path = "/api/v1/messages/events",
            methods = HttpMethod.GET,
            summary = "Stream the reader's message signals as server-sent events",
            description = "Sends `ready` once open, `ping` every 20 seconds, and a `signal` naming the"
                    + " conversation and kind of each change.",
            tags = {"Messages"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = MessageEvent.class, type = "text/event-stream")))
    private void events(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        new SseHandler(0, client -> attach(accountId, client)).handle(ctx);
    }

    /**
     * Hands an opened stream to the hub, which drops it again once the tab closes.
     *
     * @param accountId the account the tab is signed in as
     * @param client    the opened stream
     */
    void attach(int accountId, SseClient client) {
        var stream = new SseMessageStream(client);
        client.keepAlive();
        client.onClose(() -> signals.leave(accountId, stream));
        signals.join(accountId, stream);
    }
}
