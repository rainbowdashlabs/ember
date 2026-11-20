/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventGroupPartnerSignUpStatusChanged;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPartnerRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.handler.EventGroupPartnerSignUpStatusHandler;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventGroupSettingsRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.PartnerEventGroupSignupRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupSignUpStatus;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A Jugendflamme event group of station A shared with station B on the same instance: B's members sign
 * up through A's rules, and A decides and calls off as it does for its own.
 */
class EventGroupPartnerServiceTest extends RepositoryTestBase {
    private static Station holder;
    private static Station partner;
    private static StationMember mara;
    private static StationMember ben;
    private static StationMember lea;
    private static int partnerOfHolder;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    private final EventGroupPartnerRepository partnerRepo = new EventGroupPartnerRepository();
    private final EventFederationRepository federationRegistrations = new EventFederationRepository();
    private final PartnerEventGroupSignupRepository signedUp = new PartnerEventGroupSignupRepository();
    private EventGroupService eventGroups;
    private EventGroupRegistrationService signUps;
    private EventGroupPartnerService partners;
    private PartnerEventGroupService partnerSide;
    private Notifier notifier;
    private DomainEventBus decisions;
    private FederationTestTransport transport;
    private EventGroup eventGroup;
    private int sat1;
    private int sun1;
    private int sat2;
    private int test;

