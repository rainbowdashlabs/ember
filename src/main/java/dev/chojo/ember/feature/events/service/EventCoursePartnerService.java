/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventCoursePartnerRegistration;
import dev.chojo.ember.feature.events.entity.EventCourseShare;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCoursePartnerRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventCourseRoutes.RemoteCourseCancelled;
import dev.chojo.ember.feature.events.service.CourseRules.SetResult;
import dev.chojo.ember.feature.events.service.EventCourseRegistrationService.Standing;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Courses this station shares with partner stations, and the sign-ups partners' members send for
 * them.
 *
 * <p>Sharing a course shares every part with the same partners, parts added later included, so a
 * partner sees the appointments it signs up for. A partner's member signs up for the course as a
 * whole, through the same rules, limit, deadline and confirmation as a member of this station, and
 * every part they choose carries a partner registration tied to that sign-up. Partners answer no
 * course questions, as they answer none for an appointment.
 */
@Singleton
public class EventCoursePartnerService {
    private static final Logger log = LoggerFactory.getLogger(EventCoursePartnerService.class);

    private final EventCourseService courses;
    private final EventCourseRegistrationService gate;
    private final EventCoursePartnerRepository partners;
    private final EventFederationRepository federation;
    private final OccurrenceCalendar calendar;
    private final EventRequirementService requirements;
    private final FederatedRegistrantService registrants;
    private final FederationRepository federationPartners;
    private final FederationTransport transport;

    @Inject
    public EventCoursePartnerService(
            EventCourseService courses,
            EventCourseRegistrationService gate,
            EventCoursePartnerRepository partners,
            EventFederationRepository federation,
            OccurrenceCalendar calendar,
            EventRequirementService requirements,
            FederatedRegistrantService registrants,
            FederationRepository federationPartners,
            FederationTransport transport) {
        this.courses = courses;
        this.gate = gate;
        this.partners = partners;
        this.federation = federation;
        this.calendar = calendar;
        this.requirements = requirements;
        this.registrants = registrants;
        this.federationPartners = federationPartners;
        this.transport = transport;
    }

    /**
     * How a course is shared.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @return the share, empty where the course is not shared
     */
    public Optional<EventCourseShare> findShare(int stationId, int courseId) {
        courses.requireCourse(stationId, courseId);
        return partners.findShare(courseId);
    }

    /**
     * Shares a course with partners, and every part with it.
     *
     * <p>A course whose audiences name anybody is not shared: they name groups, tags and members of
     * this station, which mean nothing at a partner, so the course would stand open to everybody
     * there. Nor is one with a part whose documents partners could not sign alike.
     *
     * @param stationId  the station asking
     * @param courseId   the course
     * @param scope      every partner, or the ones named
     * @param partnerIds the partners named
     */
    public void share(int stationId, int courseId, ShareScope scope, List<Integer> partnerIds) {
        courses.requireCourse(stationId, courseId);
        var targets = scope == ShareScope.SPECIFIC ? List.copyOf(partnerIds) : List.<Integer>of();
        Transactions.run(() -> {
            gate.lockCourse(stationId, courseId);
            if (!courses.viewAudience(courseId).namesNobody()
                    || !courses.registrationAudience(courseId).namesNobody()) {
                throw EventRefusal.RESTRICTED_EVENT_NOT_SHARED.raise();
            }
            for (var part : courses.findParts(courseId)) {
                requirements.requireShareable(part.eventId());
                shareAppointment(part.eventId(), scope, targets);
            }
            partners.setShare(courseId, scope, targets);
        });
        log.info("Course {} shared with partners ({}, {} named)", courseId, scope, targets.size());
    }

    /**
     * Stops sharing a course and its parts. Partner sign-ups already made stay.
     *
     * @param stationId the station asking
     * @param courseId  the course
     */
    public void unshare(int stationId, int courseId) {
        courses.requireCourse(stationId, courseId);
        Transactions.run(() -> {
            gate.lockCourse(stationId, courseId);
            for (var part : courses.findParts(courseId)) {
                federation.removeShare(part.eventId());
            }
            partners.removeShare(courseId);
        });
        log.info("Course {} no longer shared with partners", courseId);
    }

