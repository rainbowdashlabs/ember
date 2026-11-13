/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.feature.events.entity.EventCourseSetDraft;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCoursePartnerRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.PartnerCourseSignupRepository;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A Jugendflamme course of station A shared with station B on the same instance: B's members sign
 * up through A's rules, and A decides and calls off as it does for its own.
 */
class EventCoursePartnerServiceTest extends RepositoryTestBase {
    private static Station holder;
    private static Station partner;
    private static StationMember mara;
    private static StationMember ben;
    private static StationMember lea;
    private static int partnerOfHolder;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventCourseRepository courseRepo = new EventCourseRepository();
    private final EventCoursePartnerRepository partnerRepo = new EventCoursePartnerRepository();
    private final EventFederationRepository federationRegistrations = new EventFederationRepository();
    private final PartnerCourseSignupRepository signedUp = new PartnerCourseSignupRepository();
    private EventCourseService courses;
    private EventCourseRegistrationService signUps;
    private EventCoursePartnerService partners;
    private PartnerCourseService partnerSide;
    private Notifier notifier;
    private EventCourse course;
    private int sat1;
    private int sun1;
    private int sat2;
    private int test;

    @BeforeAll
    static void setup() {
        holder = stationRepo.create("Course Holder Station");
        partner = stationRepo.create("Course Partner Station");
        var federationService = TestFederationServices.of(new FederationRepository(), stationRepo);
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                holder.id(), partner.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerOfHolder = new FederationRepository()
                .findPartnerByStationAndRemoteUid(holder.id(), partner.uid())
                .orElseThrow()
                .id();
        var account = accountRepo.create("mara@partner-course.test", "Mara", "Partner");
        accounts.add(account.id());
        mara = stationMemberRepo.create(partner.id(), account.id());
        var second = accountRepo.create("ben@partner-course.test", "Ben", "Partner");
        accounts.add(second.id());
        ben = stationMemberRepo.create(partner.id(), second.id());
        var local = accountRepo.create("lea@holder-course.test", "Lea", "Holder");
        accounts.add(local.id());
        lea = stationMemberRepo.create(holder.id(), local.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(holder.id());
        stationRepo.delete(partner.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        var federationRepo = new FederationRepository();
        var questions = new CourseQuestionService(new EventRegistrationFieldRepository(), memberEligibility);
        courses = new EventCourseService(courseRepo, eventRepo, restrictionService, occurrenceCalendar, questions);
        signUps = new EventCourseRegistrationService(
                courseRepo,
                new EventCourseRegistrationRepository(),
                eventRepo,
                eventRegistrationRepo,
                newEventServices(new DomainEventBus(Set.of())).registration(),
                occurrenceCalendar,
                mock(DomainEventBus.class),
                memberNameResolver,
                questions,
                partnerRepo);
        var httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        var transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        partners = new EventCoursePartnerService(
                courses,
                signUps,
                partnerRepo,
                federationRegistrations,
                occurrenceCalendar,
                mock(EventRequirementService.class),
                mock(FederatedRegistrantService.class),
                federationRepo,
                transport.transport());
        notifier = mock(Notifier.class);
        partnerSide = new PartnerCourseService(
                transport.transport(),
                new FederationEntityResolver(federationRepo),
                signedUp,
                stationMemberRepo,
                notifier);
        transport.serve(new EventCoursePartnerServer(partners, courses, signUps), partnerSide);
        course = jugendflamme(null);
    }

    private EventCourse jugendflamme(Integer limit) {
        var created = courses.create(
                holder.id(),
                new EventCourseDetails(
                        "Jugendflamme 1", null, limit, Instant.now().plus(Duration.ofDays(5)), false),
                List.of(new EventCourseSetDraft(null, "Übung", 2), new EventCourseSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var sets = courses.findSets(created.id()).stream()
                .collect(Collectors.toMap(EventCourseSet::name, EventCourseSet::id));
        sat1 = part(created, sets.get("Übung"), 20);
        sun1 = part(created, sets.get("Übung"), 21);
        sat2 = part(created, sets.get("Übung"), 27);
        test = part(created, sets.get("Prüfung"), 28);
        partners.share(holder.id(), created.id(), ShareScope.ALL_PARTNERS, List.of());
        return created;
    }

    private int part(EventCourse into, int setId, int daysAhead) {
        var zone = occurrenceCalendar.zoneOf(holder.id());
        var start =
                LocalDate.now().plusDays(daysAhead).atTime(9, 0).atZone(zone).toInstant();
        var event = eventRepo.create(
                holder.id(),
                "Tag " + daysAhead,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        courses.addPart(holder.id(), into.id(), event.id(), setId);
        return event.id();
    }

    private Set<Integer> heldParts() {
        var signUp = partnerRepo.find(course.id(), partnerOfHolder, mara.uid()).orElseThrow();
        return partnerRepo.findLinked(signUp.id()).stream()
                .map(EventFederationRegistration::eventId)
                .collect(Collectors.toSet());
    }

    @Test
    void sharingACourseSharesEveryPartAndTheOnesAddedLater() {
        var shared = new EventFederationRepository().findSharedEventIds(partnerOfHolder, holder.id());
        assertTrue(shared.containsAll(List.of(sat1, sun1, sat2, test)));

        var later = part(course, courses.findSets(course.id()).getFirst().id(), 30);

        assertTrue(new EventFederationRepository()
                .findSharedEventIds(partnerOfHolder, holder.id())
                .contains(later));
        assertEquals(
                List.of(course.id()),
                partners.sharedCourseIds(partnerOfHolder, holder.id()).stream()
                        .filter(id -> id == course.id())
                        .toList());
    }

    @Test
    void aPartnerMemberSignsUpThroughTheSameRules() {
        assertRefused(
                EventRefusal.COURSE_CHOICE_SHORT,
                () -> partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, test)));

        var standing = partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));

        assertEquals(RegistrationStatus.ACCEPTED, standing.status());
        assertEquals(Set.of(sat1, sun1, test), heldParts());
        assertTrue(signedUp.signedUp(mara.id(), holder.uid(), course.id()));
        assertEquals(1, signUps.holding(course.id()));
        var view = partnerSide.course(partner.id(), holder.uid(), course.id(), Map.of(mara.uid(), mara.id()));
        assertEquals(List.of(sat1, sun1, test), view.standings().getFirst().parts());
        assertEquals(4, view.parts().size());
    }

    @Test
    void aFullCourseRefusesAPartnerMemberAsWell() {
        course = jugendflamme(1);
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));