    @BeforeAll
    static void setup() {
        holder = stationRepo.create("Event group Holder Station");
        partner = stationRepo.create("Event group Partner Station");
        var federationService = TestFederationServices.of(new FederationRepository(), stationRepo);
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                holder.id(), partner.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerOfHolder = new FederationRepository()
                .findPartnerByStationAndRemoteUid(holder.id(), partner.uid())
                .orElseThrow()
                .id();
        var account = accountRepo.create("mara@partner-group.test", "Mara", "Partner");
        accounts.add(account.id());
        mara = stationMemberRepo.create(partner.id(), account.id());
        var second = accountRepo.create("ben@partner-group.test", "Ben", "Partner");
        accounts.add(second.id());
        ben = stationMemberRepo.create(partner.id(), second.id());
        var local = accountRepo.create("lea@holder-group.test", "Lea", "Holder");
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
        var questions = new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility);
        eventGroups = newEventGroupService();
        signUps = new EventGroupRegistrationService(
                eventGroupRepo,
                new EventGroupRegistrationRepository(),
                eventRegistrationRepo,
                newEventServices(new DomainEventBus(Set.of())).registration(),
                mock(DomainEventBus.class),
                memberNameResolver,
                questions,
                newEventGroupSignUpRules(),
                newEventGroupQueue(
                        newEventServices(new DomainEventBus(Set.of())).registration(), mock(DomainEventBus.class)),
                new RegistrationQueueRepository());
        var httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        decisions = spy(new DomainEventBus(Set.of(new EventGroupPartnerSignUpStatusHandler(
                partnerRepo, eventGroupRepo, federationRepo, transport::transport))));
        partners = new EventGroupPartnerService(
                eventGroups,
                newEventGroupSignUpRules(),
                partnerRepo,
                federationRegistrations,
                mock(EventRequirementService.class),
                mock(FederatedRegistrantService.class),
                federationRepo,
                transport.transport(),
                newEventGroupQueue(
                        newEventServices(new DomainEventBus(Set.of())).registration(), decisions),
                newPartnerPlacesService(),
                decisions);
        notifier = mock(Notifier.class);
        partnerSide = new PartnerEventGroupService(
                transport.transport(),
                new FederationEntityResolver(federationRepo),
                signedUp,
                stationMemberRepo,
                notifier,
                new PartnerStatusNotices(notifier, memberNameResolver));
        transport.serve(new EventGroupPartnerServer(partners, eventGroups, signUps), partnerSide);
        eventGroup = jugendflamme(null);
    }

    private EventGroup jugendflamme(Integer limit) {
        var created = eventGroups.create(
                holder.id(),
                new EventGroupDetails(
                        "Jugendflamme 1",
                        null,
                        limit,
                        RegistrationDeadline.fixed(Instant.now().plus(Duration.ofDays(5))),
                        false,
                        null),
                List.of(new EventGroupSetDraft(null, "Übung", 2), new EventGroupSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        var sets = eventGroups.findSets(created.id()).stream()
                .collect(Collectors.toMap(EventGroupSet::name, EventGroupSet::id));
        sat1 = part(created, sets.get("Übung"), 20).id();
        sun1 = part(created, sets.get("Übung"), 21).id();
        sat2 = part(created, sets.get("Übung"), 27).id();
        test = part(created, sets.get("Prüfung"), 28).id();
        partners.share(holder.id(), created.id(), ShareScope.ALL_PARTNERS, List.of());
        return created;
    }

    private Set<Integer> heldParts() {
        var signUp =
                partnerRepo.find(eventGroup.id(), partnerOfHolder, mara.uid()).orElseThrow();
        return partnerRepo.findLinked(signUp.id()).stream()
                .map(EventFederationRegistration::eventId)
                .collect(Collectors.toSet());
    }

    @Test
    void sharingAnEventGroupSharesEveryPartAndTheOnesAddedLater() {
        var shared = new EventFederationRepository().findSharedEventIds(partnerOfHolder, holder.id());
        assertTrue(shared.containsAll(List.of(sat1, sun1, sat2, test)));

        var later = part(
                        eventGroup,
                        eventGroups.findSets(eventGroup.id()).getFirst().id(),
                        30)
                .id();

        assertTrue(new EventFederationRepository()
                .findSharedEventIds(partnerOfHolder, holder.id())
                .contains(later));
        assertEquals(eventGroup.id(), partners.requireSharedWith(servedByHolder(), eventGroup.id()));
    }

    @Test
    void anEventGroupSharedWithAllPartnersReachesThePartner() {
        assertTrue(partnerRepo.isSharedWith(eventGroup.id(), partnerOfHolder, holder.id()));
        assertEquals(eventGroup.id(), partners.requireSharedWith(servedByHolder(), eventGroup.id()));
    }

    @Test
    void anEventGroupSharedWithTheNamedPartnerReachesIt() {
        partners.share(holder.id(), eventGroup.id(), ShareScope.SPECIFIC, List.of(partnerOfHolder));

        assertEquals(eventGroup.id(), partners.requireSharedWith(servedByHolder(), eventGroup.id()));
    }

    @Test
    void anEventGroupSharedWithOtherPartnersOrNobodyDoesNotReachThePartner() {
        partners.share(holder.id(), eventGroup.id(), ShareScope.SPECIFIC, List.of());
        assertFalse(partnerRepo.isSharedWith(eventGroup.id(), partnerOfHolder, holder.id()));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE, () -> partners.requireSharedWith(servedByHolder(), eventGroup.id()));

        partners.unshare(holder.id(), eventGroup.id());
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE, () -> partners.requireSharedWith(servedByHolder(), eventGroup.id()));
    }

    @Test
    void anEventGroupOfAnotherStationDoesNotReachThePartner() {
        var partnerRow = new FederationRepository()
                .findPartnerByStationAndRemoteUid(partner.id(), holder.uid())
                .orElseThrow();

        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> partners.requireSharedWith(new ServingPartner(partnerRow, holder.uid()), eventGroup.id()));
    }

    @Test
    void aSharedEventGroupWhoseViewIsRestrictedLaterDoesNotReachThePartner() {
        var restricted = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);
        eventGroups.setAudiences(holder.id(), eventGroup.id(), RestrictionAudience.empty(), restricted);

        assertFalse(partnerRepo.isSharedWith(eventGroup.id(), partnerOfHolder, holder.id()));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE, () -> partners.requireSharedWith(servedByHolder(), eventGroup.id()));
    }

    private static ServingPartner servedByHolder() {
        var row = new FederationRepository()
                .findPartnerByStationAndRemoteUid(holder.id(), partner.uid())
                .orElseThrow();
        return new ServingPartner(row, partner.uid());
    }

    @Test
    void aPartnerMemberChoosesNoMoreThanASetAllows() {
        var sets = eventGroups.findSets(eventGroup.id());
        eventGroups.replaceSets(
                holder.id(),
                eventGroup.id(),
                sets.stream()
                        .map(set -> new EventGroupSetDraft(
                                set.id(), set.name(), set.name().equals("Übung") ? 1 : set.minimum(), 1))
                        .toList());

        assertRefused(
                EventRefusal.EVENT_GROUP_SET_TOO_MANY,
                () -> partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test)));
    }

    @Test
    void aPartnerMemberSignsUpThroughTheSameRules() {
        assertRefused(
                EventRefusal.EVENT_GROUP_CHOICE_SHORT,
                () -> partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, test)));

        var standing = partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        assertEquals(RegistrationStatus.ACCEPTED, standing.status());
        assertEquals(Set.of(sat1, sun1, test), heldParts());
        assertTrue(signedUp.signedUp(mara.id(), holder.uid(), eventGroup.id()));
        assertEquals(1, signUps.holding(eventGroup.id()));
        var view = partnerSide.eventGroup(partner.id(), holder.uid(), eventGroup.id(), Map.of(mara.uid(), mara.id()));
        assertEquals(List.of(sat1, sun1, test), view.standings().getFirst().parts());
        assertEquals(4, view.parts().size());
    }

    @Test
    void aFullEventGroupPutsAPartnerMemberOnTheWaitingListAsWell() {
        eventGroup = jugendflamme(1);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        var waiting = partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));

        assertEquals(RegistrationStatus.QUEUED, waiting.status());
    }

    @Test
    void partsAreChangedAndTheSignUpWithdrawn() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        partnerSide.changeParts(partner.id(), holder.uid(), eventGroup.id(), mara.uid(), Set.of(sun1, sat2, test));
        assertEquals(Set.of(sun1, sat2, test), heldParts());

        partnerSide.withdraw(partner.id(), holder.uid(), eventGroup.id(), mara);
        assertEquals(Set.of(), heldParts());
        assertEquals(
                RegistrationStatus.WITHDRAWN,
                partnerRepo
                        .find(eventGroup.id(), partnerOfHolder, mara.uid())
                        .orElseThrow()
                        .status());
        assertTrue(federationRegistrations.findRegistrations(sat2, null).stream()
                .allMatch(registration -> registration.status() == RegistrationStatus.WITHDRAWN));
        assertFalse(signedUp.signedUp(mara.id(), holder.uid(), eventGroup.id()));
        assertEquals(0, signUps.holding(eventGroup.id()));
    }

    @Test
    void theHolderDecidesAPartnerSignUpWithEveryPart() {
        eventGroups.update(
                holder.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Jugendflamme 1",
                        null,
                        null,
                        RegistrationDeadline.fixed(Instant.now().plus(Duration.ofDays(5))),
                        true,
                        null));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        var signUp =
                partnerRepo.find(eventGroup.id(), partnerOfHolder, mara.uid()).orElseThrow();
        assertEquals(RegistrationStatus.PENDING, signUp.status());

        partners.decide(holder.id(), eventGroup.id(), signUp.id(), RegistrationStatus.ACCEPTED);

        assertTrue(partnerRepo.findLinked(signUp.id()).stream()
                .allMatch(registration -> registration.status() == RegistrationStatus.ACCEPTED));
        assertEquals(1, partners.signUps(holder.id(), eventGroup.id()).size());
    }

    @Test
    void callingTheEventGroupOffTellsThePartnerMembersItSignedUp() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        partners.tellCancelled(eventGroup.id(), eventGroup.details().name(), "Unwetter");

        verify(notifier).notify(any(), eq(NotificationType.EVENT_CANCELLED), any(), any());
    }

    @Test
    void aSharedEventGroupWhoseAudienceNamesAnybodyIsNotShared() {
        var restricted = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);
        eventGroups.setAudiences(holder.id(), eventGroup.id(), RestrictionAudience.empty(), restricted);

        assertRefused(
                EventRefusal.RESTRICTED_EVENT_NOT_SHARED,
                () -> partners.share(holder.id(), eventGroup.id(), ShareScope.ALL_PARTNERS, List.of()));
    }

    @Test
    void theShareReadsBackWithItsNamedPartnersUntilItEnds() {
        partners.share(holder.id(), eventGroup.id(), ShareScope.SPECIFIC, List.of(partnerOfHolder));

        var share = partners.findShare(holder.id(), eventGroup.id()).orElseThrow();
        assertEquals(ShareScope.SPECIFIC, share.scope());
        assertEquals(List.of(partnerOfHolder), share.partnerIds());

        partners.unshare(holder.id(), eventGroup.id());
        assertTrue(partners.findShare(holder.id(), eventGroup.id()).isEmpty());
    }

    @Test
    void anEventGroupNotSharedWithThePartnerIsNotThere() {
        partners.share(holder.id(), eventGroup.id(), ShareScope.SPECIFIC, List.of());

        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> partnerSide.eventGroup(
                        partner.id(), holder.uid(), eventGroup.id(), Map.of(mara.uid(), mara.id())));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test)));
    }

    @Test
    void aMemberHoldingAPlaceStillWithdrawsAfterTheShareEnded() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partners.unshare(holder.id(), eventGroup.id());

        assertFalse(new EventFederationRepository()
                .findSharedEventIds(partnerOfHolder, holder.id())
                .contains(sat1));
        partnerSide.withdraw(partner.id(), holder.uid(), eventGroup.id(), mara);
        assertEquals(0, signUps.holding(eventGroup.id()));
    }

    @Test
    void theCancelNoticeReachesOnlyMembersThePartnerStationSignedUp() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerRepo.signUp(eventGroup.id(), partnerOfHolder, ben.uid(), RegistrationStatus.ACCEPTED);

        partners.tellCancelled(eventGroup.id(), eventGroup.details().name(), null);

        verify(notifier)
                .notify(
                        eq(StationAudience.household(List.of(mara.id()))),
                        eq(NotificationType.EVENT_CANCELLED),
                        any(),
                        any());
    }

    @Test
    void aPartnerSignUpTakesThePlaceAMemberHereWanted() {
        eventGroup = jugendflamme(1);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        var waiting = signUps.register(
                holder.id(), eventGroup.id(), lea.id(), Set.of(sat1, sun1, test), Map.of(), null, false);

        assertEquals(RegistrationStatus.QUEUED, waiting.status());
    }

    @Test
    void aPartLeavingTheEventGroupKeepsThePartnerPlaceAsOneOfItsOwn() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, sat2, test));

        eventGroups.removePart(holder.id(), eventGroup.id(), sat2);

        assertEquals(Set.of(sat1, sun1, test), heldParts());
        assertTrue(federationRegistrations.findRegistrations(sat2, null).stream()
                .anyMatch(registration -> registration.status() == RegistrationStatus.ACCEPTED));
    }

    @Test
    void aPartnerSeesOnlyTheGroupFieldsMeantForOutsideReaders() {
        eventGroups.save(
                holder.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        eventGroup.details().name(),
                        null,
                        null,
                        eventGroup.details().registrationDeadline(),
                        false,
                        null),
                null,
                null,
                null,
                new EventGroupContent(
                        null,
                        List.of(
                                new EventFieldDraft(
                                        null,
                                        "Ort",
                                        FieldType.LOCATION,
                                        EventQuestionSettings.empty(),
                                        "Gerätehaus",
                                        true,
                                        null,
                                        true),
                                new EventFieldDraft(
                                        null,
                                        "Notfallnummer",
                                        FieldType.TEXT,
                                        EventQuestionSettings.empty(),
                                        "0171",
                                        false,
                                        null,
                                        false))));

        var view = partnerSide.eventGroup(partner.id(), holder.uid(), eventGroup.id(), Map.of(mara.uid(), mara.id()));

        assertEquals(
                List.of("Ort"),
                view.fields().stream().map(AppointmentField::name).toList());
    }

    @Test
    void aPartnerMemberHasToChooseTheRequiredParts() {
        eventGroups.setRequired(holder.id(), eventGroup.id(), sat1, true);

        assertRefused(
                EventRefusal.EVENT_GROUP_CHOICE_SHORT,
                () -> partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sun1, sat2, test)));

        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        var view = partnerSide.eventGroup(partner.id(), holder.uid(), eventGroup.id(), Map.of(mara.uid(), mara.id()));
        assertTrue(view.parts().stream().anyMatch(part -> part.eventId() == sat1 && part.required()));
    }

    private EventGroupPartnerRegistration partnerSignUpOf(StationMember member) {
        return partnerRepo.find(eventGroup.id(), partnerOfHolder, member.uid()).orElseThrow();
    }

    @Test
    void aPartnerMemberWaitingMovesUpWhenAPlaceFallsFree() {
        eventGroup = jugendflamme(1);
        signUps.register(holder.id(), eventGroup.id(), lea.id(), Set.of(sat1, sun1, test), Map.of(), null, false);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        assertEquals(RegistrationStatus.QUEUED, partnerSignUpOf(mara).status());
        assertEquals(
                1,
                partners.signUps(holder.id(), eventGroup.id()).stream()
                        .filter(signUp -> signUp.standing().status() == RegistrationStatus.QUEUED)
                        .findFirst()
                        .orElseThrow()
                        .standing()
                        .queuePosition());

        signUps.withdraw(holder.id(), eventGroup.id(), lea.id());

        assertEquals(RegistrationStatus.ACCEPTED, partnerSignUpOf(mara).status());
        assertEquals(Set.of(sat1, sun1, test), heldParts());
    }

    @Test
    void whoeverDecidesPutsAPartnerMemberOnTheWaitingList() {
        eventGroups.update(
                holder.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Jugendflamme 1",
                        null,
                        1,
                        RegistrationDeadline.fixed(Instant.now().plus(Duration.ofDays(5))),
                        true,
                        null));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        int signUpId = partnerSignUpOf(mara).id();

        partners.moveToQueue(holder.id(), eventGroup.id(), List.of(signUpId));
        partners.moveInQueue(holder.id(), eventGroup.id(), signUpId, 1);

        assertEquals(RegistrationStatus.QUEUED, partnerSignUpOf(mara).status());
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_PENDING_FOR_QUEUE,
                () -> partners.moveToQueue(holder.id(), eventGroup.id(), List.of(signUpId)));
    }

    @Test
    void aPartnerMemberIsNotPutOnTheListOfAnEventGroupWithoutLimits() {
        eventGroups.update(
                holder.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Jugendflamme 1",
                        null,
                        null,
                        RegistrationDeadline.fixed(Instant.now().plus(Duration.ofDays(5))),
                        true,
                        null));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        int signUpId = partnerSignUpOf(mara).id();

        assertRefused(
                EventRefusal.QUEUE_WITHOUT_LIMIT,
                () -> partners.moveToQueue(holder.id(), eventGroup.id(), List.of(signUpId)));
    }

    @Test
    void partnerMembersOnTheListAreReordered() {
        eventGroups.update(
                holder.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Jugendflamme 1",
                        null,
                        1,
                        RegistrationDeadline.fixed(Instant.now().plus(Duration.ofDays(5))),
                        true,
                        null));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));
        int first = partnerSignUpOf(mara).id();
        int second = partnerSignUpOf(ben).id();
        partners.moveToQueue(holder.id(), eventGroup.id(), List.of(first, second));

        partners.moveInQueue(holder.id(), eventGroup.id(), second, 1);

        var ranks = partners.signUps(holder.id(), eventGroup.id()).stream()
                .collect(Collectors.toMap(EventGroupPartnerService.PartnerSignUp::id, signUp -> signUp.standing()
                        .queuePosition()));
        assertEquals(1, ranks.get(second));
        assertEquals(2, ranks.get(first));
    }

    @Test
    void turningDownAnAcceptedPartnerMemberMovesTheNextUpAndAcceptingBeyondTheLimitHasToBeSaid() {
        eventGroup = jugendflamme(1);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));
        int waiting = partnerSignUpOf(ben).id();

        assertRefused(
                EventRefusal.REGISTRATION_FULL,
                () -> partners.decide(holder.id(), eventGroup.id(), waiting, RegistrationStatus.ACCEPTED, false));

        partners.decide(holder.id(), eventGroup.id(), partnerSignUpOf(mara).id(), RegistrationStatus.DENIED, false);

        assertEquals(RegistrationStatus.ACCEPTED, partnerSignUpOf(ben).status());
    }

    @Test
    void aPartnerMemberHoldingAPlaceCannotAddAFullPart() {
        var sets = eventGroups.findSets(eventGroup.id());
        eventGroups.replaceSets(
                holder.id(),
                eventGroup.id(),
                sets.stream()
                        .map(set -> new EventGroupSetDraft(
                                set.id(), set.name(), set.name().equals("Übung") ? 1 : set.minimum(), 2))
                        .toList());
        new EventGroupSettingsRepository().setOwnLimit(sat2, 1);
        signUps.register(holder.id(), eventGroup.id(), lea.id(), Set.of(sat2, test), Map.of(), null, false);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, test));

        assertRefused(
                EventRefusal.EVENT_GROUP_PART_FULL,
                () -> partnerSide.changeParts(
                        partner.id(), holder.uid(), eventGroup.id(), mara.uid(), Set.of(sat1, sat2, test)));
    }

    @Test
    void aPartLeavingEndsThePartnerWaitingThatOnlyStoodWithTheEventGroup() {
        eventGroup = jugendflamme(1);
        signUps.register(holder.id(), eventGroup.id(), lea.id(), Set.of(sat1, sun1, sat2, test), Map.of(), null, false);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, sat2, test));

        eventGroups.removePart(holder.id(), eventGroup.id(), sat2);

        assertTrue(federationRegistrations.findRegistrations(sat2, null).stream()
                .allMatch(registration -> registration.status() == RegistrationStatus.WITHDRAWN));
    }

    /**
     * A partner handed the decision gets its members' sign-ups waiting for it, and whoever runs the event
     * group neither accepts, turns down nor queues them; the host page says whose decision they are.
     */
    @Test
    void aPartnerThatDecidesKeepsItsSignUpsToItself() {
        eventGroup = jugendflamme(5);
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, null, true);

        var standing = partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        assertEquals(
                RegistrationStatus.PENDING, standing.status(), "the event group confirms nobody, the partner does");
        assertNull(rankOf(partnerSignUpOf(mara).id()), "and nobody waits on the list for it");
        int signUpId = partnerSignUpOf(mara).id();
        assertRefused(
                EventRefusal.PARTNER_DECIDES_ITS_OWN_FOR_EVENT_GROUP,
                () -> partners.decide(holder.id(), eventGroup.id(), signUpId, RegistrationStatus.ACCEPTED));
        assertRefused(
                EventRefusal.PARTNER_DECIDES_ITS_OWN_FOR_EVENT_GROUP,
                () -> partners.decide(holder.id(), eventGroup.id(), signUpId, RegistrationStatus.DENIED));
        assertRefused(
                EventRefusal.PARTNER_DECIDES_ITS_OWN_FOR_EVENT_GROUP,
                () -> partners.moveToQueue(holder.id(), eventGroup.id(), List.of(signUpId)));
        assertEquals(RegistrationStatus.PENDING, partnerSignUpOf(mara).status());

        var listed = partners.signUps(holder.id(), eventGroup.id()).getFirst();
        assertEquals(partnerOfHolder, listed.partnerId());
        assertTrue(listed.partnerDecides());
    }

    /** A partner's member already waiting may still be moved within the list after the partner took the decision. */
    @Test
    void aWaitingMemberOfADecidingPartnerIsStillMovedInLine() {
        eventGroup = jugendflamme(1);
        signUps.register(holder.id(), eventGroup.id(), lea.id(), Set.of(sat1, sun1, test), Map.of(), null, false);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, null, true);
        int second = partnerSignUpOf(ben).id();

        partners.moveInQueue(holder.id(), eventGroup.id(), second, 1);

        assertEquals(1, rankOf(second));
    }

    /**
     * A partner whose places are all taken is passed over when a place falls free, keeping its place in
     * line, and the next one waiting moves up instead. Once one of the partner's own places falls free,
     * its member moves up.
     */
    @Test
    void aPartnerAtItsPlacesIsPassedOverAndTheNextMovesUp() {
        eventGroup = jugendflamme(2);
        var account = accountRepo.create("tom-" + UUID.randomUUID() + "@holder-group.test", "Tom", "Holder");
        accounts.add(account.id());
        var tom = stationMemberRepo.create(holder.id(), account.id());
        signUps.register(holder.id(), eventGroup.id(), lea.id(), Set.of(sat1, sun1, test), Map.of(), null, false);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));
        signUps.register(holder.id(), eventGroup.id(), tom.id(), Set.of(sat1, sun1, test), Map.of(), null, false);
        assertEquals(RegistrationStatus.QUEUED, partnerSignUpOf(ben).status());
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, 1, true);
        assertEquals(
                1,
                partners.partnerPlaces(holder.id(), eventGroup.id()).getFirst().taken());

        signUps.withdraw(holder.id(), eventGroup.id(), lea.id());

        assertEquals(RegistrationStatus.QUEUED, partnerSignUpOf(ben).status(), "the partner's one place is taken");
        assertEquals(1, rankOf(partnerSignUpOf(ben).id()));
        assertEquals(
                RegistrationStatus.ACCEPTED,
                new EventGroupRegistrationRepository()
                        .find(eventGroup.id(), tom.id())
                        .orElseThrow()
                        .status(),
                "the next one waiting moves up instead");

        partnerSide.withdraw(partner.id(), holder.uid(), eventGroup.id(), mara);

        assertEquals(
                RegistrationStatus.ACCEPTED,
                partnerSignUpOf(ben).status(),
                "once the partner's own place falls free, its member waiting first moves up");
    }

    /**
     * Places go only to a partner the event group is shared with, and go with the share: a share naming
     * others drops them, and so does ending the share. An event group of another station is not there.
     */
    @Test
    void placesAtAnEventGroupGoWithItsShare() {
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, 2, true);
        assertEquals(
                2,
                partners.partnerPlaces(holder.id(), eventGroup.id())
                        .getFirst()
                        .arrangement()
                        .slotBudget());

        partners.share(holder.id(), eventGroup.id(), ShareScope.SPECIFIC, List.of());
        assertTrue(partners.partnerPlaces(holder.id(), eventGroup.id()).isEmpty());
        assertRefused(
                EventRefusal.PLACES_PARTNER_NOT_SHARED_WITH,
                () -> partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, 2, true));

        partners.share(holder.id(), eventGroup.id(), ShareScope.SPECIFIC, List.of(partnerOfHolder));
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, 2, true);
        partners.share(holder.id(), eventGroup.id(), ShareScope.ALL_PARTNERS, List.of());
        assertEquals(1, partners.partnerPlaces(holder.id(), eventGroup.id()).size());

        partners.unshare(holder.id(), eventGroup.id());
        assertTrue(partners.partnerPlaces(holder.id(), eventGroup.id()).isEmpty());
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> partners.arrangePartnerPlaces(partner.id(), eventGroup.id(), partnerOfHolder, 2, true));
        assertRefused(EventRefusal.EVENT_GROUP_NOT_HERE, () -> partners.partnerPlaces(partner.id(), eventGroup.id()));
    }

    /**
     * A partner confirms its own members only where it was handed the decision, and the station holding
     * the event group tells it so; the event group page there shows what it was given only then.
     */
    @Test
    void aPartnerConfirmsItsOwnOnlyWhereItDecides() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        assertNull(partnerSide
                .eventGroup(partner.id(), holder.uid(), eventGroup.id(), Map.of(mara.uid(), mara.id()))
                .places());

        assertRefused(
                EventRefusal.PARTNER_DOES_NOT_CONFIRM_ITS_OWN_FOR_EVENT_GROUP,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid()));

        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, 2, true);
        var places = partnerSide
                .eventGroup(partner.id(), holder.uid(), eventGroup.id(), Map.of(mara.uid(), mara.id()))
                .places();
        assertEquals(2, places.slotBudget());
        assertTrue(places.decidesItself());
    }

    /**
     * A pending sign-up is confirmed with every part; confirming again changes nothing; and a sign-up
     * beyond the partner's places is refused as an ordinary answer.
     */
    @Test
    void aPartnerConfirmsWithinItsPlaces() {
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, 1, true);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));

        var confirmed = partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid());

        assertEquals(RegistrationStatus.ACCEPTED, confirmed.status());
        assertTrue(partnerRepo.findLinked(partnerSignUpOf(mara).id()).stream()
                .allMatch(registration -> registration.status() == RegistrationStatus.ACCEPTED));
        assertEquals(
                RegistrationStatus.ACCEPTED,
                partnerSide
                        .confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid())
                        .status(),
                "confirming again answers as it stands");
        assertRefused(
                EventRefusal.NO_PLACES_LEFT_FOR_PARTNER,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), ben.uid()));
        assertEquals(RegistrationStatus.PENDING, partnerSignUpOf(ben).status());
    }

    /**
     * Within its places but on a full event group, a confirmed member waits on the list like everybody
     * else, and waiting counts against the partner's places; confirming the waiting one again leaves it.
     */
    @Test
    void aConfirmedMemberWaitsWhereTheEventGroupIsFull() {
        eventGroup = jugendflamme(1);
        signUps.register(holder.id(), eventGroup.id(), lea.id(), Set.of(sat1, sun1, test), Map.of(), null, false);
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, 1, true);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));

        var waiting = partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid());

        assertEquals(RegistrationStatus.QUEUED, waiting.status());
        assertEquals(1, waiting.queuePosition());
        assertEquals(
                RegistrationStatus.QUEUED,
                partnerSide
                        .confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid())
                        .status());
        assertRefused(
                EventRefusal.NO_PLACES_LEFT_FOR_PARTNER,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), ben.uid()));

        signUps.withdraw(holder.id(), eventGroup.id(), lea.id());
        assertEquals(RegistrationStatus.ACCEPTED, partnerSignUpOf(mara).status());
    }

    /** A withdrawn or turned-down sign-up is not there to confirm, and a called-off event group confirms nobody. */
    @Test
    void onlyAStandingSignUpOfAnEventGroupStillOnIsConfirmed() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partners.decide(holder.id(), eventGroup.id(), partnerSignUpOf(mara).id(), RegistrationStatus.DENIED);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));
        partnerSide.withdraw(partner.id(), holder.uid(), eventGroup.id(), ben);
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, null, true);

        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_SIGNED_UP,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid()));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_SIGNED_UP,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), ben.uid()));

        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));
        eventGroupRepo.cancel(eventGroup.id(), "Unwetter");
        assertRefused(
                EventRefusal.EVENT_GROUP_CANCELLED,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), ben.uid()));
    }

    /**
     * A partner confirms only at an event group shared with it: one shared with other partners only, or
     * no longer shared at all, is not there for it.
     */
    @Test
    void aPartnerConfirmsOnlyAtAnEventGroupSharedWithIt() {
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, null, true);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        partners.share(holder.id(), eventGroup.id(), ShareScope.SPECIFIC, List.of());
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid()));

        partners.unshare(holder.id(), eventGroup.id());
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid()));
        assertEquals(RegistrationStatus.PENDING, partnerSignUpOf(mara).status());
    }

    private void verifyTold(StationMember member, int times) {
        verify(notifier, times(times))
                .notify(
                        eq(StationAudience.household(List.of(member.id()))),
                        eq(NotificationType.EVENT_REGISTRATION_STATUS),
                        any(),
                        any());
    }

    private RegistrationStatus known(StationMember member) {
        return signedUp.knownStatus(member.id(), holder.uid(), eventGroup.id()).orElseThrow();
    }

    /**
     * Accepting, turning down and putting a partner's member on the waiting list are told to the partner,
     * which tells the member's household; reordering the list is not told.
     */
    @Test
    void theHolderTellsThePartnerAboutItsDecisions() {
        eventGroups.update(
                holder.id(),
                eventGroup.id(),
                new EventGroupDetails(
                        "Jugendflamme 1",
                        null,
                        1,
                        RegistrationDeadline.fixed(Instant.now().plus(Duration.ofDays(5))),
                        true,
                        null));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), ben, Set.of(sat1, sun1, test));
        assertEquals(RegistrationStatus.PENDING, known(mara));
        int maraSignUp = partnerSignUpOf(mara).id();
        int benSignUp = partnerSignUpOf(ben).id();

        partners.decide(holder.id(), eventGroup.id(), maraSignUp, RegistrationStatus.ACCEPTED);
        verifyTold(mara, 1);
        assertEquals(RegistrationStatus.ACCEPTED, known(mara));

        partners.moveToQueue(holder.id(), eventGroup.id(), List.of(benSignUp));
        verify(decisions).publish(argThat(statusChanged(holder.id(), benSignUp, RegistrationStatus.QUEUED, 1, false)));
        verifyTold(ben, 1);
        clearInvocations(decisions);

        partners.moveInQueue(holder.id(), eventGroup.id(), benSignUp, 1);
        verify(decisions, never())
                .publish(argThat(published -> published instanceof EventGroupPartnerSignUpStatusChanged));

        partners.decide(holder.id(), eventGroup.id(), maraSignUp, RegistrationStatus.DENIED);
        verifyTold(mara, 2);
        verify(decisions)
                .publish(argThat(statusChanged(holder.id(), benSignUp, RegistrationStatus.ACCEPTED, null, true)));
        verifyTold(ben, 2);
        assertEquals(RegistrationStatus.ACCEPTED, known(ben));
    }

    /**
     * A notice older than the one taken in, a repeat, or one about a member this station did not sign up
     * changes nothing and tells nobody.
     */
    @Test
    void aLateRepeatedOrUnknownGroupNoticeIsIgnored() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        clearInvocations(notifier);
        var hostRow =
                new FederationRepository().findPartnerById(partnerOfHolder).orElseThrow();
        var now = Instant.now();

        tell(hostRow, mara.uid(), RegistrationStatus.DENIED, now);
        tell(hostRow, mara.uid(), RegistrationStatus.DENIED, now);
        tell(hostRow, mara.uid(), RegistrationStatus.ACCEPTED, now.minusSeconds(60));
        tell(hostRow, mara.uid(), RegistrationStatus.DENIED, now.plusSeconds(60));
        tell(hostRow, ben.uid(), RegistrationStatus.DENIED, now);
        tell(hostRow, UUID.randomUUID(), RegistrationStatus.DENIED, now);

        verifyTold(mara, 1);
        assertEquals(RegistrationStatus.DENIED, known(mara));
        verifyTold(ben, 0);
    }

    /**
     * A withdrawal keeps what the partner last said, so a notice older than that is still ignored once the
     * member signs up again.
     */
    @Test
    void aGroupWithdrawalKeepsTheLastInstant() {
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        clearInvocations(notifier);
        var hostRow =
                new FederationRepository().findPartnerById(partnerOfHolder).orElseThrow();
        var now = Instant.now();
        tell(hostRow, mara.uid(), RegistrationStatus.DENIED, now);
        verifyTold(mara, 1);

        partnerSide.withdraw(partner.id(), holder.uid(), eventGroup.id(), mara);
        assertFalse(signedUp.signedUp(mara.id(), holder.uid(), eventGroup.id()));
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));

        tell(hostRow, mara.uid(), RegistrationStatus.DENIED, now.minusSeconds(60));
        verifyTold(mara, 1);
        tell(hostRow, mara.uid(), RegistrationStatus.DENIED, now.plusSeconds(60));
        verifyTold(mara, 2);
    }

    private void tell(FederationPartner hostRow, UUID member, RegistrationStatus status, Instant at) {
        transport
                .transport()
                .deliver(
                        hostRow,
                        RemoteEventGroupRoutes.EVENT_GROUP_SIGN_UP_STATUS_WEBHOOK.at(),
                        new RemoteEventGroupSignUpStatus(
                                eventGroup.id(), "Jugendflamme 1", member, status, null, false, at));
    }

    /** A partner confirming one of its own tells the member's household where they stand now. */
    @Test
    void confirmingOnesOwnSignUpIsToldToTheHousehold() {
        partners.arrangePartnerPlaces(holder.id(), eventGroup.id(), partnerOfHolder, null, true);
        partnerSide.signUp(partner.id(), holder.uid(), eventGroup.id(), mara, Set.of(sat1, sun1, test));
        clearInvocations(notifier);

        partnerSide.confirm(partner.id(), holder.uid(), eventGroup.id(), mara.uid());

        verifyTold(mara, 1);
        assertEquals(RegistrationStatus.ACCEPTED, known(mara));
    }

    private Integer rankOf(int signUpId) {
        return partners.signUps(holder.id(), eventGroup.id()).stream()
                .filter(signUp -> signUp.id() == signUpId)
                .findFirst()
                .orElseThrow()
                .standing()
                .queuePosition();
    }

    /** Matches the announcement of a decision, whatever instant it carries. */
    private static ArgumentMatcher<DomainEvent> statusChanged(
            int stationId, int id, RegistrationStatus status, Integer queuePosition, boolean movedUp) {
        return published -> published instanceof EventGroupPartnerSignUpStatusChanged changed
                && changed.stationId() == stationId
                && changed.signUpId() == id
                && changed.status() == status
                && Objects.equals(changed.queuePosition(), queuePosition)
                && changed.movedUp() == movedUp;
    }
}
