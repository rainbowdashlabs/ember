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
import dev.chojo.ember.feature.messages.route.MessageWire.ConversationMessagePage;
import dev.chojo.ember.feature.messages.route.MessageWire.ConversationMessageResponse;
import dev.chojo.ember.feature.messages.service.MessageService;
import dev.chojo.ember.feature.messages.service.ReactionService;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The messages of one conversation: reading them a page at a time, writing, changing and deleting
 * one's own, and reacting. Every route belongs to the signed-in account and to no station.
 */
@Singleton
public class MessageRoutes implements Routes {
    private static final String BELONGS_TO_PEOPLE =
            "a message belongs to a conversation of accounts, and MessageGuards admits only those taking part";

    private final MessageService messages;
    private final ReactionService reactions;

    @Inject
    public MessageRoutes(MessageService messages, ReactionService reactions) {
        this.messages = messages;
        this.reactions = reactions;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/messages/conversations/{conversationUid}/messages";
        routes.get(base, this::page, StationPermission.LOGIN);
        routes.post(base, this::send, StationPermission.LOGIN);
        routes.get(base + "/{messageId}", this::one, StationPermission.LOGIN);
        routes.put(base + "/{messageId}", this::edit, StationPermission.LOGIN);
        routes.delete(base + "/{messageId}", this::delete, StationPermission.LOGIN);
        routes.post(base + "/{messageId}/reactions", this::react, StationPermission.LOGIN);
        routes.delete(base + "/{messageId}/reactions", this::takeBack, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages",
            methods = HttpMethod.GET,
            summary = "A page of a conversation, oldest first",
            description = "The newest messages, those before one message, or those after it.",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            queryParams = {
                @OpenApiParam(name = "before", type = Integer.class),
                @OpenApiParam(name = "after", type = Integer.class)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConversationMessagePage.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void page(Context ctx) {
        var session = UserSession.from(ctx);
        Integer before = ctx.queryParamAsClass("before", Integer.class).getOrNull();
        Integer after = ctx.queryParamAsClass("after", Integer.class).getOrNull();
        var page = messages.page(session.accountId(), pathUuid(ctx, "conversationUid"), before, after);
        ctx.json(ConversationMessagePage.of(page, session.account().uid()));
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages",
            methods = HttpMethod.POST,
            summary = "Write a message",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WriteMessageRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ConversationMessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void send(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(WriteMessageRequest.class);
        var written = messages.send(
                session.accountId(),
                pathUuid(ctx, "conversationUid"),
                Objects.requireNonNullElse(request.text(), ""),
                request.replyToId());
        ctx.status(HttpStatus.CREATED);
        ctx.json(ConversationMessageResponse.of(written, session.account().uid()));
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages/{messageId}",
            methods = HttpMethod.GET,
            summary = "One message of a conversation",
            tags = {"Messages"},
            pathParams = {
                @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "messageId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConversationMessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void one(Context ctx) {
        var session = UserSession.from(ctx);
        var message = messages.one(session.accountId(), pathUuid(ctx, "conversationUid"), pathInt(ctx, "messageId"));
        ctx.json(ConversationMessageResponse.of(message, session.account().uid()));
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages/{messageId}",
            methods = HttpMethod.PUT,
            summary = "Change the text of one's own message",
            tags = {"Messages"},
            pathParams = {
                @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "messageId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EditMessageRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConversationMessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void edit(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(EditMessageRequest.class);
        var changed = messages.edit(
                session.accountId(),
                pathUuid(ctx, "conversationUid"),
                pathInt(ctx, "messageId"),
                Objects.requireNonNullElse(request.text(), ""));
        ctx.json(ConversationMessageResponse.of(changed, session.account().uid()));
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages/{messageId}",
            methods = HttpMethod.DELETE,
            summary = "Delete one's own message; its place stays",
            tags = {"Messages"},
            pathParams = {
                @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "messageId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void delete(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        messages.delete(accountId, pathUuid(ctx, "conversationUid"), pathInt(ctx, "messageId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages/{messageId}/reactions",
            methods = HttpMethod.POST,
            summary = "React to a message with a standard emoji",
            tags = {"Messages"},
            pathParams = {
                @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "messageId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReactionRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConversationMessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void react(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ReactionRequest.class);
        var reacted = reactions.react(
                session.accountId(),
                pathUuid(ctx, "conversationUid"),
                pathInt(ctx, "messageId"),
                Objects.requireNonNullElse(request.emoji(), ""));
        ctx.json(ConversationMessageResponse.of(reacted, session.account().uid()));
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages/{messageId}/reactions",
            methods = HttpMethod.DELETE,
            summary = "Take one's own reaction to a message back",
            tags = {"Messages"},
            pathParams = {
                @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "messageId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "emoji", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConversationMessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void takeBack(Context ctx) {
        var session = UserSession.from(ctx);
        var taken = reactions.takeBack(
                session.accountId(),
                pathUuid(ctx, "conversationUid"),
                pathInt(ctx, "messageId"),
                Objects.requireNonNullElse(ctx.queryParam("emoji"), ""));
        ctx.json(ConversationMessageResponse.of(taken, session.account().uid()));
    }

    /**
     * @param text      the text, with line breaks and mentions
     * @param replyToId the message it answers, or absent
     */
    public record WriteMessageRequest(
            @Nullable String text, @Nullable Integer replyToId) {}

    /**
     * @param text the new text
     */
    public record EditMessageRequest(@Nullable String text) {}

    /**
     * @param emoji a standard emoji
     */
    public record ReactionRequest(@Nullable String emoji) {}
}
