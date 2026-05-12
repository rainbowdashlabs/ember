/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.entity.ProfileFieldChange;
import dev.chojo.ember.entity.ProfileFieldChangeAcknowledgement;
import dev.chojo.ember.repository.ProfileFieldChangeRepository.MemberChangeSummary;
import dev.chojo.ember.service.ProfileFieldService;
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
public class ProfileFieldChangeRoutes implements Routes {
    private final ProfileFieldService profileFieldService;

    @Inject
    public ProfileFieldChangeRoutes(ProfileFieldService profileFieldService) {
        this.profileFieldService = profileFieldService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/profile-changes/pending",
                this::getPendingSummary,
                Roles.MEMBER_MANAGEMENT,
                Roles.MEMBER_MANAGER);
        routes.get(
                prefix + "/station-members/{memberId}/profile-changes",
                this::getChanges,
                Roles.MEMBER_MANAGEMENT,
                Roles.MEMBER_MANAGER);
        routes.post(
                prefix + "/profile-changes/{changeId}/acknowledge",
                this::acknowledge,
                Roles.MEMBER_MANAGEMENT,
                Roles.MEMBER_MANAGER);
        routes.post(
                prefix + "/station-members/{memberId}/profile-changes/acknowledge-all",
                this::acknowledgeAll,
                Roles.MEMBER_MANAGEMENT,
                Roles.MEMBER_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/profile-changes/pending",
            methods = HttpMethod.GET,
            summary = "Get summary of members with unacknowledged profile field changes",
            tags = {"Profile Field Changes"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberChangeSummary[].class)))
    private void getPendingSummary(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(profileFieldService.findUnacknowledgedSummary(
                session.stationId(), session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/profile-changes",
            methods = HttpMethod.GET,
            summary = "Get profile field change history for a member",
            tags = {"Profile Field Changes"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileFieldChange[].class)))
    private void getChanges(Context ctx) {
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        ctx.json(profileFieldService.findChanges(memberId));
    }

    @OpenApi(
            path = "/api/v1/profile-changes/{changeId}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Acknowledge a profile field change with optional comment",
            tags = {"Profile Field Changes"},
            pathParams = @OpenApiParam(name = "changeId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AcknowledgeRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ProfileFieldChangeAcknowledgement.class)))
    private void acknowledge(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int changeId = ctx.pathParamAsClass("changeId", Integer.class).get();
        var request = ctx.bodyAsClass(AcknowledgeRequest.class);
        ctx.json(profileFieldService.acknowledge(changeId, session.member().id(), request.comment()));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/profile-changes/acknowledge-all",
            methods = HttpMethod.POST,
            summary = "Acknowledge all unacknowledged changes for a member",
            tags = {"Profile Field Changes"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AcknowledgeRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ProfileFieldChangeAcknowledgement[].class)))
    private void acknowledgeAll(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var request = ctx.bodyAsClass(AcknowledgeRequest.class);
        ctx.json(profileFieldService.acknowledgeAll(memberId, session.member().id(), request.comment()));
    }

    public record AcknowledgeRequest(String comment) {}
}
