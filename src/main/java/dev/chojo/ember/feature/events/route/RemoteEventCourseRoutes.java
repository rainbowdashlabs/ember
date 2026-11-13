/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.EventCoursePartState;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.feature.events.route.EventCourseRoutes.CourseSummary;
import dev.chojo.ember.feature.events.service.EventCoursePartnerService.PartnerStanding;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What a station serves its partners about the courses it shares with them: a course with the
 * standing of the partner's members, and their sign-ups.
 *
 * <p>A partner that has never heard of these endpoints never calls them. Its members still see the
 * parts as shared appointments, and are told there that they sign up through the course.
 */
@Singleton
public class RemoteEventCourseRoutes implements Routes {

    /** A shared course with the standing of the members the partner asks about. */
    public static final FederationEndpoint GET_COURSE = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/courses/{id}",
            RemoteCourseQuery.class,
            RemoteCourseDetail.class);

    /** One of the partner's members signing up for a shared course. */
    public static final FederationEndpoint SIGN_UP = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/courses/{id}/sign-up",
            RemoteCourseSignUpRequest.class,
            PartnerStanding.class);

    /** One of the partner's members changing the parts they chose. */
    public static final FederationEndpoint CHANGE_PARTS = FederationEndpoint.put(
            FederationSurface.EVENT_SHARE,
            "/remote/events/courses/{id}/sign-up",
            RemoteCourseSignUpRequest.class,
            PartnerStanding.class);

    /** One of the partner's members withdrawing from a shared course. */
    public static final FederationEndpoint WITHDRAW = FederationEndpoint.delete(
            FederationSurface.EVENT_SHARE, "/remote/events/courses/{id}/sign-up", RemoteCourseMember.class, Void.class);

    /**
     * The station holding a course telling a partner that the course was called off, naming the
     * partner's members who held or waited for a place.
     */
    public static final FederationEndpoint COURSE_CANCELLED_WEBHOOK = FederationEndpoint.post(
                    FederationSurface.EVENT_SHARE,
                    "/remote/webhook/course-cancelled",
                    RemoteCourseCancelled.class,
                    Void.class)
            .keepingInStep();

    public static final List<FederationEndpoint> CONTRACT =
            List.of(GET_COURSE, SIGN_UP, CHANGE_PARTS, WITHDRAW, COURSE_CANCELLED_WEBHOOK);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteEventCourseRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(GET_COURSE)
                .serveCreated(SIGN_UP)
                .serve(CHANGE_PARTS)
                .serve(WITHDRAW)
                .serve(COURSE_CANCELLED_WEBHOOK));
    }

    /** @param memberUids the partner's members whose standing is asked about */
    public record RemoteCourseQuery(List<UUID> memberUids) {}

    /**
     * A shared course as a partner is shown it.
     *
     * @param course    the course
     * @param sets      its sets in order
     * @param parts     its parts in date order
     * @param standings where each member asked about stands
     */
    public record RemoteCourseDetail(
            CourseSummary course,
            List<EventCourseSet> sets,
            List<EventCoursePartState> parts,
            List<PartnerStanding> standings) {}

    /**
     * A partner's member signing up, or changing the parts they chose.
     *
     * @param memberUid the member, by the id their own station gave them
     * @param parts     the chosen parts
     */
    public record RemoteCourseSignUpRequest(UUID memberUid, List<Integer> parts) {}

    /** @param memberUid the member, by the id their own station gave them */
    public record RemoteCourseMember(UUID memberUid) {}

    /**
     * A shared course that was called off.
     *
     * @param courseId   the course at the station holding it
     * @param courseName its name
     * @param reason     why, as told to the people signed up, or null
     * @param memberUids the partner's members who held or waited for a place
     */
    public record RemoteCourseCancelled(
            int courseId, String courseName, @Nullable String reason, List<UUID> memberUids) {}
}
