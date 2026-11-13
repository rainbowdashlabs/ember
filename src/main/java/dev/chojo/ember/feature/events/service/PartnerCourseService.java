/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.PartnerCourseSignupRepository;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseCancelled;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseDetail;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseMember;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseQuery;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseSignUpRequest;
import dev.chojo.ember.feature.events.service.EventCoursePartnerService.PartnerStanding;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * This station's members and the courses partner stations share with it: reading a course and
 * signing up, changing parts and withdrawing, each asked of the station that holds the course.
 * Whether the partner lives on this instance or another is the transport's to know.
 *
 * <p>This station notes which of its members it signed up, and believes a partner's news about the
 * course only for those.
 */
@Singleton
public class PartnerCourseService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(PartnerCourseService.class);

    private final FederationTransport transport;
    private final FederationEntityResolver entityResolver;
    private final PartnerCourseSignupRepository signedUp;
    private final StationMemberRepository members;
    private final Notifier notifier;

    @Inject
    public PartnerCourseService(
            FederationTransport transport,
            FederationEntityResolver entityResolver,
            PartnerCourseSignupRepository signedUp,
            StationMemberRepository members,
            Notifier notifier) {
        this.transport = transport;
        this.entityResolver = entityResolver;
        this.signedUp = signedUp;
        this.members = members;
        this.notifier = notifier;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteCourseCancelled, Void>serve(
                RemoteEventCourseRoutes.COURSE_CANCELLED_WEBHOOK, (partner, params, body) -> {
                    cancelled(partner, body);
                    return null;
                });
    }

    /**
     * Tells this station's members a partner named that the partner's course was called off. Only
     * members this station signed up for that course are told.
     */
    private void cancelled(ServingPartner partner, RemoteCourseCancelled cancelled) {
        int stationId = partner.row().stationId();
        var told = cancelled.memberUids().stream()
                .map(uid -> members.findByUid(stationId, uid))
                .flatMap(Optional::stream)
                .map(StationMember::id)
                .filter(memberId -> signedUp.signedUp(memberId, partner.askingStationUid(), cancelled.courseId()))
                .toList();
        if (told.isEmpty()) return;
        notifier.notify(
                StationAudience.household(told),
                NotificationType.EVENT_CANCELLED,
                NotificationData.of(
                        new NotificationParams.EventCancelled(
                                cancelled.courseName(), cancelled.reason(), null, null, true),
                        NotificationLinks.federatedCourse(partner.askingStationUid(), cancelled.courseId())),
                Delivery.EVERY_TIME);
        log.info(
                "Partner {} called off course {}, {} member(s) told",
                partner.askingStationUid(),
                cancelled.courseId(),
                told.size());
    }

    /**
     * A partner's course with the standing of the given members of this station. A member the partner
     * holds a place for is noted as signed up from here, so a sign-up whose answer was lost on the
     * way still hears when the course is called off.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the course
     * @param courseId          the course
     * @param household         the members asked about, by the id partners know them by
     * @return the course as the partner shows it
     */
    public RemoteCourseDetail course(
            int stationId, UUID partnerStationUid, int courseId, Map<UUID, Integer> household) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var detail = answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventCourseRoutes.GET_COURSE.at(courseId),
                        new RemoteCourseQuery(List.copyOf(household.keySet())),
                        RemoteCourseDetail.class),
                EventRefusal.COURSE_NOT_HERE);
        for (var standing : detail.standings()) {
            var memberId = household.get(standing.memberUid());
            if (memberId != null && holds(standing)) signedUp.record(memberId, partnerStationUid, courseId);
        }
        return detail;
    }

    private static boolean holds(PartnerStanding standing) {
        return standing.status() == RegistrationStatus.PENDING || standing.status() == RegistrationStatus.ACCEPTED;
    }

    /**
     * Signs a member of this station up for a partner's course.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the course
     * @param courseId          the course
     * @param member            the member
     * @param parts             the chosen parts
     * @return where the member stands, as the partner recorded it
     */
    public PartnerStanding signUp(
            int stationId, UUID partnerStationUid, int courseId, StationMember member, Set<Integer> parts) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var standing = answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventCourseRoutes.SIGN_UP.at(courseId),
                        new RemoteCourseSignUpRequest(member.uid(), List.copyOf(parts)),
                        PartnerStanding.class),
                EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN);
        signedUp.record(member.id(), partnerStationUid, courseId);
        log.info("Station {} signed a member up for course {} at partner {}", stationId, courseId, partner.id());
        return standing;
    }

    /**
     * Changes the parts a member of this station chose at a partner's course.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the course
     * @param courseId          the course
     * @param memberUid         the member
     * @param parts             the parts chosen from now on
     * @return where the member stands, as the partner recorded it
     */
    public PartnerStanding changeParts(
            int stationId, UUID partnerStationUid, int courseId, UUID memberUid, Set<Integer> parts) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        return answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventCourseRoutes.CHANGE_PARTS.at(courseId),
                        new RemoteCourseSignUpRequest(memberUid, List.copyOf(parts)),
                        PartnerStanding.class),
                EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN);
    }

    /**
     * Withdraws a member of this station from a partner's course.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the course
     * @param courseId          the course
     * @param member            the member
     */
    public void withdraw(int stationId, UUID partnerStationUid, int courseId, StationMember member) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        answeredOr(
                () -> {
                    transport.deliver(
                            partner,
                            RemoteEventCourseRoutes.WITHDRAW.at(courseId),
                            new RemoteCourseMember(member.uid()));
                    return Boolean.TRUE;
                },
                EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN);
        signedUp.forget(member.id(), partnerStationUid, courseId);
        log.info("Station {} withdrew a member from course {} at partner {}", stationId, courseId, partner.id());
    }

    private static <T> T answeredOr(Supplier<T> call, Refusal unanswered) {
        try {
            return call.get();
        } catch (RefusalResponse e) {
            if (e.refusal() == FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER) throw unanswered.raise();
            throw e;
        }
    }
}
