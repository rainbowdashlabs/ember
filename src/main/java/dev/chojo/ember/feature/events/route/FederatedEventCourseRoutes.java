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
import dev.chojo.ember.feature.events.route.EventCourseRoutes.CourseDetail;
import dev.chojo.ember.feature.events.route.EventCourseRoutes.CourseSignUp;
import dev.chojo.ember.feature.events.route.EventCourseRoutes.CourseSignUpRequest;
import dev.chojo.ember.feature.events.service.EventCoursePartnerService.PartnerStanding;
import dev.chojo.ember.feature.events.service.PartnerCourseService;
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
 * A course a partner station shares with this one, as this station's members see it: the course
 * with the standing of the reader and everybody they look after, and their sign-ups. Every answer
 * comes from the station holding the course.
 */
@Singleton
public class FederatedEventCourseRoutes implements Routes {
    private static final String COURSE = "/federated/{stationuid}/events/courses/{courseId}";

    private final PartnerCourseService partnerCourses;
    private final StationMemberService members;
    private final GuardianPolicy guardianPolicy;
    private final MemberIdentityFactory identities;

    @Inject
    public FederatedEventCourseRoutes(
            PartnerCourseService partnerCourses,
            StationMemberService members,
            GuardianPolicy guardianPolicy,
            MemberIdentityFactory identities) {
        this.partnerCourses = partnerCourses;
        this.members = members;
        this.guardianPolicy = guardianPolicy;
        this.identities = identities;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + COURSE, this::detail, StationPermission.USER, StationModule.EVENTS);
        routes.post(prefix + COURSE + "/sign-up", this::signUp, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + COURSE + "/sign-up", this::changeParts, StationPermission.USER, StationModule.EVENTS);
        routes.delete(prefix + COURSE + "/sign-up", this::withdraw, StationPermission.USER, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/courses/{courseId}",
            methods = HttpMethod.GET,
            summary = "A course a partner station shares, with the standing of the reader's household",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "courseId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void detail(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var household = household(session);
        var remote = partnerCourses.course(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "courseId"), household);
        var standings = remote.standings().stream()
                .filter(standing -> household.containsKey(standing.memberUid()))
                .map(standing -> signUpOf(session, household.get(standing.memberUid()), standing))
                .toList();
        ctx.json(new CourseDetail(
                remote.course(),
                remote.sets(),
                remote.parts(),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                List.of(),
                standings));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/courses/{courseId}/sign-up",
            methods = HttpMethod.POST,
            summary = "Sign the reader, or somebody they look after, up for a partner station's course",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "courseId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = CourseSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void signUp(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CourseSignUpRequest.class);
        var member = target(session, request.memberId());
        var standing = partnerCourses.signUp(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "courseId"), member, partsOf(request));
        ctx.status(HttpStatus.CREATED).json(signUpOf(session, member.id(), standing));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/courses/{courseId}/sign-up",
            methods = HttpMethod.PUT,
            summary = "Change the parts chosen with a sign-up for a partner station's course",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "courseId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CourseSignUpRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CourseSignUp.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void changeParts(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CourseSignUpRequest.class);
        var member = target(session, request.memberId());
        var standing = partnerCourses.changeParts(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "courseId"),
                member.uid(),
                partsOf(request));
        ctx.json(signUpOf(session, member.id(), standing));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/courses/{courseId}/sign-up",
            methods = HttpMethod.DELETE,
            summary = "Withdraw a sign-up for a partner station's course",
            tags = {"Federation"},
            pathParams = {
                @OpenApiParam(name = "stationuid", required = true),
                @OpenApiParam(name = "courseId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "memberId", type = Integer.class),
            responses = @OpenApiResponse(status = "204"))
    private void withdraw(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var asked = ctx.queryParam("memberId");
        var member = target(session, asked == null ? null : Integer.valueOf(asked));
        partnerCourses.withdraw(session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "courseId"), member);
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
                .orElseThrow(EventRefusal.COURSE_MEMBER_NOT_HERE::raise);
    }

    private CourseSignUp signUpOf(StationSession session, int memberId, PartnerStanding standing) {
        return new CourseSignUp(
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

    private static Set<Integer> partsOf(CourseSignUpRequest request) {
        return Set.copyOf(Objects.requireNonNullElse(request.parts(), new ArrayList<Integer>()));
    }
}
