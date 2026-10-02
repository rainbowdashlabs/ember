/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Notification receivers a partner station calls when something changed on a board it shares with
 * us. Their literal {@code /remote/boards/webhook/*} paths sit under the same prefix as the
 * {@code /remote/boards/{boardKey}/*} routes, so this class is bound before all other remote board
 * route classes.
 */
@Singleton
public class RemoteBoardWebhookRoutes implements Routes {
    private static final String WEBHOOKS = "/remote/boards/webhook";

    public static final FederationEndpoint TICKET_CHANGED = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE, WEBHOOKS + "/ticket-changed", Void.class, Void.class);
    public static final FederationEndpoint MENTION =
            FederationEndpoint.post(FederationSurface.BOARD_SHARE, WEBHOOKS + "/mention", Void.class, Void.class);
    public static final FederationEndpoint ASSIGNMENT =
            FederationEndpoint.post(FederationSurface.BOARD_SHARE, WEBHOOKS + "/assignment", Void.class, Void.class);
    public static final FederationEndpoint UNASSIGNMENT =
            FederationEndpoint.post(FederationSurface.BOARD_SHARE, WEBHOOKS + "/unassignment", Void.class, Void.class);
    public static final FederationEndpoint BOARD_RENAMED = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            WEBHOOKS + "/board-renamed",
            RemoteBoardRoutes.RemoteBoardRenamedWebhook.class,
            Void.class);
    public static final FederationEndpoint BOARD_UNSHARED = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            WEBHOOKS + "/board-unshared",
            RemoteBoardRoutes.RemoteBoardUnsharedWebhook.class,
            Void.class);
    public static final FederationEndpoint SHARE_MODE_CHANGED = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            WEBHOOKS + "/share-mode-changed",
            RemoteBoardRoutes.RemoteShareModeChangedWebhook.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            TICKET_CHANGED, MENTION, ASSIGNMENT, UNASSIGNMENT, BOARD_RENAMED, BOARD_UNSHARED, SHARE_MODE_CHANGED);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteBoardWebhookRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(TICKET_CHANGED)
                .serve(MENTION)
                .serve(ASSIGNMENT)
                .serve(UNASSIGNMENT)
                .serve(BOARD_RENAMED)
                .serve(BOARD_UNSHARED)
                .serve(SHARE_MODE_CHANGED));
    }
}
