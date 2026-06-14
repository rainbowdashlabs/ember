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
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import io.javalin.http.BadRequestResponse;
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
    private final ProblemReportRepository repository;

    @Inject
    public ProblemReportRoutes(ProblemReportRepository repository) {
        this.repository = repository;
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
        var report = repository.create(
                session.stationId(),
                session.member() != null ? session.member().id() : null,
                session.account().fullName().trim(),
                request.message(),
                request.pageUrl(),
                request.userRoles(),
                request.recentRequests(),
                request.browserInfo(),
                request.screenSize());
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
            path = "/api/v1/admin/problem-reports/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a problem report",
            tags = {"Problem Reports"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        repository.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record CreateReportRequest(
            String message,
            String pageUrl,
            String userRoles,
            String recentRequests,
            String browserInfo,
            String screenSize) {}

    public record AcknowledgeAllResponse(int acknowledged) {}
}