        assertRefused(
                EventRefusal.COURSE_FULL,
                () -> partnerSide.signUp(partner.id(), holder.uid(), course.id(), ben, Set.of(sat1, sun1, test)));
    }

    @Test
    void partsAreChangedAndTheSignUpWithdrawn() {
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));

        partnerSide.changeParts(partner.id(), holder.uid(), course.id(), mara.uid(), Set.of(sun1, sat2, test));
        assertEquals(Set.of(sun1, sat2, test), heldParts());

        partnerSide.withdraw(partner.id(), holder.uid(), course.id(), mara);
        assertEquals(Set.of(), heldParts());
        assertEquals(
                RegistrationStatus.WITHDRAWN,
                partnerRepo
                        .find(course.id(), partnerOfHolder, mara.uid())
                        .orElseThrow()
                        .status());
        assertTrue(federationRegistrations.findRegistrations(sat2, null).stream()
                .allMatch(registration -> registration.status() == RegistrationStatus.WITHDRAWN));
        assertFalse(signedUp.signedUp(mara.id(), holder.uid(), course.id()));
        assertEquals(0, signUps.holding(course.id()));
    }

    @Test
    void theHolderDecidesAPartnerSignUpWithEveryPart() {
        courses.update(
                holder.id(),
                course.id(),
                new EventCourseDetails(
                        "Jugendflamme 1", null, null, Instant.now().plus(Duration.ofDays(5)), true));
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));
        var signUp = partnerRepo.find(course.id(), partnerOfHolder, mara.uid()).orElseThrow();
        assertEquals(RegistrationStatus.PENDING, signUp.status());

        partners.decide(holder.id(), course.id(), signUp.id(), RegistrationStatus.ACCEPTED);

        assertTrue(partnerRepo.findLinked(signUp.id()).stream()
                .allMatch(registration -> registration.status() == RegistrationStatus.ACCEPTED));
        assertEquals(1, partners.signUps(holder.id(), course.id()).size());
    }

    @Test
    void callingTheCourseOffTellsThePartnerMembersItSignedUp() {
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));

        partners.tellCancelled(course.id(), course.name(), "Unwetter");

        verify(notifier).notify(any(), eq(NotificationType.EVENT_CANCELLED), any(), any());
    }

    @Test
    void aSharedCourseWhoseAudienceNamesAnybodyIsNotShared() {
        var restricted = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);
        courses.setAudiences(holder.id(), course.id(), RestrictionAudience.empty(), restricted);

        assertRefused(
                EventRefusal.RESTRICTED_EVENT_NOT_SHARED,
                () -> partners.share(holder.id(), course.id(), ShareScope.ALL_PARTNERS, List.of()));
    }

    @Test
    void aCourseNotSharedWithThePartnerIsNotThere() {
        partners.share(holder.id(), course.id(), ShareScope.SPECIFIC, List.of());

        assertRefused(
                EventRefusal.COURSE_NOT_HERE,
                () -> partnerSide.course(partner.id(), holder.uid(), course.id(), Map.of(mara.uid(), mara.id())));
        assertRefused(
                EventRefusal.COURSE_NOT_HERE,
                () -> partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test)));
    }

    @Test
    void aMemberHoldingAPlaceStillWithdrawsAfterTheShareEnded() {
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));
        partners.unshare(holder.id(), course.id());

        assertFalse(new EventFederationRepository()
                .findSharedEventIds(partnerOfHolder, holder.id())
                .contains(sat1));
        partnerSide.withdraw(partner.id(), holder.uid(), course.id(), mara);
        assertEquals(0, signUps.holding(course.id()));
    }

    @Test
    void theCancelNoticeReachesOnlyMembersThePartnerStationSignedUp() {
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));
        partnerRepo.signUp(course.id(), partnerOfHolder, ben.uid(), RegistrationStatus.ACCEPTED);

        partners.tellCancelled(course.id(), course.name(), null);

        verify(notifier)
                .notify(
                        eq(StationAudience.household(List.of(mara.id()))),
                        eq(NotificationType.EVENT_CANCELLED),
                        any(),
                        any());
    }

    @Test
    void aPartnerSignUpTakesThePlaceAMemberHereWanted() {
        course = jugendflamme(1);
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, test));

        assertRefused(
                EventRefusal.COURSE_FULL,
                () -> signUps.register(
                        holder.id(), course.id(), lea.id(), Set.of(sat1, sun1, test), Map.of(), null, false));
    }

    @Test
    void aPartLeavingTheCourseKeepsThePartnerPlaceAsOneOfItsOwn() {
        partnerSide.signUp(partner.id(), holder.uid(), course.id(), mara, Set.of(sat1, sun1, sat2, test));

        courses.removePart(holder.id(), course.id(), sat2);

        assertEquals(Set.of(sat1, sun1, test), heldParts());
        assertTrue(federationRegistrations.findRegistrations(sat2, null).stream()
                .anyMatch(registration -> registration.status() == RegistrationStatus.ACCEPTED));
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }
}