    private void shareAppointment(int eventId, ShareScope scope, List<Integer> targets) {
        var share = federation.setShare(eventId, scope);
        federation.setShareTargets(share.id(), targets);
    }

    /**
     * The courses of this station shared with a partner.
     *
     * @param partnerId this station's record of the partner
     * @param stationId this station
     * @return the ids of the shared courses
     */
    public List<Integer> sharedCourseIds(int partnerId, int stationId) {
        return partners.findSharedCourseIds(partnerId, stationId);
    }

    /**
     * Whether a partner's member holds or waits for a place in a course. A member who does may still
     * withdraw after the course stopped being shared with their station, so no place stays taken that
     * nobody can give back.
     *
     * @param courseId       the course
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @return true for a pending or accepted sign-up
     */
    public boolean holdsPlace(int courseId, int partnerId, UUID remoteMemberId) {
        return partners.find(courseId, partnerId, remoteMemberId)
                .filter(EventCoursePartnerRegistration::active)
                .isPresent();
    }

    /**
     * Signs a partner's member up for a course with a choice of its parts.
     *
     * @param stationId      this station
     * @param courseId       the course
     * @param partnerId      the partner the member belongs to
     * @param remoteMemberId the member
     * @param partIds        the chosen parts
     * @return where the member stands now
     */
    public PartnerStanding register(
            int stationId, int courseId, int partnerId, UUID remoteMemberId, Set<Integer> partIds) {
        Transactions.run(() -> {
            var course = gate.lockOpenCourse(stationId, courseId, false);
            var existing = partners.find(courseId, partnerId, remoteMemberId);
            if (existing.filter(EventCoursePartnerRegistration::active).isPresent()) {
                throw EventRefusal.COURSE_ALREADY_SIGNED_UP.raise();
            }
            Integer limit = course.registrationLimit();
            if (limit != null && gate.holding(courseId) >= limit) {
                throw EventRefusal.COURSE_FULL.raise();
            }
            var parts = gate.lockParts(courseId);
            var standing = Standing.of(stationId, parts, calendar);
            EventCourseRegistrationService.requireChoosable(partIds, parts.keySet(), standing, Set.of(), false);
            gate.requireRulesMet(courseId, partIds, standing, false);
            var status = course.requiresConfirmation() ? RegistrationStatus.PENDING : RegistrationStatus.ACCEPTED;
            existing.ifPresent(earlier ->
                    partners.findLinked(earlier.id()).forEach(registration -> partners.unlink(registration.id())));
            var stored = partners.signUp(courseId, partnerId, remoteMemberId, status);
            for (int partId : EventCourseRegistrationService.ascending(partIds)) {
                place(parts.get(partId), stored, status);
            }
        });
        log.info("A member of partner {} signed up for course {}", partnerId, courseId);
        return standing(courseId, partnerId, remoteMemberId);
    }

    /**
     * Changes the parts a partner's member chose. A part already past cannot be given up.
     *
     * @param stationId      this station
     * @param courseId       the course
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @param partIds        the parts chosen from now on
     * @return where the member stands now
     */
    public PartnerStanding changeParts(
            int stationId, int courseId, int partnerId, UUID remoteMemberId, Set<Integer> partIds) {
        Transactions.run(() -> {
            gate.lockOpenCourse(stationId, courseId, false);
            var current = requireActive(courseId, partnerId, remoteMemberId);
            var parts = gate.lockParts(courseId);
            var standing = Standing.of(stationId, parts, calendar);
            var held = heldParts(current);
            EventCourseRegistrationService.requireChoosable(partIds, parts.keySet(), standing, held.keySet(), false);
            var dropped = new HashSet<>(held.keySet());
            dropped.removeAll(partIds);
            if (dropped.stream().anyMatch(standing::tookPlace)) throw EventRefusal.COURSE_PART_TOOK_PLACE.raise();
            gate.requireRulesMet(courseId, partIds, standing, false);
            for (int partId : EventCourseRegistrationService.ascending(partIds)) {
                if (!held.containsKey(partId)) place(parts.get(partId), current, current.status());
            }
            for (int partId : dropped) {
                var registration = held.get(partId);
                partners.restate(registration.id(), EventCourseRegistrationService.givenUp(registration.status()));
                partners.unlink(registration.id());
            }
        });
        return standing(courseId, partnerId, remoteMemberId);
    }

