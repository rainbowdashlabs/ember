/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.beacon.service.BeaconReportService;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.feature.system.service.ProblemReportScreenshotService;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProblemReportRoutes implements Routes {
    private final ProblemReportRepository repository;
    private final BeaconReportService beacon;
    private final UpdateCheckService updates;
    private final ProblemReportScreenshotService screenshots;

    @Inject
    public ProblemReportRoutes(
            ProblemReportRepository repository,
            BeaconReportService beacon,
            UpdateCheckService updates,
            ProblemReportScreenshotService screenshots) {
        this.repository = repository;
        this.beacon = beacon;
        this.updates = updates;
        this.screenshots = screenshots;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/problem-reports", this::create, StationPermission.LOGIN);
        routes.get(prefix + "/admin/problem-reports", this::list, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/problem-reports/{id}/acknowledge",
                this::acknowledge,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/problem-reports/acknowledge-all",
                this::acknowledgeAll,
                InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/problem-reports/{id}/screenshot", this::screenshot, InstancePermission.ADMINISTRATOR);
        routes.delete(prefix + "/admin/problem-reports/{id}", this::delete, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/problem-reports",
            methods = HttpMethod.POST,
            summary = "Submit a problem report",
            tags = {"Problem Reports"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateReportRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ProblemReport.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateReportRequest.class);
        if (request.message() == null || request.message().isBlank()) {
            throw new BadRequestResponse("message is required");
        }
        Integer memberId = session.member() != null ? session.member().id() : null;
        // Kept before the report is written, so a picture that cannot be stored fails the whole
        // report rather than leaving one that claims a picture nobody can fetch.
        var picture = screenshots.store(request.screenshot(), memberId);
        var report = repository.create(
                session.stationId(),
                memberId,
                session.account().fullName().trim(),
                request.message(),
                request.pageUrl(),
                request.userRoles(),
                request.recentRequests(),
                request.browserInfo(),
                request.screenSize(),
                picture.orElse(null));
        if (beacon.goesByItself(report)) forward(report, report.screenshotFileId(), false);
        ctx.status(HttpStatus.CREATED).json(report);
    }

    /**
     * Passes a report to a beacon, with the picture it is to go with.
     *
     * <p>The picture goes first and the report second, which is the service's business; what is
     * decided here is which picture, because whoever reviewed it may have covered more of it and may
     * have decided it should not go at all. A report and its picture go together or the picture does
     * not go: nothing adds one to a report that has already left.
     *
     * @param pictureFileId the picture to send with it, or null to send the report on its own
     * @param temporary     whether that picture was made for this delivery alone and is to be let go
     *                      of once it has gone, which is what a covered copy is
     */
    private void forward(ProblemReport report, Integer pictureFileId, boolean temporary) {
        byte[] bytes = null;
        String type = null;
        if (pictureFileId != null) {
            var picture = screenshots.read(pictureFileId);
            if (picture.isPresent()) {
                bytes = picture.get().data();
                type = picture.get().contentType();
            }
        }
        beacon.sendReportNow(report, updates.currentVersion(), bytes, type);
        repository.markForwarded(report.id());
        if (temporary) screenshots.forget(pictureFileId);
    }

    @OpenApi(
            path = "/api/v1/admin/problem-reports",
            methods = HttpMethod.GET,
            summary = "List all problem reports",
            tags = {"Problem Reports"},
            queryParams = @OpenApiParam(name = "includeAcknowledged", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProblemReport[].class)))
    private void list(Context ctx) {
        boolean includeAcknowledged = "true".equals(ctx.queryParam("includeAcknowledged"));
        ctx.json(repository.findAll(includeAcknowledged));
    }

    @OpenApi(
            path = "/api/v1/admin/problem-reports/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Acknowledge a problem report",
            tags = {"Problem Reports"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void acknowledge(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        repository.acknowledge(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/problem-reports/acknowledge-all",
            methods = HttpMethod.POST,
            summary = "Acknowledge all problem reports",
            tags = {"Problem Reports"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AcknowledgeAllResponse.class)))
    private void acknowledgeAll(Context ctx) {
        int count = repository.acknowledgeAll();
        ctx.json(new AcknowledgeAllResponse(count));
    }

    @OpenApi(
            path = "/api/v1/admin/problem-reports/{id}/screenshot",
            methods = HttpMethod.GET,
            summary = "The picture the report was written with",
            tags = {"Problem Reports"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = "image/png")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void screenshot(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var report = repository.findById(id).orElseThrow(NotFoundResponse::new);
        if (!report.hasScreenshot()) throw new NotFoundResponse();
        var picture = screenshots.read(report.screenshotFileId()).orElseThrow(NotFoundResponse::new);
        ctx.contentType(picture.contentType()).result(picture.data());
    }

    /**
     * /** Deletes the report and the picture with it, because the picture has nowhere else to belong. */
    @OpenApi(
            path = "/api/v1/admin/problem-reports/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a problem report",
            tags = {"Problem Reports"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var report = repository.findById(id).orElse(null);
        repository.delete(id);
        if (report != null) screenshots.forget(report.screenshotFileId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A report as the dialog sends it.
     *
     * @param screenshot the picture of the page as base64, already covered where the reporter covered
     *     it, or null where they attached none. Never taken without being asked for.
     */
    public record CreateReportRequest(
            String message,
            String pageUrl,
            String userRoles,
            String recentRequests,
            String browserInfo,
            String screenSize,
            String screenshot) {}

    public record AcknowledgeAllResponse(int acknowledged) {}
}
