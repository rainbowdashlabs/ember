/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.route.EventGroupRoutes.EventGroupDetail;
import dev.chojo.ember.feature.events.route.EventGroupRoutes.EventGroupSignUp;
import dev.chojo.ember.feature.events.route.EventGroupRoutes.EventGroupSignUpRequest;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerStanding;
import dev.chojo.ember.feature.events.service.PartnerEventGroupService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.StationModule;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * An event group a partner station shares with this one, as this station's members see it: the event group
 * with the standing of the reader and everybody they look after, and their sign-ups. Every answer
 * comes from the station holding the event group.
 */
@Singleton
public class FederatedEventGroupRoutes implements Routes {
    private static final String EVENT_GROUP = "/federated/{stationuid}/events/groups/{eventGroupId}";

    private final PartnerEventGroupService partnerEventGroups;
    private final StationMemberService members;
    private final GuardianPolicy guardianPolicy;
    private final MemberIdentityFactory identities;

    @Inject
    public FederatedEventGroupRoutes(
            PartnerEventGroupService partnerEventGroups,
            StationMemberService members,
            GuardianPolicy guardianPolicy,
            MemberIdentityFactory identities) {
        this.partnerEventGroups = partnerEventGroups;
        this.members = members;
        this.guardianPolicy = guardianPolicy;
        this.identities = identities;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + EVENT_GROUP, this::detail, StationPermission.USER, StationModule.EVENTS);
        routes.post(prefix + EVENT_GROUP + "/sign-up", this::signUp, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + EVENT_GROUP + "/sign-up", this::changeParts, StationPermission.USER, StationModule.EVENTS);
        routes.delete(prefix + EVENT_GROUP + "/sign-up", this::withdraw, StationPermission.USER, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}",
            methods = HttpMethod.GET,
            summary = "An event group a partner station shares, with the standing of the reader's household",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void detail(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var household = household(session);
        var remote = partnerEventGroups.eventGroup(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "eventGroupId"), household);
        var standings = remote.standings().stream()
                .filter(standing -> household.containsKey(standing.memberUid()))
                .map(standing -> signUpOf(session, household.get(standing.memberUid()), standing))
                .toList();
        ctx.json(new EventGroupDetail(
                remote.eventGroup(),
                remote.sets(),
                remote.parts(),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                List.of(),
                standings));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}/sign-up",
            methods = HttpMethod.POST,
            summary = "Sign the reader, or somebody they look after, up for a partner station's event group",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventGroupSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void signUp(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupSignUpRequest.class);
        var member = target(session, request.memberId());
        var standing = partnerEventGroups.signUp(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "eventGroupId"),
                member,
                partsOf(request));
        ctx.status(HttpStatus.CREATED).json(signUpOf(session, member.id(), standing));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}/sign-up",
            methods = HttpMethod.PUT,
            summary = "Change the parts chosen with a sign-up for a partner station's event group",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void changeParts(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupSignUpRequest.class);
        var member = target(session, request.memberId());
        var standing = partnerEventGroups.changeParts(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "eventGroupId"),
                member.uid(),
                partsOf(request));
        ctx.json(signUpOf(session, member.id(), standing));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/groups/{eventGroupId}/sign-up",
            methods = HttpMethod.DELETE,
            summary = "Withdraw a sign-up for a partner station's event group",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "memberId", type = Integer.class),
            responses = @OpenApiResponse(status = "204"))
    private void withdraw(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var asked = ctx.queryParam("memberId");
        var member = target(session, asked == null ? null : Integer.valueOf(asked));
        partnerEventGroups.withdraw(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "eventGroupId"), member);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /** The reader and everybody they look after, by the id partners know them by. */
    private Map<UUID, Integer> household(StationSession session) {
        var household = new LinkedHashMap<UUID, Integer>();
        household.put(session.member().uid(), session.member().id());
        for (var managed : members.findManaged(session.member().id())) {
            household.put(managed.uid(), managed.id());
        }
        return household;
    }

    private StationMember target(StationSession session, @Nullable Integer requested) {
        int memberId = requested == null ? session.member().id() : requested;
        if (memberId != session.member().id()
                && !guardianPolicy.mayActFor(session.user(), memberId)
                && !session.hasPermission(StationPermission.EVENT_MANAGER)) {
            throw EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER.raise();
        }
        return members.findById(memberId)
                .filter(member -> member.stationId() == session.stationId())
                .orElseThrow(EventRefusal.EVENT_GROUP_MEMBER_NOT_HERE::raise);
    }

    private EventGroupSignUp signUpOf(StationSession session, int memberId, PartnerStanding standing) {
        return new EventGroupSignUp(
                memberId,
                identities.local(session.stationId(), memberId),
                standing.status(),
                standing.registeredAt(),
                standing.parts(),
                standing.sets(),
                List.of(),
                false,
                true);
    }

    private static Set<Integer> partsOf(EventGroupSignUpRequest request) {
        return Set.copyOf(Objects.requireNonNullElse(request.parts(), new ArrayList<Integer>()));
    }
}
