/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteBoardRenamedWebhook;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteBoardUnsharedWebhook;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteShareModeChangedWebhook;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Notification receivers a partner station calls when something changed on a board it shares with
 * us. Their literal {@code /remote/boards/webhook/*} paths sit under the same prefix as the
 * {@code /remote/boards/{boardKey}/*} routes, so this class is bound before all other remote board
 * route classes.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class RemoteBoardWebhookRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(RemoteBoardWebhookRoutes.class);

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

    private final FederatedBoardService federatedBoardService;
    private final RemoteBoardGuards guards;

    @Inject
    public RemoteBoardWebhookRoutes(FederatedBoardService federatedBoardService, RemoteBoardGuards guards) {
        this.federatedBoardService = federatedBoardService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(TICKET_CHANGED, this::onTicketChanged)
                        .handle(MENTION, this::onMention)
                        .handle(ASSIGNMENT, this::onAssignment)
                        .handle(UNASSIGNMENT, this::onUnassignment)
                        .handle(BOARD_RENAMED, this::onBoardRenamed)
                        .handle(BOARD_UNSHARED, this::onBoardUnshared)
                        .handle(SHARE_MODE_CHANGED, this::onShareModeChanged));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/ticket-changed",
            methods = HttpMethod.POST,
            summary = "Webhook: ticket changed notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void onTicketChanged(Context ctx) {
        guards.requirePartner(ctx);
        log.info("Received board ticket-changed webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/mention",
            methods = HttpMethod.POST,
            summary = "Webhook: mention notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void onMention(Context ctx) {
        guards.requirePartner(ctx);
        log.info("Received board mention webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/assignment",
            methods = HttpMethod.POST,
            summary = "Webhook: assignment notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void onAssignment(Context ctx) {
        guards.requirePartner(ctx);
        log.info("Received board assignment webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/unassignment",
            methods = HttpMethod.POST,
            summary = "Webhook: unassignment notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void onUnassignment(Context ctx) {
        guards.requirePartner(ctx);
        log.info("Received board unassignment webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/board-renamed",
            methods = HttpMethod.POST,
            summary = "Webhook: board renamed notification",
            tags = {"Boards Remote"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteBoardRenamedWebhook.class)),
            responses = @OpenApiResponse(status = "204"))
    private void onBoardRenamed(Context ctx) {
        var partner = guards.requirePartner(ctx);
        var req = ctx.bodyAsClass(RemoteBoardRenamedWebhook.class);
        federatedBoardService.updateBookmarkName(partner.id(), req.boardUid(), req.newName(), req.newShortKey());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/board-unshared",
            methods = HttpMethod.POST,
            summary = "Webhook: board unshared notification",
            tags = {"Boards Remote"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteBoardUnsharedWebhook.class)),
            responses = @OpenApiResponse(status = "204"))
    private void onBoardUnshared(Context ctx) {
        var partner = guards.requirePartner(ctx);
        var req = ctx.bodyAsClass(RemoteBoardUnsharedWebhook.class);
        federatedBoardService.deleteBookmarksByBoard(partner.id(), req.boardUid());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/share-mode-changed",
            methods = HttpMethod.POST,
            summary = "Webhook: share mode changed notification",
            tags = {"Boards Remote"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteShareModeChangedWebhook.class)),
            responses = @OpenApiResponse(status = "204"))
    private void onShareModeChanged(Context ctx) {
        var partner = guards.requirePartner(ctx);
        var req = ctx.bodyAsClass(RemoteShareModeChangedWebhook.class);
        federatedBoardService.updateBookmarkShareMode(partner.id(), req.boardUid(), req.shareMode());
        ctx.status(204);
    }
}
