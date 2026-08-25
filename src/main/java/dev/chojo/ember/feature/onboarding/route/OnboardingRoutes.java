/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.onboarding.entity.OnboardingLevel;
import dev.chojo.ember.feature.onboarding.entity.OnboardingStatus;
import dev.chojo.ember.feature.onboarding.entity.OnboardingTaskState;
import dev.chojo.ember.feature.onboarding.service.OnboardingService;
import io.javalin.http.Context;
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
public class OnboardingRoutes implements Routes {

    private final OnboardingService onboardingService;

    @Inject
    public OnboardingRoutes(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/onboarding/member", this::getMemberTasks, StationPermission.USER);
        routes.put(prefix + "/onboarding/member/{taskId}", this::markMemberTask, StationPermission.USER);
        routes.get(prefix + "/onboarding/station", this::getStationTasks, StationPermission.STATION_ADMINISTRATOR);
        routes.put(
                prefix + "/onboarding/station/{taskId}",
                this::markStationTask,
                StationPermission.STATION_ADMINISTRATOR);
        routes.get(prefix + "/onboarding/instance", this::getInstanceTasks, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/onboarding/instance/{taskId}", this::markInstanceTask, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/onboarding/member",
            methods = HttpMethod.GET,
            summary = "What this member still has to set up",
            tags = {"Onboarding"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OnboardingStatus.class)))
    private void getMemberTasks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(onboardingService.forMember(session.member(), session.userType()));
    }

    @OpenApi(
            path = "/api/v1/onboarding/member/{taskId}",
            methods = HttpMethod.PUT,
            summary = "Tick off, pass over or take up one of this member's tasks",
            description = "A task that reads its answer from the data cannot be ticked off by hand.",
            tags = {"Onboarding"},
            pathParams = @OpenApiParam(name = "taskId", required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MarkRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OnboardingStatus.class)))
    private void markMemberTask(Context ctx) {
        UserSession session = UserSession.from(ctx);
        mark(ctx, session, OnboardingLevel.MEMBER);
        ctx.json(onboardingService.forMember(session.member(), session.userType()));
    }

    @OpenApi(
            path = "/api/v1/onboarding/station",
            methods = HttpMethod.GET,
            summary = "What this station still has to set up",
            description = "Shared by everyone who manages the station: what one of them settles is settled for all.",
            tags = {"Onboarding"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OnboardingStatus.class)))
    private void getStationTasks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(onboardingService.forStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/onboarding/station/{taskId}",
            methods = HttpMethod.PUT,
            summary = "Tick off, pass over or take up one of the station's tasks",
            tags = {"Onboarding"},
            pathParams = @OpenApiParam(name = "taskId", required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MarkRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OnboardingStatus.class)))
    private void markStationTask(Context ctx) {
        UserSession session = UserSession.from(ctx);
        mark(ctx, session, OnboardingLevel.STATION);
        ctx.json(onboardingService.forStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/onboarding/instance",
            methods = HttpMethod.GET,
            summary = "What this instance still has to set up",
            description = "Shared by every administrator, apart from the first task, which is about the reader's "
                    + "own account.",
            tags = {"Onboarding"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OnboardingStatus.class)))
    private void getInstanceTasks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(onboardingService.forInstance(session.account()));
    }

    @OpenApi(
            path = "/api/v1/onboarding/instance/{taskId}",
            methods = HttpMethod.PUT,
            summary = "Tick off, pass over or take up one of the instance's tasks",
            tags = {"Onboarding"},
            pathParams = @OpenApiParam(name = "taskId", required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MarkRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OnboardingStatus.class)))
    private void markInstanceTask(Context ctx) {
        UserSession session = UserSession.from(ctx);
        mark(ctx, session, OnboardingLevel.INSTANCE);
        ctx.json(onboardingService.forInstance(session.account()));
    }

    private void mark(Context ctx, UserSession session, OnboardingLevel level) {
        var request = ctx.bodyAsClass(MarkRequest.class);
        int memberId = session.member() == null ? 0 : session.member().id();
        onboardingService.mark(level, ctx.pathParam("taskId"), request.state(), memberId, session.accountId());
    }

    /**
     * @param state DONE to tick a task off, SKIPPED to pass it over, OPEN to take it up again
     */
    public record MarkRequest(OnboardingTaskState state) {}
}
