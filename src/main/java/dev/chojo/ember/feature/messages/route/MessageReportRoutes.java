/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.messages.entity.MessageReport;
import dev.chojo.ember.feature.messages.entity.ReportedMessage;
import dev.chojo.ember.feature.messages.route.MessageWire.ConversationMessageResponse;
import dev.chojo.ember.feature.messages.route.MessageWire.MessagePersonResponse;
import dev.chojo.ember.feature.messages.service.MessageReportService;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Reporting a message of a station chat, and the list of reported messages for the people handling
 * the station's chats, where a report is answered by removing the message or dismissing it.
 */
@Singleton
public class MessageReportRoutes implements Routes {
    private final MessageReportService reports;

    @Inject
    public MessageReportRoutes(MessageReportService reports) {
        this.reports = reports;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(
                prefix + "/messages/conversations/{conversationUid}/messages/{messageId}/report",
                this::report,
                StationPermission.LOGIN);
        String base = prefix + "/messages/reports";
        routes.get(base, this::open, StationPermission.MESSAGE_CHAT_MANAGE);
        routes.post(base + "/{reportId}/remove", this::remove, StationPermission.MESSAGE_CHAT_MANAGE);
        routes.post(base + "/{reportId}/dismiss", this::dismiss, StationPermission.MESSAGE_CHAT_MANAGE);
    }

    @OpenApi(
            path = "/api/v1/messages/conversations/{conversationUid}/messages/{messageId}/report",
            methods = HttpMethod.POST,
            summary = "Report a message of a station chat to the people handling the station's chats",
            tags = {"Messages"},
            pathParams = {
                @OpenApiParam(name = "conversationUid", type = UUID.class, required = true),
                @OpenApiParam(name = "messageId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReportMessageRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("a station chat is reached through the reader's own membership of it, which MessageGuards checks")
    private void report(Context ctx) {
        var request = ctx.bodyAsClass(ReportMessageRequest.class);
        reports.report(
                UserSession.from(ctx).accountId(),
                pathUuid(ctx, "conversationUid"),
                pathInt(ctx, "messageId"),
                request.reason());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/reports",
            methods = HttpMethod.GET,
            summary = "The open reports of the station's chats, each with the message in its context",
            tags = {"Messages"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ReportedMessageResponse[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void open(Context ctx) {
        var session = StationSession.from(ctx);
        UUID readerUid = session.user().account().uid();
        ctx.json(reports.open(session.stationId(), session.user().accountId()).stream()
                .map(reported -> ReportedMessageResponse.of(reported, readerUid))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/messages/reports/{reportId}/remove",
            methods = HttpMethod.POST,
            summary = "Remove a reported message for the station, which handles all its reports",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "reportId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void remove(Context ctx) {
        var session = StationSession.from(ctx);
        reports.remove(session.stationId(), session.user().accountId(), pathInt(ctx, "reportId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/messages/reports/{reportId}/dismiss",
            methods = HttpMethod.POST,
            summary = "Dismiss the reports of a message, which stays",
            tags = {"Messages"},
            pathParams = @OpenApiParam(name = "reportId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void dismiss(Context ctx) {
        var session = StationSession.from(ctx);
        reports.dismiss(session.stationId(), session.user().accountId(), pathInt(ctx, "reportId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A reported message as the people handling the station's chats look at it.
     *
     * @param id              the report
     * @param conversationUid the chat the message is in
     * @param chatName        the chat's name
     * @param reporter        who reported it, or null once that account is gone
     * @param reason          why, or null
     * @param reportedText    the message's text as it read when it was reported
     * @param reportedAt      when it was reported
     * @param messageId       the message reported
     * @param context         the message with a few before and after it, oldest first
     */
    public record ReportedMessageResponse(
            int id,
            UUID conversationUid,
            String chatName,
            @Nullable MessagePersonResponse reporter,
            @Nullable String reason,
            String reportedText,
            Instant reportedAt,
            int messageId,
            List<ConversationMessageResponse> context) {
        static ReportedMessageResponse of(ReportedMessage reported, UUID readerUid) {
            MessageReport report = reported.report();
            return new ReportedMessageResponse(
                    report.id(),
                    report.conversation().uid(),
                    Objects.requireNonNullElse(report.conversation().name(), ""),
                    MessagePersonResponse.ofNullable(report.reporter()),
                    report.reason(),
                    report.content(),
                    report.createdAt(),
                    report.messageId(),
                    reported.context().stream()
                            .map(view -> ConversationMessageResponse.of(view, readerUid))
                            .toList());
        }
    }

    /**
     * @param reason why the message is reported, or absent
     */
    public record ReportMessageRequest(@Nullable String reason) {}
}
