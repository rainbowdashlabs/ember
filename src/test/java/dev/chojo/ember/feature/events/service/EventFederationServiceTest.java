/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.route.FederatedEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFederationServiceTest extends RepositoryTestBase {
    private static final UUID REMOTE_MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE_MEMBER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REMOTE_MEMBER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final DomainEventBus COMMENT_BUS = mock(DomainEventBus.class);

    private static EventFederationService service;
    private static FederationService federationService;
    private static FederationRepository federationRepo;
    private static FederationHttpClient httpClient;
    private static FederationTestTransport transport;
    private static CommentService commentService;

    private static Station stationA;
    private static Station stationB;
    private static Station stationC;
    private static int partnerId;
    private static int eventId;
    private static FederationPartner localPartner;
    private static MemberIdentity testMemberIdentity;
    private static EventCrudService crudService;
    private static EventAttachmentService attachmentService;
    private static MediaLibraryService media;

    /**
     * Station A has two partners: station B on this instance, and station C, which invited it and is
     * reached over HTTP as a remote partner.
     */
    @BeforeAll
    static void setup() {
        media = mock(MediaLibraryService.class);
        attachmentService = new EventAttachmentService(new EventAttachmentRepository(), media);
        federationRepo = new FederationRepository();
        EventFederationRepository eventFederationRepo = new EventFederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, TestStationKeys.store(), new Api());
        httpClient = mock(FederationHttpClient.class);
        var eventBus = new DomainEventBus(Set.of());
        crudService = newEventServices(eventBus).crud();
        var memberSvc = newStationMemberService(accountRepo, mock(AuthService.class));
        commentService = newCommentService(COMMENT_BUS);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        service = new EventFederationService(
                eventFederationRepo,
                federationService,
                transport.transport(),
                federationRepo,
                stationRepo,
                crudService,
                commentService,
                new MemberNameResolver(
                        memberSvc,
                        accountRepo,
                        eventFederationRepo,
                        federationRepo,
                        stationRepo,
                        mock(MemberGroupService.class),
                        mock(UserTagService.class)),
                new FederationFanout(new TaskScheduler()),
                new FederationEntityResolver(federationRepo),
                attachmentService,
                new EventFieldService(
                        eventFieldRepo,
                        stationMemberRepo,
                        memberEligibility,
                        eventRepo,
                        attendanceRepo,
                        eventFieldRegistrationService),
                occurrenceCalendar,
                media,
                new Api());
        transport.serve(service);

        stationA = stationRepo.create("EventFedSvcStationA");
        stationB = stationRepo.create("EventFedSvcStationB");
        stationC = stationRepo.create("EventFedSvcStationC");

        var keyPair = federationService.generateKeyPair();
        localPartner = federationService.acceptInvite(
                stationA.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerId = localPartner.id();

        var keyPairC = federationService.generateKeyPair();
        FederationPartner remotePartner = federationService.acceptInvite(
                stationA.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPairC),
                "https://remote-event.example.com",
                null);

        Account testAccount = accountRepo.create("eventfed@test.com", "Test", "Author");
        StationMember testMember = stationMemberRepo.create(stationA.id(), testAccount.id());
        testMemberIdentity = memberIdentityFactory.local(stationA.id(), testMember.id());

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        var event = eventRepo.create(
                stationA.id(),
                "Federated Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventId = event.id();
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(stationA.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationB.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationC.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        stationRepo.delete(stationC.id());
    }

    @Test
    @Order(1)
    void setShareAllPartners() {
        var share = service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        assertNotNull(share);
        assertEquals(eventId, share.eventId());
        assertEquals(ShareScope.ALL_PARTNERS, share.scope());
    }

    @Test
    @Order(2)
    void findShareByEvent() {
        var found = service.findShareByEvent(eventId);
        assertTrue(found.isPresent());
        assertEquals(ShareScope.ALL_PARTNERS, found.get().scope());
    }

    @Test
    @Order(3)
    void setShareSpecificWithTargets() {
        var share = service.setShare(eventId, ShareScope.SPECIFIC, List.of(partnerId));
        assertNotNull(share);
        assertEquals(ShareScope.SPECIFIC, share.scope());

        var targets = service.findShareTargets(share.id());
        assertTrue(targets.contains(partnerId));
    }

    @Test
    @Order(4)
    void findSharedEventIds() {
        var sharedIds = service.findSharedEventIds(partnerId, stationA.id());
        assertTrue(sharedIds.contains(eventId));
    }

    @Test
    @Order(5)
    void findSharedEventIdsAllPartners() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var sharedIds = service.findSharedEventIds(partnerId, stationA.id());
        assertTrue(sharedIds.contains(eventId));
    }

    /**
     * An appointment not every member here may know about is withdrawn from the partners, and the
     * order the two settings were made in does not matter. The audience names groups of this station,
     * which mean nothing at the partner, so a shared one would stand open to everybody there.
     */
    @Test
    @Order(5)
    void restrictingVisibilityWithdrawsAShare() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var eventRestrictions = newEventServices(new DomainEventBus(Set.of())).restriction();
        eventRestrictions.setViewRestrictions(
                eventId,
                new RestrictionSelection(
                        List.of(StationUserType.GUARDIAN), List.of(), List.of(), List.of(), RestrictionMode.AND));

        assertFalse(service.findSharedEventIds(partnerId, stationA.id()).contains(eventId));

        eventRestrictions.setViewRestrictions(eventId, RestrictionSelection.empty());
        assertTrue(service.findSharedEventIds(partnerId, stationA.id()).contains(eventId));
    }

    @Test
    @Order(6)
    void removeShare() {
        service.removeShare(eventId);
        var found = service.findShareByEvent(eventId);
        assertTrue(found.isEmpty());
    }

    @Test
    @Order(7)
    void findShareByEventMissing() {
        assertTrue(service.findShareByEvent(999999).isEmpty());
    }

    @Test
    @Order(10)
    void registerFederated() {
        var reg = service.registerFederated(eventId, partnerId, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1));
        assertNotNull(reg);
        assertEquals(eventId, reg.eventId());
        assertEquals(partnerId, reg.partnerId());
        assertEquals(REMOTE_MEMBER_1, reg.remoteMemberId());
        assertEquals(LocalDate.of(2026, 7, 1), reg.eventDate());
        assertNotNull(reg.status());
    }

    @Test
    @Order(11)
    void findRegistrationById() {
        var reg = service.registerFederated(eventId, partnerId, REMOTE_MEMBER_2, LocalDate.of(2026, 7, 2));
        var found = service.findRegistrationById(reg.id());
        assertTrue(found.isPresent());
        assertEquals(reg.id(), found.get().id());
        assertEquals(REMOTE_MEMBER_2, found.get().remoteMemberId());
    }

    @Test
    @Order(12)
    void findRegistrationByIdMissing() {
        assertTrue(service.findRegistrationById(999999).isEmpty());
    }

    @Test
    @Order(13)
    void updateRegistrationStatus() {
        var reg = service.registerFederated(eventId, partnerId, REMOTE_MEMBER_3, LocalDate.of(2026, 7, 3));
        boolean updated = service.updateRegistrationStatus(reg.id(), RegistrationStatus.ACCEPTED);
        assertTrue(updated);
        var found = service.findRegistrationById(reg.id()).orElseThrow();
        assertEquals(RegistrationStatus.ACCEPTED, found.status());
    }

    @Test
    @Order(14)
    void findRegistrations() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        var regs = service.findRegistrations(eventId, date);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().anyMatch(r -> r.remoteMemberId().equals(REMOTE_MEMBER_1)));
    }

    @Test
    @Order(15)
    void findRegistrationsByPartner() {
        var regs = service.findRegistrationsByPartner(partnerId);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().anyMatch(r -> r.remoteMemberId().equals(REMOTE_MEMBER_1)));
    }

    @Test
    @Order(16)
    void findRegistrationsNullDate() {
        var regs = service.findRegistrations(eventId, null);
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
    }

    @Test
    @Order(16)
    void findRegistrationsByRemoteMember() {
        var regs = service.findRegistrationsByRemoteMember(REMOTE_MEMBER_1);
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().allMatch(r -> r.remoteMemberId().equals(REMOTE_MEMBER_1)));
    }

    @Test
    @Order(17)
    void withdrawRegistration() {
        UUID toWithdraw = UUID.fromString("00000000-0000-0000-0000-000000000099");
        LocalDate day = LocalDate.of(2026, 8, 1);
        service.registerFederated(eventId, partnerId, toWithdraw, day);

        assertTrue(service.withdrawRegistration(eventId, partnerId, toWithdraw, day));
        var withdrawn = service.findRegistrations(eventId, day).stream()
                .filter(reg -> reg.remoteMemberId().equals(toWithdraw))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the row stays, or the host cannot tell somebody who left from somebody who never answered"));
        assertEquals(RegistrationStatus.WITHDRAWN, withdrawn.status());

        assertTrue(
                service.undoWithdrawal(eventId, partnerId, toWithdraw, day),
                "and a partner may ask for it back while the window is open");
        assertTrue(
                service.registerFederated(eventId, partnerId, toWithdraw, day).id() > 0,
                "registering again after a withdrawal answers rather than colliding with the old row");
    }

    /**
     * The three arrangements, and the default being the one the product always had.
     *
     * <p>Nobody has to configure anything for the host to decide: that is what holds where nothing is
     * said, and it is what every shared appointment did before any of this existed.
     */
    @Test
    @Order(19)
    void aPartnerDecidesOnlyWhereTheHostSaidSo() {
        service.setPartnerPlaces(eventId, partnerId, null, false);
        assertFalse(
                service.partnerPlaces(eventId, partnerId).partnerConfirms(),
                "saying nothing leaves the decision with the station holding the appointment");
        assertNull(
                service.partnerPlaces(eventId, partnerId).slotBudget(),
                "and leaves it uncapped, which is what no arrangement means");

        service.setPartnerPlaces(eventId, partnerId, null, true);
        assertTrue(service.partnerPlaces(eventId, partnerId).partnerConfirms(), "the partner may be given the pen");
        assertNull(service.partnerPlaces(eventId, partnerId).slotBudget(), "with no cap on how many come");

        service.setPartnerPlaces(eventId, partnerId, 2, true);
        assertEquals(2, service.partnerPlaces(eventId, partnerId).slotBudget(), "or with one");

        service.setPartnerPlaces(eventId, partnerId, 3, true);
        assertEquals(
                1,
                service.partnerPlaces(eventId).size(),
                "the screen that arranges this reads every partner's arrangement for the appointment");

        service.setPartnerPlaces(eventId, partnerId, null, false);
        assertFalse(
                service.partnerPlaces(eventId, partnerId).partnerConfirms(),
                "and the host can take it back, which leaves no arrangement rather than a false one");
        assertTrue(
                service.partnerPlaces(eventId).isEmpty(),
                "taking it back leaves no row, so nothing reads as an arrangement that is not one");
    }

    /**
     * A visitor is asked about the date the way a member of this station is: a date the series does
     * not fall on and a date that was called off are both refused, and the dates beside a cancelled
     * one still take visitors.
     */
    @Test
    @Order(19)
    void aVisitorCannotRegisterForACancelledDate() {
        var calendar = occurrenceCalendar.forStation(stationA.id());
        LocalDate next = calendar.today().plusDays(2);
        var weekly = eventRepo.create(
                stationA.id(),
                "Weekly Visitors",
                "desc",
                StationEvent.EventType.RECURRING,
                next.getDayOfWeek().getValue(),
                next.minusWeeks(2).atTime(18, 0).atZone(calendar.zone()).toInstant(),
                next.minusWeeks(2).atTime(20, 0).atZone(calendar.zone()).toInstant(),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        UUID visitor = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        try {
            eventDateCancellationRepo.cancel(weekly.id(), next, CancellationCause.MANUAL, null, null);

            var cancelled = assertThrows(
                    RefusalResponse.class, () -> service.registerFederated(weekly.id(), partnerId, visitor, next));
            assertEquals(Refusal.REGISTRATION_DAY_CANCELLED, cancelled.refusal());
            var notADate = assertThrows(
                    RefusalResponse.class,
                    () -> service.registerFederated(weekly.id(), partnerId, visitor, next.plusDays(1)));
            assertEquals(Refusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE, notADate.refusal());
            assertEquals(
                    next.plusWeeks(1),
                    service.registerFederated(weekly.id(), partnerId, visitor, next.plusWeeks(1))
                            .eventDate());
        } finally {
            eventRepo.delete(weekly.id());
        }
    }

    /**
     * One partner's member on one date, which is what a partner names when it confirms or takes back.
     * A date nobody answered for is empty rather than somebody else's row.
     */
    @Test
    @Order(19)
    void aRegistrationIsFoundByThePartnerAndTheDayItIsFor() {
        LocalDate day = LocalDate.of(2026, 9, 11);
        UUID who = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        service.registerFederated(eventId, partnerId, who, day);

        assertTrue(service.findRegistration(eventId, partnerId, who, day).isPresent());
        assertTrue(
                service.findRegistration(eventId, partnerId, who, day.plusDays(1))
                        .isEmpty(),
                "another day is another answer, or none");
    }

    /**
     * A budget is spent in the statement that grants the place.
     *
     * <p>Counting first and writing after is exactly the race this has to survive: two people at the
     * partner pressing confirm in the same moment would both see room and both take the last place.
     */
    @Test
    @Order(19)
    void aBudgetCannotBeOverspent() {
        LocalDate day = LocalDate.of(2026, 9, 9);
        UUID first = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        service.setPartnerPlaces(eventId, partnerId, 1, true);
        var one = service.registerFederated(eventId, partnerId, first, day);
        var two = service.registerFederated(eventId, partnerId, second, day);

        assertTrue(
                service.acceptWithinBudget(one.id(), eventId, partnerId, day),
                "the one place the partner was given is theirs to fill");
        assertFalse(
                service.acceptWithinBudget(two.id(), eventId, partnerId, day),
                "and the second is refused rather than quietly granted");

        var counted = service.countPartnerPlaces(eventId, partnerId, day);
        assertEquals(1, counted.taken());
        assertEquals(1, counted.budget());

        service.setPartnerPlaces(eventId, partnerId, null, false);
    }

    /**
     * Lowering a budget below what is already taken never un-invites anybody.
     *
     * <p>Withdrawing a place already granted is a conversation between two stations, not a number
     * quietly going down. The places stand and no more may be added.
     */
    @Test
    @Order(19)
    void loweringABudgetLeavesThePlacesAlreadyGiven() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        UUID held = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID wanted = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        service.setPartnerPlaces(eventId, partnerId, 2, true);
        var standing = service.registerFederated(eventId, partnerId, held, day);
        assertTrue(service.acceptWithinBudget(standing.id(), eventId, partnerId, day));

        service.setPartnerPlaces(eventId, partnerId, 1, true);
        assertEquals(
                1, service.countPartnerPlaces(eventId, partnerId, day).taken(), "the place already given still stands");

        var next = service.registerFederated(eventId, partnerId, wanted, day);
        assertFalse(
                service.acceptWithinBudget(next.id(), eventId, partnerId, day), "and the room that is gone is gone");

        service.setPartnerPlaces(eventId, partnerId, null, false);
    }

    @Test
    @Order(20)
    void cacheAndGetName() {
        service.cacheName(partnerId, REMOTE_MEMBER_1, "Alice Smith");
        var cached = service.getCachedName(partnerId, REMOTE_MEMBER_1);
        assertTrue(cached.isPresent());
        assertEquals("Alice Smith", cached.get());
    }

    @Test
    @Order(21)
    void getCachedNameMissing() {
        var cached = service.getCachedName(partnerId, UUID.randomUUID());
        assertTrue(cached.isEmpty());
    }

    @Test
    @Order(22)
    void cacheNameUpdatesExisting() {
        service.cacheName(partnerId, REMOTE_MEMBER_1, "Alice Updated");
        var cached = service.getCachedName(partnerId, REMOTE_MEMBER_1);
        assertTrue(cached.isPresent());
        assertEquals("Alice Updated", cached.get());
    }

    @Test
    @Order(23)
    void invalidateName() {
        UUID toInvalidate = UUID.fromString("00000000-0000-0000-0000-000000000098");
        service.cacheName(partnerId, toInvalidate, "Bob");
        service.invalidateName(partnerId, toInvalidate);
        assertTrue(service.getCachedName(partnerId, toInvalidate).isEmpty());
    }

    @Test
    @Order(30)
    void browseFederatedEventsWithShare() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var items = service.browseFederatedEvents(stationB.id());
        assertFalse(items.isEmpty(), "Should find shared events");
        assertTrue(items.stream().anyMatch(item -> {
            if (item.event() instanceof SharedEvent re) {
                return eventId == re.id();
            }
            return false;
        }));
    }

    @Test
    @Order(31)
    void browseFederatedEventsNoShares() {
        service.removeShare(eventId);
        var items = service.browseFederatedEvents(stationB.id());
        assertTrue(items.stream().noneMatch(item -> {
            if (item.event() instanceof SharedEvent re) {
                return eventId == re.id();
            }
            return false;
        }));
    }

    /**
     * A partner on this instance is told what it was given, exactly as one across the network is.
     *
     * <p>Both stations keep a record of the other, and everything the host writes hangs off the
     * host's. Read through the visitor's, which is the one the visitor's own screens carry, the host
     * appears to have set nothing aside: the places went missing and the choosing silently stayed
     * with the host, on the one arrangement where it was meant to move.
     */
    @Test
    @Order(31)
    void aPartnerOnThisInstanceIsToldWhatItWasGiven() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var hostPartner = federationService.findPartners(stationA.id()).stream()
                .filter(p -> p.partnerStationId().equals(stationB.uid()))
                .findFirst()
                .orElseThrow();
        service.setPartnerPlaces(eventId, hostPartner.id(), 2, true);
        try {
            var detail = service.getFederatedEvent(stationB.id(), stationA.uid(), eventId);
            assertNotNull(detail.places(), "the arrangement reaches the station it was made with");
            assertTrue(detail.places().decidesItself(), "which is what says the choosing is theirs");
            assertEquals(2, detail.places().slotBudget(), "and how many places they were given");
        } finally {
            service.setPartnerPlaces(eventId, hostPartner.id(), null, false);
        }
    }

    /**
     * A visitor meets the same door a member does, wherever their station is kept.
     *
     * <p>The remote route asked this and the same-instance path did not, so two stations sharing an
     * instance could sign up for an appointment that had been called off, or one whose list closed
     * weeks ago. The question belongs where both paths pass, not on one of them.
     */
    @Test
    @Order(33)
    void aVisitorIsTurnedAwayFromAnAppointmentThatTakesNoRegistrations() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        var openHouse = eventRepo.create(
                stationA.id(),
                "Kein Anmeldung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            assertThrows(
                    RefusalResponse.class,
                    () -> service.registerFederated(
                            openHouse.id(), partnerId, REMOTE_MEMBER_3, LocalDate.of(2026, 7, 9)),
                    "an appointment with no list has none for a visitor either");
        } finally {
            eventRepo.delete(openHouse.id());
        }
    }

    @Test
    @Order(32)
    void getFederatedEventLocal() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var result = service.getFederatedEvent(stationB.id(), stationA.uid(), eventId);
        assertNotNull(result);
        assertEquals(eventId, result.event().id());
        assertEquals("Federated Event", result.event().name());
        assertNull(result.places(), "nothing was set aside, so the holder decides as it always did");
        transport.assertParity(
                partnerOf(stationB, stationA),
                RemoteEventRoutes.GET_EVENT.at(eventId),
                null,
                RemoteEventRoutes.RemoteEventDetail.class);
        transport.assertParity(
                partnerOf(stationB, stationA), RemoteEventRoutes.LIST_EVENTS.at(), null, SharedEvent.class);
    }

    /**
     * An appointment shared with named partners names the holder's own partner rows. A partner on
     * this instance used to be looked up by its own row instead and never saw such an appointment.
     */
    @Test
    @Order(32)
    void anAppointmentSharedWithNamedPartnersReachesAPartnerHere() {
        service.setShare(
                eventId,
                ShareScope.SPECIFIC,
                List.of(partnerOf(stationA, stationB).id()));
        assertEquals(
                eventId,
                service.getFederatedEvent(stationB.id(), stationA.uid(), eventId)
                        .event()
                        .id());
        assertTrue(service.browseFederatedEvents(stationB.id()).stream()
                .anyMatch(item -> ((SharedEvent) item.event()).id() == eventId));
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
    }

    /** Whether the partner still exists depends on other tests; either way an unshared event is refused. */
    @Test
    @Order(33)
    void getFederatedEventNotShared() {
        service.removeShare(eventId);
        assertThrows(Exception.class, () -> service.getFederatedEvent(stationB.id(), stationA.uid(), eventId));
    }

    @Test
    @Order(34)
    void federatedEventItemRecord() {
        var event =
                new SharedEvent(1, "Training", "", StationEvent.EventType.ONE_TIME, 0, "", "", false, true, null, null);
        var item = new EventFederationService.FederatedEventItem(
                42, "TestStation", "00000000-0000-0000-0000-000000000042", event);
        assertEquals(42, item.partnerId());
        assertEquals("TestStation", item.partnerStationName());
        assertEquals(event, item.event());
    }

    @Test
    @Order(40)
    void browseFederatedEventsViaHttp() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());

        var remoteEvent = new SharedEvent(
                9999,
                "Remote Event",
                "Remote desc",
                StationEvent.EventType.ONE_TIME,
                0,
                Instant.now().toString(),
                Instant.now().plus(2, ChronoUnit.HOURS).toString(),
                true,
                false,
                null,
                null);
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        any(),
                        eq(stationA.id()),
                        eq(SharedEvent.class)))
                .thenReturn(List.of(remoteEvent));

        var items = service.browseFederatedEvents(stationA.id());
        assertFalse(items.isEmpty(), "Should include events from local and/or remote partners");

        verify(httpClient)
                .getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        any(),
                        eq(stationA.id()),
                        eq(SharedEvent.class));

        assertTrue(
                items.stream().anyMatch(i -> {
                    if (i.event() instanceof SharedEvent re) {
                        return re.id() == 9999 && re.name().equals("Remote Event");
                    }
                    return false;
                }),
                "Should contain the mocked remote event");
    }

    /** A partner on another instance answers for itself, and what it answers is passed straight on. */
    @Test
    @Order(41)
    void getFederatedEventRemote() {
        var remoteEvent = new SharedEvent(
                eventId,
                "Remote Event",
                "desc",
                StationEvent.EventType.RECURRING,
                1,
                "10:00",
                "12:00",
                false,
                false,
                LocalDate.parse("2026-12-31"),
                null);
        when(httpClient.get(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId),
                        any(),
                        eq(stationA.id()),
                        any()))
                .thenReturn(new RemoteEventRoutes.RemoteEventDetail(remoteEvent, List.of(), null));

        var result = service.getFederatedEvent(stationA.id(), stationC.uid(), eventId);
        assertNotNull(result);
        assertEquals(eventId, result.event().id());
        assertEquals("Remote Event", result.event().name());

        verify(httpClient)
                .get(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId),
                        any(),
                        eq(stationA.id()),
                        any());
    }

    @Test
    @Order(42)
    void getFederatedEventRemoteReturnsNull() {
        when(httpClient.get(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId),
                        any(),
                        eq(stationA.id()),
                        any()))
                .thenReturn(null);

        var refused = assertThrows(
                RefusalResponse.class, () -> service.getFederatedEvent(stationA.id(), stationC.uid(), eventId));
        assertEquals(Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER, refused.refusal());
    }

    /** Station B has no remote partner, so it browses only station A's locally shared events. */
    @Test
    @Order(43)
    void browseFederatedEventsHttpReturnsEmpty() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var items = service.browseFederatedEvents(stationB.id());
        assertNotNull(items);
        assertTrue(
                items.stream().anyMatch(i -> {
                    if (i.event() instanceof SharedEvent re) {
                        return eventId == re.id();
                    }
                    return false;
                }),
                "Should contain locally shared events from stationA");
    }

    /** Station B shares nothing and station C answers with nothing, so station A browses nothing. */
    @Test
    @Order(44)
    void browseFederatedEventsRemoteReturnsEmptyLocalHasNone() {
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        any(),
                        eq(stationA.id()),
                        eq(SharedEvent.class)))
                .thenReturn(List.of());

        var items = service.browseFederatedEvents(stationA.id());
        assertNotNull(items);
    }

    @Test
    @Order(50)
    void createRemoteComment() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        service.cacheName(partnerId, REMOTE_MEMBER_1, "Alice Remote");

        var response = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Hello from remote!", null);
        assertNotNull(response);
        assertEquals("Hello from remote!", response.content());
        assertFalse(response.deleted());
        assertNotNull(response.author());
        assertEquals(REMOTE_MEMBER_1, response.author().memberUid());
        assertEquals("Alice Remote", response.authorName());
    }

    @Test
    @Order(51)
    void createRemoteCommentWithParent() {
        var parent = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Parent comment", null);
        var reply = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_2, "Bob Remote", parent.id(), "Reply to parent", null);
        assertNotNull(reply);
        assertEquals(parent.id(), reply.parentId());
        assertEquals("Reply to parent", reply.content());
    }

    @Test
    @Order(52)
    void updateRemoteComment() {
        var created = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Original content", null);
        var updated = service.updateRemoteComment(localPartner, created.id(), REMOTE_MEMBER_1, "Updated content");
        assertNotNull(updated);
        assertEquals("Updated content", updated.content());
        assertNotNull(updated.updatedAt());
    }

    @Test
    @Order(53)
    void updateRemoteCommentWrongOwner() {
        var created = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "My comment", null);
        assertThrows(
                RefusalResponse.class,
                () -> service.updateRemoteComment(localPartner, created.id(), REMOTE_MEMBER_2, "Hacked!"));
    }

    @Test
    @Order(54)
    void updateRemoteCommentNotFederated() {
        var localComment = localComment("Local comment");
        assertThrows(
                RefusalResponse.class,
                () -> service.updateRemoteComment(localPartner, localComment.id(), REMOTE_MEMBER_1, "Edited"));
    }

    @Test
    @Order(55)
    void deleteRemoteComment() {
        var created = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_3, "Delete Me", null, "To be deleted", null);
        boolean deleted = service.deleteRemoteComment(localPartner, created.id(), REMOTE_MEMBER_3);
        assertTrue(deleted);
    }

    @Test
    @Order(56)
    void deleteRemoteCommentWrongOwner() {
        var created =
                service.createRemoteComment(localPartner, eventId, REMOTE_MEMBER_1, "Alice", null, "My comment", null);
        assertThrows(
                RefusalResponse.class, () -> service.deleteRemoteComment(localPartner, created.id(), REMOTE_MEMBER_2));
    }

    @Test
    @Order(57)
    void deleteRemoteCommentNotFederated() {
        var localComment = localComment("Local comment to delete");
        assertThrows(
                RefusalResponse.class,
                () -> service.deleteRemoteComment(localPartner, localComment.id(), REMOTE_MEMBER_1));
    }

    /**
     * A partner's reply to a member here, mentioning that member, tells nobody here; its removal
     * still withdraws what was written about it.
     */
    @Test
    @Order(58)
    void aPartnersCommentTellsNobodyButItsRemovalIsAnnounced() {
        var local = localComment("Frage von hier");
        reset(COMMENT_BUS);

        var reply = service.createRemoteComment(
                localPartner,
                eventId,
                REMOTE_MEMBER_3,
                "Partner",
                local.id(),
                "@[%s/%s:Hier] Antwort".formatted(testMemberIdentity.stationUid(), testMemberIdentity.memberUid()),
                null);
        verify(COMMENT_BUS, never()).publish(any());

        service.deleteRemoteComment(localPartner, reply.id(), REMOTE_MEMBER_3);
        verify(COMMENT_BUS)
                .publish(argThat(event -> event instanceof CommentDeleted deleted
                        && deleted.commentId() == reply.id()
                        && deleted.entityType() == CommentEntityType.EVENT));
    }

    private static Comment localComment(String content) {
        return commentRepo.create(CommentEntityType.EVENT, eventId, null, null, testMemberIdentity, content);
    }

    @Test
    @Order(60)
    void toCommentResponseDeletedComment() {
        var comment = localComment("Will be deleted");
        commentService.delete(comment);
        var deletedComment = new Comment(
                comment.id(),
                comment.type(),
                comment.targetId(),
                comment.stationId(),
                comment.eventDate(),
                comment.parentId(),
                comment.author(),
                "",
                true,
                comment.createdAt(),
                comment.updatedAt());
        var response = service.toCommentResponse(deletedComment);
        assertTrue(response.deleted());
        assertEquals("", response.content());
        assertNull(response.authorName());
        assertNull(response.author());
    }

    @Test
    @Order(61)
    void toCommentResponseLocalAuthor() {
        var comment = localComment("Local author comment");
        var response = service.toCommentResponse(comment);
        assertFalse(response.deleted());
        assertEquals("Local author comment", response.content());
        assertNotNull(response.author());
        assertNotNull(response.author().stationUid());
        assertNotNull(response.author().memberUid());
        assertTrue(response.authorName().contains("Test"));
        assertTrue(response.authorName().contains("Author"));
    }

    @Test
    @Order(62)
    void toCommentResponseFederatedAuthor() {
        var created = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_2, "Bob Federated", null, "Federated comment", null);
        var comment =
                commentService.findById(CommentEntityType.EVENT, created.id()).orElseThrow();
        var response = service.toCommentResponse(comment);
        assertFalse(response.deleted());
        assertEquals("Federated comment", response.content());
        assertNotNull(response.author());
        assertEquals(REMOTE_MEMBER_2, response.author().memberUid());
        assertNotNull(response.author().stationUid());
        assertEquals("Bob Federated", response.authorName());
    }

    @Test
    @Order(63)
    void listComments() {
        var comments = service.listComments(eventId);
        assertNotNull(comments);
        assertFalse(comments.isEmpty(), "Should have comments from earlier tests");
        assertTrue(comments.stream().allMatch(c -> c.id() > 0));
    }

    @Test
    @Order(70)
    void listFederatedCommentsLocal() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var result = service.listFederatedComments(stationB.id(), stationA.uid(), eventId);
        assertNotNull(result);
        transport.assertParity(
                partnerOf(stationB, stationA),
                RemoteEventRoutes.LIST_COMMENTS.at(eventId),
                null,
                CommentResponse.class);
    }

    @Test
    @Order(71)
    void listFederatedCommentsRemote() {
        var mockResponses = List.of(new CommentResponse(
                1,
                null,
                null,
                null,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote User",
                "Remote comment",
                false,
                Instant.now(),
                null,
                null));
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId + "/comments"),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(mockResponses);

        var result = service.listFederatedComments(stationA.id(), stationC.uid(), eventId);
        assertEquals(1, result.size());
        assertEquals("Remote comment", result.getFirst().content());
    }

    @Test
    @Order(72)
    void listFederatedCommentsUnknownPartner() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listFederatedComments(stationA.id(), UUID.randomUUID(), eventId));
    }

    @Test
    @Order(73)
    void createFederatedCommentLocal() {
        var result = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Local federated create", null);
        assertEquals("Local federated create", result.content());
    }

    /**
     * A partner on this instance used to comment on any appointment of the station it is paired
     * with, shared or not, while a partner on another instance was refused. Both are refused now.
     */
    @Test
    @Order(73)
    void commentsOnAnAppointmentNotSharedWithAPartnerHereAreRefused() {
        var unshared = eventRepo.create(
                stationA.id(),
                "Unshared Event",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.createFederatedComment(
                        stationB.id(), stationA.uid(), unshared.id(), REMOTE_MEMBER_1, "Alice", null, "No", null));
        assertEquals(Refusal.EVENT_NOT_SHARED_WITH_PARTNER, refused.refusal());
        assertThrows(
                RefusalResponse.class,
                () -> service.listFederatedComments(stationB.id(), stationA.uid(), unshared.id()));
        assertThrows(
                RefusalResponse.class, () -> service.getFederatedEvent(stationB.id(), stationA.uid(), unshared.id()));
    }

    @Test
    @Order(74)
    void createFederatedCommentRemote() {
        var mockResponse = new CommentResponse(
                99,
                null,
                null,
                null,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote Author",
                "Remote created",
                false,
                Instant.now(),
                null,
                null);
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId + "/comments"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(mockResponse);

        var result = service.createFederatedComment(
                stationA.id(), stationC.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Remote content", null);
        assertEquals("Remote created", result.content());
    }

    @Test
    @Order(75)
    void createFederatedCommentRemoteReturnsNull() {
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId + "/comments"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(null);

        assertThrows(
                RefusalResponse.class,
                () -> service.createFederatedComment(
                        stationA.id(), stationC.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Will fail", null));
    }

    @Test
    @Order(76)
    void updateFederatedCommentLocal() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "To update locally", null);
        int commentId = createResult.id();

        var result = service.updateFederatedComment(
                stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_1, "Updated locally");
        assertEquals("Updated locally", result.content());
    }

    @Test
    @Order(77)
    void updateFederatedCommentLocalWrongOwner() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Owner check", null);
        int commentId = createResult.id();

        assertThrows(
                RefusalResponse.class,
                () -> service.updateFederatedComment(
                        stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_2, "Wrong owner"));
    }

    @Test
    @Order(78)
    void updateFederatedCommentRemote() {
        var mockResponse = new CommentResponse(
                100,
                null,
                null,
                null,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote",
                "Updated remote",
                false,
                Instant.now(),
                Instant.now(),
                null);
        when(httpClient.put(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/100"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(mockResponse);

        var result =
                service.updateFederatedComment(stationA.id(), stationC.uid(), 100, REMOTE_MEMBER_1, "Updated remote");
        assertEquals("Updated remote", result.content());
    }

    @Test
    @Order(79)
    void updateFederatedCommentRemoteReturnsNull() {
        when(httpClient.put(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/200"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(null);

        assertThrows(
                RefusalResponse.class,
                () -> service.updateFederatedComment(stationA.id(), stationC.uid(), 200, REMOTE_MEMBER_1, "Will fail"));
    }

    @Test
    @Order(80)
    void deleteFederatedCommentLocal() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_3, "Charlie", null, "Delete me locally", null);
        int commentId = createResult.id();

        service.deleteFederatedComment(stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_3);
        assertTrue(commentService
                .findById(CommentEntityType.EVENT, commentId)
                .map(Comment::deleted)
                .orElse(true));
    }

    @Test
    @Order(81)
    void deleteFederatedCommentLocalWrongOwner() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Delete check", null);
        int commentId = createResult.id();

        assertThrows(
                RefusalResponse.class,
                () -> service.deleteFederatedComment(stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_2));
    }

    @Test
    @Order(82)
    void deleteFederatedCommentRemote() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/300"),
                        eq(new RemoteEventRoutes.RemoteCommentDeleteRequest(REMOTE_MEMBER_1)),
                        any(),
                        eq(stationA.id())))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.deleteFederatedComment(stationA.id(), stationC.uid(), 300, REMOTE_MEMBER_1));
    }

    @Test
    @Order(83)
    void deleteFederatedCommentRemoteFails() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/301"),
                        any(),
                        any(),
                        eq(stationA.id())))
                .thenReturn(false);

        assertThrows(
                RefusalResponse.class,
                () -> service.deleteFederatedComment(stationA.id(), stationC.uid(), 301, REMOTE_MEMBER_1));
    }

    @Test
    @Order(84)
    void findMyRegistrationsLocal() {
        var regs = service.findMyRegistrations(stationA.id(), List.of(REMOTE_MEMBER_1));
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
    }

    @Test
    @Order(84)
    void findMyRegistrationsEmpty() {
        var regs = service.findMyRegistrations(stationA.id(), List.of(UUID.randomUUID()));
        assertNotNull(regs);
    }

    @Test
    @Order(85)
    void getFederatedEventUnknownPartner() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getFederatedEvent(stationA.id(), UUID.randomUUID(), eventId));
    }

    @Test
    @Order(90)
    void browseFederatedEventsAsksAPartnerElsewhere() {
        var remoteEvent = new SharedEvent(
                1, "Test Event", "desc", StationEvent.EventType.ONE_TIME, 0, "10:00", "12:00", true, false, null, null);
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        eq(stationC.uid()),
                        eq(stationA.id()),
                        eq(SharedEvent.class)))
                .thenReturn(List.of(remoteEvent));

        var result = service.browseFederatedEvents(stationA.id());
        assertTrue(result.stream().anyMatch(item -> item.event().equals(remoteEvent)));
    }

    /**
     * What the other station recorded is carried back rather than assumed. An appointment that asks
     * for no confirmation accepts at once, and telling our own member they are waiting would be
     * telling them something nobody said.
     */
    @Test
    @Order(91)
    void registerForFederatedEvent() {
        var accepted = new EventFederationRegistration(
                1, 1, 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1), RegistrationStatus.ACCEPTED, Instant.now());
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/1/register"),
                        any(),
                        eq(stationC.uid()),
                        eq(stationA.id()),
                        eq(EventFederationRegistration.class)))
                .thenReturn(accepted);

        var status = service.registerForFederatedEvent(
                stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1));
        assertEquals(RegistrationStatus.ACCEPTED, status);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.registerForFederatedEvent(
                        stationA.id(), stationC.uid(), 2, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
        assertEquals(Refusal.FEDERATED_REGISTRATION_NOT_TAKEN, refused.refusal());
    }

    @Test
    @Order(92)
    void withdrawFederatedRegistration() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/1/register"),
                        any(),
                        eq(stationC.uid()),
                        eq(stationA.id())))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.withdrawFederatedRegistration(
                stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
        assertDoesNotThrow(() -> service.withdrawFederatedRegistration(
                stationA.id(), stationC.uid(), 2, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
    }

    /** A partner elsewhere that does not answer a confirmation or an undo is told in this station's words. */
    @Test
    @Order(93)
    void aPartnerElsewhereThatDoesNotAnswerIsNamedInTheFeaturesWords() {
        var confirm = assertThrows(
                RefusalResponse.class,
                () -> service.confirmOwnFederatedMember(
                        stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
        assertEquals(Refusal.NO_PLACES_LEFT_AT_HOLDER, confirm.refusal());
        var undo = assertThrows(
                RefusalResponse.class,
                () -> service.undoFederatedWithdrawal(
                        stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
        assertEquals(Refusal.FEDERATED_WITHDRAWAL_NO_LONGER_UNDONE, undo.refusal());
    }

    @Test
    @Order(97)
    void whatOneStationShowsAnother() {
        var event = new SharedEvent(
                1, "Name", "Description", StationEvent.EventType.RECURRING, 3, "09:00", "11:00", false, true, null, 8);
        assertEquals(1, event.id());
        assertEquals("Name", event.name());
        assertEquals("Description", event.description());
        assertEquals(StationEvent.EventType.RECURRING, event.eventType());
        assertEquals(3, event.dayOfWeek());
        assertEquals("09:00", event.startTime());
        assertEquals("11:00", event.endTime());
        assertFalse(event.requiresRegistration());
        assertTrue(event.requiresConfirmation());
        assertEquals(8, event.repeatCount());
    }

    /** A series that ends says so to a partner as well, or it repeats for ever on their screen. */
    @Test
    @Order(98)
    void aSharedSeriesCarriesItsEnd() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        var series = crudService.create(
                stationA.id(),
                "Geteilte Reihe",
                "desc",
                StationEvent.EventType.RECURRING,
                start.atZone(ZoneOffset.UTC).getDayOfWeek().getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var ends = crudService.setRepeatEnd(series.id(), null, 4).orElseThrow();
        var shared = SharedEvent.of(ends);

        assertEquals(4, shared.repeatCount());
        assertNull(shared.repeatUntil());
    }

    /**
     * A partner station is handed the open files of a shared event and never what it keeps back.
     *
     * <p>Internal means kept back from the room, and a partner is further out than the room. The
     * list omits such a file, and asking for it by its own id is refused at the source rather than
     * answered differently from the list.
     */
    @Test
    @Order(99)
    void aPartnerIsHandedTheOpenFilesAndNeverTheInternalOnes() {
        var stationFile = mediaFileRepo.create(
                null, stationA.id(), UUID.randomUUID().toString(), "laufzettel.pdf", "application/pdf", 12);
        var kept = mediaFileRepo.create(
                null, stationA.id(), UUID.randomUUID().toString(), "einsatzplan.pdf", "application/pdf", 12);
        when(media.findFile(stationFile.id())).thenReturn(Optional.of(stationFile));
        when(media.findFile(kept.id())).thenReturn(Optional.of(kept));
        when(media.read(stationA.id(), stationFile.contentHash()))
                .thenReturn(Optional.of(
                        new MediaContent("Laufzettel".getBytes(StandardCharsets.UTF_8), "application/pdf")));

        var open = attachmentService.attach(eventId, stationA.id(), stationFile.id(), "Laufzettel", false);
        var internal = attachmentService.attach(eventId, stationA.id(), kept.id(), "Einsatzplan", true);
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());

        try {
            var listed = service.listFederatedAttachments(stationB.id(), stationA.uid(), eventId);
            assertEquals(1, listed.size(), "the partner is handed one of the two");
            assertEquals("Laufzettel", listed.getFirst().name());

            var handed = service.getFederatedAttachment(stationB.id(), stationA.uid(), eventId, open.id());
            assertEquals(open.id(), handed.attachmentId());
            assertEquals(
                    "Laufzettel",
                    new String(Base64.getDecoder().decode(handed.base64()), StandardCharsets.UTF_8),
                    "the bytes travel as the channel can carry them");

            assertThrows(
                    Exception.class,
                    () -> service.getFederatedAttachment(stationB.id(), stationA.uid(), eventId, internal.id()),
                    "what is kept back is refused even when its id is named");

            service.removeShare(eventId);
            assertThrows(
                    Exception.class,
                    () -> service.listFederatedAttachments(stationB.id(), stationA.uid(), eventId),
                    "an event that is no longer shared hands over nothing");
        } finally {
            attachmentService.detach(open.id());
            attachmentService.detach(internal.id());
        }
    }

    /**
     * A partner on this instance goes through the same doors as one elsewhere: it signs up, gives the
     * place back, takes the withdrawal back and confirms its own member only where it was handed that
     * decision, and every refusal names what was refused.
     */
    @Test
    @Order(94)
    void aPartnerHereSignsUpAndChangesItsMindThroughTheSameDoors() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var member = UUID.randomUUID();
        var day = LocalDate.now().plusDays(1);
        var asking = partnerOf(stationB, stationA);
        int serving = partnerOf(stationA, stationB).id();

        assertNotNull(service.registerForFederatedEvent(stationB.id(), stationA.uid(), eventId, member, day));
        transport.assertParity(
                asking, RemoteEventRoutes.LIST_REGISTRATIONS.at(eventId), null, EventFederationRegistration.class);
        transport.assertParity(
                asking,
                RemoteEventRoutes.LIST_MEMBER_REGISTRATIONS.at(member),
                null,
                RemoteEventRoutes.RemoteMemberRegistration.class);

        service.withdrawFederatedRegistration(stationB.id(), stationA.uid(), eventId, member, day);
        service.undoFederatedWithdrawal(stationB.id(), stationA.uid(), eventId, member, day);
        assertTrue(service.findRegistration(eventId, serving, member, day)
                .orElseThrow()
                .isStanding());

        assertRefused(
                Refusal.PARTNER_DOES_NOT_CONFIRM_ITS_OWN,
                () -> service.confirmOwnFederatedMember(stationB.id(), stationA.uid(), eventId, member, day));
        service.setPartnerPlaces(eventId, serving, 1, true);
        var second = UUID.randomUUID();
        assertEquals(
                RegistrationStatus.PENDING,
                service.registerForFederatedEvent(stationB.id(), stationA.uid(), eventId, second, day));
        assertRefused(
                Refusal.NO_PLACES_LEFT_FOR_PARTNER,
                () -> service.confirmOwnFederatedMember(stationB.id(), stationA.uid(), eventId, second, day));
        service.setPartnerPlaces(eventId, serving, 2, true);
        assertDoesNotThrow(
                () -> service.confirmOwnFederatedMember(stationB.id(), stationA.uid(), eventId, second, day));
        service.setPartnerPlaces(eventId, serving, null, false);

        var stranger = UUID.randomUUID();
        service.withdrawFederatedRegistration(stationB.id(), stationA.uid(), eventId, stranger, day);
        assertRefused(
                Refusal.PARTNER_WITHDRAWAL_NO_LONGER_UNDONE,
                () -> service.undoFederatedWithdrawal(stationB.id(), stationA.uid(), eventId, stranger, day));
    }

    /** What a partner here sends that cannot be taken is refused in the words of what went wrong. */
    @Test
    @Order(95)
    void aPartnerHereIsToldWhatWasWrongWithItsComment() {
        service.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var asking = partnerOf(stationB, stationA);
        var member = UUID.randomUUID();

        assertRefused(
                Refusal.PARTNER_COMMENT_NEEDS_TEXT,
                () -> service.createFederatedComment(
                        stationB.id(), stationA.uid(), eventId, member, "Visitor", null, " ", null));
        assertRefused(Refusal.PARTNER_COMMENT_DAY_NOT_A_DATE, () -> transport
                .transport()
                .send(
                        asking,
                        RemoteEventRoutes.CREATE_COMMENT.at(eventId),
                        new RemoteEventRoutes.RemoteCommentRequest(member, "Visitor", null, "Hi", "someday"),
                        CommentResponse.class));
        var comment = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, member, "Visitor", null, "Hi", null);
        assertRefused(
                Refusal.PARTNER_COMMENT_CHANGE_NEEDS_TEXT,
                () -> service.updateFederatedComment(stationB.id(), stationA.uid(), comment.id(), member, " "));
        assertEquals(
                "ok",
                transport
                        .transport()
                        .send(
                                asking,
                                RemoteEventRoutes.REGISTRATION_STATUS_WEBHOOK.at(),
                                null,
                                FederatedEventRoutes.StatusResponse.class)
                        .status());
    }

    private static void assertRefused(Refusal expected, Executable call) {
        assertEquals(expected, assertThrows(RefusalResponse.class, call).refusal());
    }

    /** The given station's own partner row for the other one. */
    private static FederationPartner partnerOf(Station station, Station other) {
        return federationRepo
                .findPartnerByStationAndRemoteUid(station.id(), other.uid())
                .orElseThrow();
    }
}
