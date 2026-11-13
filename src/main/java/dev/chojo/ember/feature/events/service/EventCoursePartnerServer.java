/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.route.EventCourseRoutes.CourseSummary;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseDetail;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseMember;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseQuery;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseSignUpRequest;
import dev.chojo.ember.feature.events.service.EventCoursePartnerService.PartnerStanding;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Serves partners the courses this station shares with them. Every request is held to the courses
 * shared with the partner it arrived on, so a partner cannot reach another course by its id. The one
 * exception is a member withdrawing who still holds a place: they can give it back after the share
 * ended.
 */
@Singleton
public class EventCoursePartnerServer implements FederationServer {
    private final EventCoursePartnerService partners;
    private final EventCourseService courses;
    private final EventCourseRegistrationService signUps;

    @Inject
    public EventCoursePartnerServer(
            EventCoursePartnerService partners, EventCourseService courses, EventCourseRegistrationService signUps) {
        this.partners = partners;
        this.courses = courses;
        this.signUps = signUps;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteCourseQuery, RemoteCourseDetail>serve(
                RemoteEventCourseRoutes.GET_COURSE,
                (partner, params, body) -> serveCourse(partner, params.integer("id"), body));
        endpoints.<RemoteCourseSignUpRequest, PartnerStanding>serve(
                RemoteEventCourseRoutes.SIGN_UP, (partner, params, body) -> {
                    int courseId = requireShared(partner, params.integer("id"));
                    return partners.register(
                            partner.servingStationId(), courseId, partner.partnerId(), body.memberUid(), partsOf(body));
                });
        endpoints.<RemoteCourseSignUpRequest, PartnerStanding>serve(
                RemoteEventCourseRoutes.CHANGE_PARTS, (partner, params, body) -> {
                    int courseId = requireShared(partner, params.integer("id"));
                    return partners.changeParts(
                            partner.servingStationId(), courseId, partner.partnerId(), body.memberUid(), partsOf(body));
                });
        endpoints.<RemoteCourseMember, Void>serve(RemoteEventCourseRoutes.WITHDRAW, (partner, params, body) -> {
            int courseId = params.integer("id");
            if (!partners.holdsPlace(courseId, partner.partnerId(), body.memberUid())) requireShared(partner, courseId);
            partners.withdraw(partner.servingStationId(), courseId, partner.partnerId(), body.memberUid());
            return null;
        });
    }

    private RemoteCourseDetail serveCourse(ServingPartner partner, int courseId, RemoteCourseQuery query) {
        requireShared(partner, courseId);
        var course = courses.requireCourse(partner.servingStationId(), courseId);
        var asked = Objects.requireNonNullElse(query.memberUids(), List.<UUID>of());
        return new RemoteCourseDetail(
                CourseSummary.of(course, courses.findParts(courseId).size(), signUps.holding(courseId)),
                courses.findSets(courseId),
                signUps.partStates(courseId),
                partners.standings(courseId, partner.partnerId(), asked));
    }

    private int requireShared(ServingPartner partner, int courseId) {
        if (!partners.sharedCourseIds(partner.partnerId(), partner.servingStationId())
                .contains(courseId)) {
            throw EventRefusal.COURSE_NOT_HERE.raise();
        }
        return courseId;
    }

    private static Set<Integer> partsOf(RemoteCourseSignUpRequest request) {
        return Set.copyOf(Objects.requireNonNullElse(request.parts(), List.of()));
    }
}
