/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import dev.chojo.ember.feature.generator.service.PaperSubmissionService;
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
 * Scans of signed paper copies of the documents an appointment asks for: handed in, and taken back while
 * they wait, by whoever may fetch the participant's copy, and read, confirmed or turned down by whoever
 * manages the registrations.
 */
@Singleton
public class PaperSubmissionRoutes implements Routes {
    private final PaperSubmissionService submissions;
    private final EventVisibility visibility;

    @Inject
    public PaperSubmissionRoutes(PaperSubmissionService submissions, EventVisibility visibility) {
        this.submissions = submissions;
        this.visibility = visibility;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(
                prefix + "/events/{id}/documents-to-bring/{templateId}/members/{memberId}/scan",
                this::submit,
                StationPermission.USER);
        routes.get(
                prefix + "/events/{id}/document-scans/{submissionId}/content",
                this::content,
                StationPermission.EVENT_REGISTRATION);
        routes.post(
                prefix + "/events/{id}/document-scans/{submissionId}/confirm",
                this::confirm,
                StationPermission.EVENT_REGISTRATION);
        routes.post(
                prefix + "/events/{id}/document-scans/{submissionId}/reject",
                this::reject,
                StationPermission.EVENT_REGISTRATION);
        routes.delete(prefix + "/events/{id}/document-scans/{submissionId}", this::withdraw, StationPermission.USER);
    }

    /**
     * Why a scan is turned down, which the participant is told.
     *
     * @param reason the reason
     */
    public record ScanRejectRequest(@Nullable String reason) {}

    @OpenApi(
            path = "/api/v1/events/{id}/documents-to-bring/{templateId}/members/{memberId}/scan",
            methods = HttpMethod.POST,
            summary = "Hand in the scan of a participant's signed paper copy of a document the appointment asks for",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "templateId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "date", required = true),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = PaperSubmission.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void submit(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        ctx.status(HttpStatus.CREATED)
                .json(submissions.submit(
                        session,
                        event,
                        AppointmentDocumentRoutes.date(ctx),
                        pathInt(ctx, "templateId"),
                        pathInt(ctx, "memberId"),
                        ctx.formParam("title"),
                        ctx.uploadedFile("file")));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/document-scans/{submissionId}/content",
            methods = HttpMethod.GET,
            summary = "The scan handed in, for whoever manages the registrations to check it",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "submissionId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void content(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        var scan = submissions.scan(session, event, pathInt(ctx, "submissionId"));
        FileResponse.send(ctx, scan.document().mimeType(), scan.document().fileName(), scan.data());
    }

    @OpenApi(
            path = "/api/v1/events/{id}/document-scans/{submissionId}/confirm",
            methods = HttpMethod.POST,
            summary = "Confirm a scan handed in as the participant's signed paper copy",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "submissionId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PaperSubmission.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void confirm(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        ctx.json(submissions.confirm(session, event, pathInt(ctx, "submissionId")));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/document-scans/{submissionId}/reject",
            methods = HttpMethod.POST,
            summary = "Turn a scan handed in down with a reason, which opens the document again",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "submissionId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ScanRejectRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PaperSubmission.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reject(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        String reason = ctx.bodyAsClass(ScanRejectRequest.class).reason();
        ctx.json(submissions.reject(session, event, pathInt(ctx, "submissionId"), reason));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/document-scans/{submissionId}",
            methods = HttpMethod.DELETE,
            summary = "Take back a scan that still waits, which removes it and opens the document again",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "submissionId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdraw(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        submissions.withdraw(session, event, pathInt(ctx, "submissionId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
