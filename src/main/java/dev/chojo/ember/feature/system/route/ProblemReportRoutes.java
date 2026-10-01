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
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.service.ProblemReportService;
import dev.chojo.ember.feature.system.service.ProblemReportService.ReportRequest;
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

@Singleton
public class ProblemReportRoutes implements Routes {
    private final ProblemReportService reports;

    @Inject
    public ProblemReportRoutes(ProblemReportService reports) {
        this.reports = reports;
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReportRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ProblemReport.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer memberId = session.memberOpt().map(StationMember::id).orElse(null);
        var report = reports.submit(
                session.requireStationId(),
                memberId,
                NameParts.of(session.account()).called(),
                ctx.bodyAsClass(ReportRequest.class));
        ctx.status(HttpStatus.CREATED).json(report);
    }

    @OpenApi(
            path = "/api/v1/admin/problem-reports",
            methods = HttpMethod.GET,
            summary = "List all problem reports",
            tags = {"Problem Reports"},
            queryParams = @OpenApiParam(name = "includeAcknowledged", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProblemReport[].class)))
    private void list(Context ctx) {
        ctx.json(reports.list("true".equals(ctx.queryParam("includeAcknowledged"))));
    }

    @OpenApi(
            path = "/api/v1/admin/problem-reports/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Acknowledge a problem report",
            tags = {"Problem Reports"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void acknowledge(Context ctx) {
        reports.acknowledge(ctx.pathParamAsClass("id", Integer.class).get());
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
        ctx.json(new AcknowledgeAllResponse(reports.acknowledgeAll()));
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
        var picture = reports.picture(ctx.pathParamAsClass("id", Integer.class).get());
        ctx.contentType(picture.contentType()).result(picture.data());
    }

    /** Deletes the report and the picture with it. */
    @OpenApi(
            path = "/api/v1/admin/problem-reports/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a problem report",
            tags = {"Problem Reports"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        reports.delete(ctx.pathParamAsClass("id", Integer.class).get());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record AcknowledgeAllResponse(int acknowledged) {}
}
