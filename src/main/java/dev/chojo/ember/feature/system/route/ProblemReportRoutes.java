/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
        routes.post(prefix + "/problem-reports", this::create, Roles.LOGIN);
        routes.get(prefix + "/admin/problem-reports", this::list, Roles.ADMIN);
        routes.post(prefix + "/admin/problem-reports/{id}/acknowledge", this::acknowledge, Roles.ADMIN);
        routes.post(prefix + "/admin/problem-reports/acknowledge-all", this::acknowledgeAll, Roles.ADMIN);
        routes.delete(prefix + "/admin/problem-reports/{id}", this::delete, Roles.ADMIN);
    }

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

    private void list(Context ctx) {
        boolean includeAcknowledged = "true".equals(ctx.queryParam("includeAcknowledged"));
        ctx.json(repository.findAll(includeAcknowledged));
    }

    private void acknowledge(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        repository.acknowledge(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void acknowledgeAll(Context ctx) {
        int count = repository.acknowledgeAll();
        ctx.json(new AcknowledgeAllResponse(count));
    }

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