    /**
     * Takes a partner member's sign-up back with every part. Parts already past keep their record.
     *
     * @param stationId      this station
     * @param courseId       the course
     * @param partnerId      the partner
     * @param remoteMemberId the member
     */
    public void withdraw(int stationId, int courseId, int partnerId, UUID remoteMemberId) {
        Transactions.run(() -> {
            gate.lockCourse(stationId, courseId);
            var current = requireActive(courseId, partnerId, remoteMemberId);
            var parts = gate.lockParts(courseId);
            var standing = Standing.of(stationId, parts, calendar);
            partners.updateStatus(current.id(), EventCourseRegistrationService.givenUp(current.status()));
            for (var registration : partners.findLinked(current.id())) {
                if (!standing.tookPlace(registration.eventId())) {
                    partners.restate(registration.id(), EventCourseRegistrationService.givenUp(registration.status()));
                }
                partners.unlink(registration.id());
            }
        });
        log.info("A member of partner {} withdrew from course {}", partnerId, courseId);
    }

    /**
     * Accepts or turns down a partner member's sign-up, and every part chosen with it.
     *
     * @param stationId this station
     * @param courseId  the course
     * @param signUpId  the partner sign-up
     * @param decision  {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     */
    public void decide(int stationId, int courseId, int signUpId, RegistrationStatus decision) {
        if (decision != RegistrationStatus.ACCEPTED && decision != RegistrationStatus.DENIED) {
            throw EventRefusal.COURSE_DECISION_UNKNOWN.raise();
        }
        Transactions.run(() -> {
            var course = gate.lockCourse(stationId, courseId);
            if (decision == RegistrationStatus.ACCEPTED && course.cancelled()) {
                throw EventRefusal.COURSE_CANCELLED.raise();
            }
            var current = partners.findById(signUpId)
                    .filter(found -> found.courseId() == courseId)
                    .filter(found -> found.active() || found.status() == RegistrationStatus.DENIED)
                    .orElseThrow(EventRefusal.COURSE_NOT_SIGNED_UP::raise);
            gate.lockParts(courseId);
            partners.updateStatus(current.id(), decision);
            for (var registration : partners.findLinked(current.id())) {
                partners.restate(registration.id(), decision);
            }
        });
        log.info("Partner sign-up {} for course {} is now {}", signUpId, courseId, decision);
    }

    /**
     * Tells every partner whose members hold or wait for a place that the course was called off,
     * naming those members. A partner that cannot be reached misses the notice; the course page
     * still shows the course as called off.
     *
     * @param courseId   the course
     * @param courseName its name
     * @param reason     why, as told to the people signed up, or null
     */
    public void tellCancelled(int courseId, String courseName, @Nullable String reason) {
        var byPartner = partners.findByCourse(courseId).stream()
                .filter(EventCoursePartnerRegistration::active)
                .collect(Collectors.groupingBy(
                        EventCoursePartnerRegistration::partnerId,
                        Collectors.mapping(EventCoursePartnerRegistration::remoteMemberId, Collectors.toList())));
        byPartner.forEach((partnerId, members) -> federationPartners
                .findPartnerById(partnerId)
                .ifPresent(partner -> transport.notify(
                        partner,
                        RemoteEventCourseRoutes.COURSE_CANCELLED_WEBHOOK.at(),
                        new RemoteCourseCancelled(courseId, courseName, reason, members))));
        if (!byPartner.isEmpty())
            log.info("Told {} partner(s) that course {} was called off", byPartner.size(), courseId);
    }

