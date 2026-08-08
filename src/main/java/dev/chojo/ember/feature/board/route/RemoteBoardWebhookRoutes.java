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

    private final FederatedBoardService federatedBoardService;
    private final RemoteBoardGuards guards;

    @Inject
    public RemoteBoardWebhookRoutes(FederatedBoardService federatedBoardService, RemoteBoardGuards guards) {
        this.federatedBoardService = federatedBoardService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String rp = prefix + "/remote/boards/webhook";
        routes.post(rp + "/ticket-changed", this::onTicketChanged);
        routes.post(rp + "/mention", this::onMention);
        routes.post(rp + "/assignment", this::onAssignment);
        routes.post(rp + "/unassignment", this::onUnassignment);
        routes.post(rp + "/board-renamed", this::onBoardRenamed);
        routes.post(rp + "/board-unshared", this::onBoardUnshared);
        routes.post(rp + "/share-mode-changed", this::onShareModeChanged);
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
