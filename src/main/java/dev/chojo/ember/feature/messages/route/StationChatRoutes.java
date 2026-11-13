/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.StationChatSummary;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.service.StationChatService;
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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The station's chats as the people handling them see them: the list, opening one for the whole
 * station, a group or a tag, renaming, taking the guardians in or out, closing and reopening.
 */
@Singleton
public class StationChatRoutes implements Routes {
    private final StationChatService chats;

    @Inject
    public StationChatRoutes(StationChatService chats) {
        this.chats = chats;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/messages/station-chats";
        routes.get(base, this::list, StationPermission.MESSAGE_CHAT_MANAGE);
        routes.post(base, this::open, StationPermission.MESSAGE_CHAT_MANAGE);
        routes.put(base + "/{conversationUid}/name", this::rename, StationPermission.MESSAGE_CHAT_MANAGE);
        routes.put(base + "/{conversationUid}/guardians", this::guardians, StationPermission.MESSAGE_CHAT_MANAGE);
        routes.post(base + "/{conversationUid}/close", this::close, StationPermission.MESSAGE_CHAT_MANAGE);
        routes.post(base + "/{conversationUid}/reopen", this::reopen, StationPermission.MESSAGE_CHAT_MANAGE);
    }

    @OpenApi(
            path = "/api/v1/messages/station-chats",
            methods = HttpMethod.GET,
            summary = "The station's chats, the open ones first",
            tags = {"Messages"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationChatResponse[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void list(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(chats.list(session.stationId()).stream()
                .map(StationChatResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/messages/station-chats",
            methods = HttpMethod.POST,
            summary = "Open a chat for the whole station, a group or a tag",
            tags = {"Messages"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = OpenStationChatRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = StationChatResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void open(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(OpenStationChatRequest.class);
        Conversation opened = chats.open(
                session.stationId(),
                session.user().accountId(),
                request.name(),
                request.audience(),
                request.groupId(),
                request.tagId(),
                Boolean.TRUE.equals(request.includeGuardians()));
        ctx.status(HttpStatus.CREATED);
        ctx.json(StationChatResponse.of(chats.one(session.stationId(), opened.uid())));
    }

    @OpenApi(
            path = "/api/v1/messages/station-chats/{conversationUid}/name",
            methods = HttpMethod.PUT,
            summary = "Rename one of the station's chats",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationChatNameRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void rename(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(StationChatNameRequest.class);
        chats.rename(session.stationId(), session.user().accountId(), pathUuid(ctx, "conversationUid"), request.name());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/station-chats/{conversationUid}/guardians",
            methods = HttpMethod.PUT,
            summary = "Take the guardians of a group or tag chat's people in, or leave them out",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationChatGuardiansRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void guardians(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(StationChatGuardiansRequest.class);
        chats.includeGuardians(session.stationId(), pathUuid(ctx, "conversationUid"), request.includeGuardians());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/station-chats/{conversationUid}/close",
            methods = HttpMethod.POST,
            summary = "Close one of the station's chats; it stays readable and takes nothing new",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void close(Context ctx) {
        var session = StationSession.from(ctx);
        chats.close(session.stationId(), session.user().accountId(), pathUuid(ctx, "conversationUid"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/station-chats/{conversationUid}/reopen",
            methods = HttpMethod.POST,
            summary = "Open a closed chat of the station again",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reopen(Context ctx) {
        var session = StationSession.from(ctx);
        chats.reopen(session.stationId(), session.user().accountId(), pathUuid(ctx, "conversationUid"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * One of the station's chats as its managers see it.
     *
     * @param uid              the chat
     * @param name             its name
     * @param audience         whom it is for
     * @param groupId          the group of a group chat, or null for another audience and once it is gone
     * @param tagId            the tag of a tag chat, or null for another audience and once it is gone
     * @param audienceName     the name of its group or tag, or null
     * @param includeGuardians whether the guardians of its people are members too
     * @param members          how many take part in it right now
     * @param createdAt        when it was opened
     * @param lastActivityAt   when anything was last written in it
     * @param closedAt         when it was closed, or null while it is open
     */
    public record StationChatResponse(
            UUID uid,
            String name,
            ChatAudience audience,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            @Nullable String audienceName,
            boolean includeGuardians,
            int members,
            Instant createdAt,
            Instant lastActivityAt,
            @Nullable Instant closedAt) {
        static StationChatResponse of(StationChatSummary summary) {
            Conversation chat = summary.conversation();
            StationScope scope = Objects.requireNonNull(chat.station(), "a station chat has its scope");
            return new StationChatResponse(
                    chat.uid(),
                    Objects.requireNonNullElse(chat.name(), ""),
                    scope.audience(),
                    scope.groupId(),
                    scope.tagId(),
                    summary.audienceName(),
                    scope.includeGuardians(),
                    summary.members(),
                    chat.createdAt(),
                    chat.lastActivityAt(),
                    scope.closedAt());
        }
    }

    /**
     * @param name             the chat's name
     * @param audience         whom it is for: STATION, GROUP or TAG; refused when absent
     * @param groupId          the group of a GROUP chat, absent otherwise
     * @param tagId            the tag of a TAG chat, absent otherwise
     * @param includeGuardians whether the guardians of a group or tag's people are members too
     */
    public record OpenStationChatRequest(
            @Nullable String name,
            @Nullable ChatAudience audience,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            @Nullable Boolean includeGuardians) {}

    /**
     * @param name the chat's new name
     */
    public record StationChatNameRequest(@Nullable String name) {}

    /**
     * @param includeGuardians whether the guardians of the chat's people are members
     */
    public record StationChatGuardiansRequest(@Nullable Boolean includeGuardians) {}
}
