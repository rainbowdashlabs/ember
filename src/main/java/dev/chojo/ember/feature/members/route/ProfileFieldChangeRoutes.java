/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldChangeAcknowledgement;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository.MemberChangeSummary;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for tracking and acknowledging profile field changes made by managers,
 * including change history and per-member summaries.
 */
@Singleton
public class ProfileFieldChangeRoutes implements Routes {
    private final ProfileFieldService profileFieldService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final StationMemberService memberService;

    @Inject
    public ProfileFieldChangeRoutes(
            ProfileFieldService profileFieldService,
            MemberIdentityFactory memberIdentityFactory,
            StationMemberService memberService) {
        this.memberService = memberService;
        this.profileFieldService = profileFieldService;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/profile-changes/all",
                this::getAllChanges,
                StationPermission.MEMBER_CHANGES,
                StationPermission.MEMBER_GUARDIAN);
        routes.get(
                prefix + "/profile-changes/pending",
                this::getPendingSummary,
                StationPermission.MEMBER_CHANGES,
                StationPermission.MEMBER_GUARDIAN);
        routes.get(
                prefix + "/station-members/{memberId}/profile-changes",
                this::getChanges,
                StationPermission.MEMBER_CHANGES,
                StationPermission.MEMBER_GUARDIAN);
        routes.post(
                prefix + "/profile-changes/{changeId}/acknowledge",
                this::acknowledge,
                StationPermission.MEMBER_CHANGES,
                StationPermission.MEMBER_GUARDIAN);
        routes.post(
                prefix + "/station-members/{memberId}/profile-changes/acknowledge-all",
                this::acknowledgeAll,
                StationPermission.MEMBER_CHANGES,
                StationPermission.MEMBER_GUARDIAN);
    }

    /**
     * The members whose changes the caller may see.
     *
     * <p>Someone charged with reviewing changes sees the whole station. A guardian sees the members
     * they manage and nobody else: the changes carry the old and the new value of a profile field,
     * so a station-wide list would hand every parent the addresses and birth dates of everyone.
     *
     * @return the members the caller is limited to, or empty when they are not limited at all
     */
    private Optional<List<Integer>> visibleMembers(UserSession session) {
        if (session.hasPermission(StationPermission.MEMBER_CHANGES)) return Optional.empty();
        return Optional.of(memberService.findManaged(session.member().id()).stream()
                .map(StationMember::id)
                .toList());
    }

    /**
     * Refuses a caller who is limited to their managed members and asks about someone else.
     */
    private void assertVisible(UserSession session, int memberId) {
        var visible = visibleMembers(session);
        if (visible.isPresent() && !visible.get().contains(memberId)) {
            throw new ForbiddenResponse("You may only see the members you manage");
        }
    }

    @OpenApi(
            path = "/api/v1/profile-changes/all",
            methods = HttpMethod.GET,
            summary = "Get all profile field changes for the station with pagination",
            tags = {"Profile Field Changes"},
            queryParams = {
                @OpenApiParam(name = "offset", type = Integer.class),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200"))
    private void getAllChanges(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        var visible = visibleMembers(session);
        var result = visible.isPresent()
                ? profileFieldService.findChangesByMembers(visible.get(), limit, offset)
                : profileFieldService.findChangesByStation(session.stationId(), limit, offset);
        var enriched = result.changes().stream()
                .map(c -> new EnrichedProfileFieldChange(
                        c, memberIdentityFactory.local(session.stationId(), c.memberId())))
                .toList();
        ctx.json(new PagedChangesResponse(enriched, result.total(), offset, limit));
    }

    @OpenApi(
            path = "/api/v1/profile-changes/pending",
            methods = HttpMethod.GET,
            summary = "Get summary of members with unacknowledged profile field changes",
            tags = {"Profile Field Changes"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberChangeSummary[].class)))
    private void getPendingSummary(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var visible = visibleMembers(session);
        var summaries =
                profileFieldService
                        .findUnacknowledgedSummary(
                                session.stationId(), session.member().id())
                        .stream()
                        .filter(s -> visible.isEmpty() || visible.get().contains(s.memberId()))
                        .toList();
        var enriched = summaries.stream()
                .map(s -> new EnrichedMemberChangeSummary(
                        s.memberId(),
                        s.memberName(),
                        s.pendingCount(),
                        s.latestChange(),
                        memberIdentityFactory.local(session.stationId(), s.memberId())))
                .toList();
        ctx.json(enriched);
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/profile-changes",
            methods = HttpMethod.GET,
            summary = "Get profile field change history for a member",
            tags = {"Profile Field Changes"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileFieldChange[].class)))
    private void getChanges(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        assertVisible(session, memberId);
        var identity = memberIdentityFactory.local(session.stationId(), memberId);
        ctx.json(profileFieldService.findChanges(memberId).stream()
                .map(c -> new EnrichedProfileFieldChange(c, identity))
                .toList());
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
        int changeId = pathInt(ctx, "changeId");
        assertVisible(session, profileFieldService.findMemberOfChange(changeId).orElseThrow(NotFoundResponse::new));
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
        int memberId = pathInt(ctx, "memberId");
        assertVisible(session, memberId);
        var request = ctx.bodyAsClass(AcknowledgeRequest.class);
        ctx.json(profileFieldService.acknowledgeAll(memberId, session.member().id(), request.comment()));
    }

    public record EnrichedMemberChangeSummary(
            int memberId, String memberName, int pendingCount, Instant latestChange, MemberIdentity identity) {}

    public record AcknowledgeRequest(String comment) {}

    public record EnrichedProfileFieldChange(ProfileFieldChange change, MemberIdentity memberIdentity) {}

    public record PagedChangesResponse(List<EnrichedProfileFieldChange> changes, int total, int offset, int limit) {}
}
