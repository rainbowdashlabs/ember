/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.messages.entity.MessagesStatus;
import dev.chojo.ember.feature.messages.route.MessageWire.MessagePersonResponse;
import dev.chojo.ember.feature.messages.route.MessageWire.ReachablePersonResponse;
import dev.chojo.ember.feature.messages.service.BlockService;
import dev.chojo.ember.feature.messages.service.MessageSettingsService;
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

import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The reader's messages beside the conversations: whether messages are on and how many wait, the
 * mail setting, finding somebody to write to, and the people the reader blocked.
 */
@Singleton
public class MessagePeopleRoutes implements Routes {
    private final MessageSettingsService settings;
    private final BlockService blocks;

    @Inject
    public MessagePeopleRoutes(MessageSettingsService settings, BlockService blocks) {
        this.settings = settings;
        this.blocks = blocks;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/messages/status", this::status, StationPermission.LOGIN);
        routes.put(prefix + "/messages/settings", this::setMail, StationPermission.LOGIN);
        routes.get(prefix + "/messages/people", this::people, StationPermission.LOGIN);
        routes.get(prefix + "/messages/blocks", this::blocked, StationPermission.LOGIN);
        routes.post(prefix + "/messages/blocks", this::block, StationPermission.LOGIN);
        routes.delete(prefix + "/messages/blocks/{personUid}", this::unblock, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/messages/status",
            methods = HttpMethod.GET,
            summary = "Whether the reader can use messages and how many wait unread",
            tags = {"Messages"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessagesStatus.class)))
    private void status(Context ctx) {
        ctx.json(settings.status(UserSession.from(ctx).accountId()));
    }

    @OpenApi(
            path = "/api/v1/messages/settings",
            methods = HttpMethod.PUT,
            summary = "Turn the mail about unread messages on or off",
            tags = {"Messages"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MessageMailRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessagesStatus.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setMail(Context ctx) {
        var request = ctx.bodyAsClass(MessageMailRequest.class);
        ctx.json(settings.setMail(UserSession.from(ctx).accountId(), request.mail()));
    }

    @OpenApi(
            path = "/api/v1/messages/people",
            methods = HttpMethod.GET,
            summary = "Find people the reader may write to: everybody sharing a station with them",
            tags = {"Messages"},
            queryParams = @OpenApiParam(name = "search", type = String.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ReachablePersonResponse[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void people(Context ctx) {
        String search = Objects.requireNonNullElse(ctx.queryParam("search"), "");
        ctx.json(settings.reachable(UserSession.from(ctx).accountId(), search).stream()
                .map(ReachablePersonResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/messages/blocks",
            methods = HttpMethod.GET,
            summary = "The people the reader blocked",
            tags = {"Messages"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessagePersonResponse[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void blocked(Context ctx) {
        ctx.json(blocks.blocked(UserSession.from(ctx).accountId()).stream()
                .map(MessagePersonResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/messages/blocks",
            methods = HttpMethod.POST,
            summary = "Block somebody for messages; they are not told",
            tags = {"Messages"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BlockPersonRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void block(Context ctx) {
        var request = ctx.bodyAsClass(BlockPersonRequest.class);
        blocks.block(UserSession.from(ctx).accountId(), request.person());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/blocks/{personUid}",
            methods = HttpMethod.DELETE,
            summary = "Take a block back",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "personUid", type = UUID.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    @StationFree("a block is one account's choice about another, and the reader only ever lifts their own")
    private void unblock(Context ctx) {
        blocks.unblock(UserSession.from(ctx).accountId(), pathUuid(ctx, "personUid"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @param mail whether the reader wants mail about messages left unread
     */
    public record MessageMailRequest(boolean mail) {}

    /**
     * @param person the account to block, by its identity
     */
    public record BlockPersonRequest(@Nullable UUID person) {}
}