    /**
     * Where the given members of one partner stand in a course.
     *
     * @param courseId        the course
     * @param partnerId       the partner
     * @param remoteMemberIds the members asked about
     * @return one standing per member, in the order asked
     */
    public List<PartnerStanding> standings(int courseId, int partnerId, List<UUID> remoteMemberIds) {
        return remoteMemberIds.stream()
                .map(member -> standing(courseId, partnerId, member))
                .toList();
    }

    /**
     * Every partner sign-up of a course that holds, waits for or was turned down for a place, with the
     * member as this station shows them.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @return the partner sign-ups, oldest first
     */
    public List<PartnerSignUp> signUps(int stationId, int courseId) {
        courses.requireCourse(stationId, courseId);
        return partners.findByCourse(courseId).stream()
                .filter(signUp -> signUp.active() || signUp.status() == RegistrationStatus.DENIED)
                .map(signUp -> new PartnerSignUp(
                        signUp.id(),
                        registrants.identify(signUp.partnerId(), signUp.remoteMemberId()),
                        standing(courseId, signUp)))
                .toList();
    }

    private PartnerStanding standing(int courseId, int partnerId, UUID remoteMemberId) {
        return partners.find(courseId, partnerId, remoteMemberId)
                .map(signUp -> standing(courseId, signUp))
                .orElseGet(() -> new PartnerStanding(remoteMemberId, null, null, List.of(), List.of()));
    }

    private PartnerStanding standing(int courseId, EventCoursePartnerRegistration signUp) {
        var counted = partners.findLinked(signUp.id()).stream()
                .filter(registration -> registration.status() == RegistrationStatus.PENDING
                        || registration.status() == RegistrationStatus.ACCEPTED
                        || (registration.status() == RegistrationStatus.DENIED
                                && signUp.status() == RegistrationStatus.DENIED))
                .map(EventFederationRegistration::eventId)
                .collect(Collectors.toSet());
        return new PartnerStanding(
                signUp.remoteMemberId(),
                signUp.status(),
                signUp.registeredAt(),
                counted.stream().sorted().toList(),
                gate.evaluate(courseId, counted));
    }

    private EventCoursePartnerRegistration requireActive(int courseId, int partnerId, UUID remoteMemberId) {
        return partners.find(courseId, partnerId, remoteMemberId)
                .filter(EventCoursePartnerRegistration::active)
                .orElseThrow(EventRefusal.COURSE_NOT_SIGNED_UP::raise);
    }

    private Map<Integer, EventFederationRegistration> heldParts(EventCoursePartnerRegistration signUp) {
        return partners.findLinked(signUp.id()).stream()
                .collect(Collectors.toMap(
                        EventFederationRegistration::eventId, Function.identity(), (first, second) -> first));
    }

    private void place(StationEvent part, EventCoursePartnerRegistration signUp, RegistrationStatus status) {
        var registration = federation.createRegistration(
                part.id(), signUp.partnerId(), signUp.remoteMemberId(), gate.dayOf(part), status);
        partners.link(registration.id(), signUp.id());
    }

    /**
     * Where a partner's member stands in a course, as the partner is told.
     *
     * @param memberUid    the member, by the id their own station gave them
     * @param status       the sign-up's status, or null where they are not signed up
     * @param registeredAt when they signed up, or null
     * @param parts        the parts that count for the rules, in id order
     * @param sets         how those parts meet every set
     */
    public record PartnerStanding(
            UUID memberUid,
            @Nullable RegistrationStatus status,
            @Nullable Instant registeredAt,
            List<Integer> parts,
            List<SetResult> sets) {}

    /**
     * A partner member's sign-up as this station's course page lists it.
     *
     * @param id       the partner sign-up, which decisions name
     * @param member   the member, or null where the partnership is gone
     * @param standing where they stand
     */
    public record PartnerSignUp(int id, @Nullable MemberIdentity member, PartnerStanding standing) {}
}
