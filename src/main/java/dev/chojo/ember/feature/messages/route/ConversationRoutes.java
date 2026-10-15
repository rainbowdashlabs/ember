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
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.ConversationKind;
import dev.chojo.ember.feature.messages.route.MessageWire.ConversationResponse;
import dev.chojo.ember.feature.messages.route.MessageWire.ConversationSummaryResponse;
import dev.chojo.ember.feature.messages.service.ConversationService;
import dev.chojo.ember.feature.messages.service.GroupService;
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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The reader's conversations: the list, starting one, a conversation's details, and what the
 * people in a group change about it. Every route belongs to the signed-in account and to no
 * station.
 */
@Singleton
public class ConversationRoutes implements Routes {
    private static final String BELONGS_TO_PEOPLE =
            "a conversation belongs to the accounts in it, and MessageGuards admits only those taking part";

    private final ConversationService conversations;
    private final GroupService groups;

    @Inject
    public ConversationRoutes(ConversationService conversations, GroupService groups) {
        this.conversations = conversations;
        this.groups = groups;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/messages/conversations";
        routes.get(base, this::list, StationPermission.LOGIN);
        routes.post(base, this::start, StationPermission.LOGIN);
        routes.get(base + "/{conversationUid}", this::detail, StationPermission.LOGIN);
        routes.put(base + "/{conversationUid}/name", this::rename, StationPermission.LOGIN);
        routes.post(base + "/{conversationUid}/people", this::add, StationPermission.LOGIN);
        routes.delete(base + "/{conversationUid}/people/{personUid}", this::remove, StationPermission.LOGIN);
        routes.post(base + "/{conversationUid}/leave", this::leave, StationPermission.LOGIN);
        routes.put(base + "/{conversationUid}/mute", this::mute, StationPermission.LOGIN);
        routes.put(base + "/{conversationUid}/read", this::read, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations",
            methods = HttpMethod.GET,
            summary = "List the reader's conversations, the most recently active first",
            tags = {"Messages"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConversationSummaryResponse[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void list(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        ctx.json(conversations.list(accountId).stream()
                .map(ConversationSummaryResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/messages/conversations",
            methods = HttpMethod.POST,
            summary = "Open the direct conversation with somebody, or start a group",
            description = "A direct conversation that exists already is opened rather than started again.",
            tags = {"Messages"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StartConversationRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ConversationResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void start(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        var request = ctx.bodyAsClass(StartConversationRequest.class);
        List<UUID> people = request.peopleOrNone();
        Conversation started = request.kind() == ConversationKind.DIRECT && people.size() == 1
                ? conversations.openDirect(accountId, people.getFirst())
                : groups.create(accountId, people, request.name());
        ctx.status(HttpStatus.CREATED);
        ctx.json(ConversationResponse.of(conversations.detail(accountId, started.uid())));
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}",
            methods = HttpMethod.GET,
            summary = "One conversation of the reader's, with everybody in it",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConversationResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void detail(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        ctx.json(ConversationResponse.of(conversations.detail(accountId, pathUuid(ctx, "conversationUid"))));
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/name",
            methods = HttpMethod.PUT,
            summary = "Rename a group, which only its owner does",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ConversationNameRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void rename(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        var request = ctx.bodyAsClass(ConversationNameRequest.class);
        groups.rename(accountId, pathUuid(ctx, "conversationUid"), request.name());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/people",
            methods = HttpMethod.POST,
            summary = "Add people to a group",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ConversationPeopleRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void add(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        var request = ctx.bodyAsClass(ConversationPeopleRequest.class);
        groups.add(accountId, pathUuid(ctx, "conversationUid"), request.peopleOrNone());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/people/{personUid}",
            methods = HttpMethod.DELETE,
            summary = "Remove somebody from a group, which only its owner does",
            tags = {"Messages"},
            pathParams = {
                @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "personUid", type = UUID.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void remove(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        groups.remove(accountId, pathUuid(ctx, "conversationUid"), pathUuid(ctx, "personUid"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/leave",
            methods = HttpMethod.POST,
            summary = "Leave a group, or put a direct conversation away until its next message",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void leave(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        groups.leave(accountId, pathUuid(ctx, "conversationUid"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/mute",
            methods = HttpMethod.PUT,
            summary = "Mute or unmute a conversation for the reader",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ConversationMuteRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void mute(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        var request = ctx.bodyAsClass(ConversationMuteRequest.class);
        conversations.mute(accountId, pathUuid(ctx, "conversationUid"), request.muted());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/read",
            methods = HttpMethod.PUT,
            summary = "Note that the reader has read a conversation up to a message",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ConversationReadRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(BELONGS_TO_PEOPLE)
    private void read(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        var request = ctx.bodyAsClass(ConversationReadRequest.class);
        conversations.read(accountId, pathUuid(ctx, "conversationUid"), request.messageId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @param kind   DIRECT for a conversation with exactly one other person, GROUP otherwise
     * @param people the other people, by their account identity
     * @param name   a group's name, absent or blank to call it after its members
     */
    public record StartConversationRequest(
            ConversationKind kind,
            @Nullable List<UUID> people,
            @Nullable String name) {
        List<UUID> peopleOrNone() {
            return Objects.requireNonNullElse(people, List.of());
        }
    }

    /**
     * @param name the new name, absent or blank to call the group after its members
     */
    public record ConversationNameRequest(@Nullable String name) {}

    /**
     * @param people the people to add, by their account identity
     */
    public record ConversationPeopleRequest(@Nullable List<UUID> people) {
        List<UUID> peopleOrNone() {
            return Objects.requireNonNullElse(people, List.of());
        }
    }

    /**
     * @param muted whether the conversation is muted
     */
    public record ConversationMuteRequest(boolean muted) {}

    /**
     * @param messageId the message read up to
     */
    public record ConversationReadRequest(int messageId) {}
}
