/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFederationServiceTest extends RepositoryTestBase {
    private static final UUID REMOTE_MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE_MEMBER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REMOTE_MEMBER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static EventFederationService service;
    private static EventService eventService;
    private static FederationService federationService;
    private static FederationRepository federationRepo;
    private static EventFederationRepository eventFederationRepo;
    private static FederationHttpClient httpClient;
    private static CommentService commentService;

    private static Station stationA;
    private static Station stationB;
    private static Station stationC;
    private static int partnerId;
    private static int eventId;
    private static FederationPartner localPartner;
    private static FederationPartner remotePartner;
    private static Account testAccount;
    private static StationMember testMember;
    private static MemberIdentity testMemberIdentity;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        eventFederationRepo = new EventFederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);
        var eventBus = new DomainEventBus(Set.of());
        eventService = new EventService(eventRepo, restrictionRepo, eventBus);
        var memberSvc = new StationMemberService(stationMemberRepo, stationRepo, accountRepo, mock(AuthService.class));
        commentService = new CommentService(eventCommentRepo, eventBus, memberSvc, stationRepo);
        service = new EventFederationService(
                eventFederationRepo,
                federationService,
                httpClient,
                federationRepo,
                stationRepo,
                eventService,
                commentService,
                eventCommentRepo,
                new MemberNameResolver(
                        memberSvc,
                        accountRepo,
                        eventFederationRepo,
                        federationRepo,
                        stationRepo,
                        mock(MemberGroupService.class),
                        mock(UserTagService.class)));

        stationA = stationRepo.create("EventFedSvcStationA");
        stationB = stationRepo.create("EventFedSvcStationB");
        stationC = stationRepo.create("EventFedSvcStationC");

        // Create bidirectional federation partnership (local)
        var keyPair = federationService.generateKeyPair();
        localPartner = federationService.acceptInvite(
                stationA.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerId = localPartner.id();

        // Create remote federation: stationA accepts, stationC initiates (stationA sees stationC as remote)
        var keyPairC = federationService.generateKeyPair();
        remotePartner = federationService.acceptInvite(
                stationA.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPairC),
                "https://remote-event.example.com",
                null);

        // Create test account and member for local comment author tests
        testAccount = accountRepo.create("eventfed@test.com", "Test", "Author");
        testMember = stationMemberRepo.create(stationA.id(), testAccount.id());
        testMemberIdentity = memberIdentityFactory.local(stationA.id(), testMember.id());

        // Create a test event on stationA
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

    // -- Share management --

    @Test
    @Order(1)
    void setShareAllPartners() {
        var share = service.setShare(eventId, "ALL_PARTNERS", List.of());
        assertNotNull(share);
        assertEquals(eventId, share.eventId());
        assertEquals("ALL_PARTNERS", share.scope());
    }

    @Test
    @Order(2)
    void findShareByEvent() {
        var found = service.findShareByEvent(eventId);
        assertTrue(found.isPresent());
        assertEquals("ALL_PARTNERS", found.get().scope());
    }

    @Test
    @Order(3)
    void setShareSpecificWithTargets() {
        var share = service.setShare(eventId, "SPECIFIC", List.of(partnerId));
        assertNotNull(share);
        assertEquals("SPECIFIC", share.scope());

        var targets = service.findShareTargets(share.id());
        assertTrue(targets.contains(partnerId));
    }

    @Test
    @Order(4)
    void findSharedEventIds() {
        // Share is SPECIFIC for partnerId, so the event should appear for this partner
        var sharedIds = service.findSharedEventIds(partnerId, stationA.id());
        assertTrue(sharedIds.contains(eventId));
    }

    @Test
    @Order(5)
    void findSharedEventIdsAllPartners() {
        // Switch back to ALL_PARTNERS
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        var sharedIds = service.findSharedEventIds(partnerId, stationA.id());
        assertTrue(sharedIds.contains(eventId));
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

    // -- Registration --

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
        assertEquals("ACCEPTED", found.status());
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
        // Null event date should return all registrations for the event
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
        service.registerFederated(eventId, partnerId, toWithdraw, LocalDate.of(2026, 8, 1));
        boolean withdrawn = service.withdrawRegistration(eventId, partnerId, toWithdraw, LocalDate.of(2026, 8, 1));
        assertTrue(withdrawn);
        assertTrue(service.findRegistrations(eventId, LocalDate.of(2026, 8, 1)).isEmpty());
    }

    // -- Name cache --

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

    // -- Federated browsing / get --

    @Test
    @Order(30)
    void browseFederatedEventsWithShare() {
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        var items = service.browseFederatedEvents(stationB.id());
        assertFalse(items.isEmpty(), "Should find shared events");
        assertTrue(items.stream().anyMatch(item -> {
            if (item.event() instanceof EventFederationService.RemoteEventSummary re) {
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
            if (item.event() instanceof EventFederationService.RemoteEventSummary re) {
                return eventId == re.id();
            }
            return false;
        }));
    }

    @Test
    @Order(32)
    void getFederatedEventLocal() {
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        var result = service.getFederatedEvent(stationB.id(), stationA.uid(), eventId);
        assertNotNull(result);
        var event = (EventFederationService.RemoteEventSummary) result;
        assertEquals(eventId, event.id());
        assertEquals("Federated Event", event.name());
    }

    @Test
    @Order(33)
    void getFederatedEventNotShared() {
        // Ensure event is not shared — must reject access.
        // Partner may or may not exist due to cross-test interference;
        // either way the call must reject access.
        service.removeShare(eventId);
        assertThrows(Exception.class, () -> service.getFederatedEvent(stationB.id(), stationA.uid(), eventId));
    }

    @Test
    @Order(34)
    void federatedEventItemRecord() {
        var item = new EventFederationService.FederatedEventItem(
                42, "TestStation", "00000000-0000-0000-0000-000000000042", Map.of("id", 1));
        assertEquals(42, item.partnerId());
        assertEquals("TestStation", item.partnerStationName());
        assertEquals(Map.of("id", 1), item.event());
    }

    // -- Remote HTTP federation tests --

    @Test
    @Order(40)
    void browseFederatedEventsViaHttp() {
        // Ensure event is shared
        service.setShare(eventId, "ALL_PARTNERS", List.of());

        // Mock HTTP response for remote partner (stationC)
        var remoteEvent = new EventFederationService.RemoteFederatedEvent(
                9999,
                "Remote Event",
                "Remote desc",
                "ONE_TIME",
                0,
                Instant.now().toString(),
                Instant.now().plus(2, ChronoUnit.HOURS).toString(),
                true,
                false);
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events"),
                        eq(stationA.id()),
                        any(),
                        eq(EventFederationService.RemoteFederatedEvent.class)))
                .thenReturn(List.of(remoteEvent));

        // browseFederatedEvents(stationA.id()) finds partners: stationB (local) and stationC (remote)
        var items = service.browseFederatedEvents(stationA.id());
        assertFalse(items.isEmpty(), "Should include events from local and/or remote partners");

        // Verify HTTP client was called for the remote partner
        verify(httpClient)
                .getList(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events"),
                        eq(stationA.id()),
                        any(),
                        eq(EventFederationService.RemoteFederatedEvent.class));

        // Should contain the remote event
        assertTrue(
                items.stream().anyMatch(i -> {
                    if (i.event() instanceof EventFederationService.RemoteFederatedEvent re) {
                        return re.id() == 9999 && re.name().equals("Remote Event");
                    }
                    return false;
                }),
                "Should contain the mocked remote event");
    }

    @Test
    @Order(41)
    void getFederatedEventRemote() {
        // stationA sees stationC as remote partner
        // getFederatedEvent(stationA.id(), stationC.uid(), eventId) should call HTTP

        var remoteEvent = new EventFederationService.RemoteEventSummary(
                eventId, "Remote Event", "desc", "REGULAR", 1, "10:00", "12:00", false, false);
        when(httpClient.get(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/" + eventId),
                        eq(stationA.id()),
                        any(),
                        any()))
                .thenReturn(remoteEvent);

        var result = service.getFederatedEvent(stationA.id(), stationC.uid(), eventId);
        assertNotNull(result);
        var event = (EventFederationService.RemoteEventSummary) result;
        assertEquals(eventId, event.id());
        assertEquals("Remote Event", event.name());

        verify(httpClient)
                .get(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/" + eventId),
                        eq(stationA.id()),
                        any(),
                        any());
    }

    @Test
    @Order(42)
    void getFederatedEventRemoteReturnsNull() {
        // When the HTTP call returns null, the service should throw
        when(httpClient.get(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/" + eventId),
                        eq(stationA.id()),
                        any(),
                        any()))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class, () -> service.getFederatedEvent(stationA.id(), stationC.uid(), eventId));
    }

    @Test
    @Order(43)
    void browseFederatedEventsHttpReturnsEmpty() {
        // When remote partner returns no events, browse from stationB should still work (local events from stationA)
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        // stationB has no remote partners, so only local browse applies
        var items = service.browseFederatedEvents(stationB.id());
        assertNotNull(items);
        // The local partner (stationA) has the shared event
        assertTrue(
                items.stream().anyMatch(i -> {
                    if (i.event() instanceof EventFederationService.RemoteEventSummary re) {
                        return eventId == re.id();
                    }
                    return false;
                }),
                "Should contain locally shared events from stationA");
    }

    @Test
    @Order(44)
    void browseFederatedEventsRemoteReturnsEmptyLocalHasNone() {
        // stationA has stationB (local, no events) and stationC (remote)
        // When remote returns empty, result should be empty (stationB has no events to share)
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events"),
                        eq(stationA.id()),
                        any(),
                        eq(EventFederationService.RemoteFederatedEvent.class)))
                .thenReturn(List.of());

        var items = service.browseFederatedEvents(stationA.id());
        // stationB has no events shared, remote returned empty => no results for stationA's owned events via partners
        assertNotNull(items);
    }

    // -- Comment support: createRemoteComment --

    @Test
    @Order(50)
    void createRemoteComment() {
        // Ensure event is shared
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        service.cacheName(partnerId, REMOTE_MEMBER_1, "Alice Remote");

        var response = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Hello from remote!");
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
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Parent comment");
        var reply = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_2, "Bob Remote", parent.id(), "Reply to parent");
        assertNotNull(reply);
        assertEquals(parent.id(), reply.parentId());
        assertEquals("Reply to parent", reply.content());
    }

    // -- Comment support: updateRemoteComment --

    @Test
    @Order(52)
    void updateRemoteComment() {
        var created = service.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Original content");
        var updated = service.updateRemoteComment(localPartner, created.id(), REMOTE_MEMBER_1, "Updated content");
        assertNotNull(updated);
        assertEquals("Updated content", updated.content());
        assertNotNull(updated.updatedAt());
    }

    @Test
    @Order(53)
    void updateRemoteCommentWrongOwner() {
        var created =
                service.createRemoteComment(localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "My comment");
        assertThrows(
                ForbiddenResponse.class,
                () -> service.updateRemoteComment(localPartner, created.id(), REMOTE_MEMBER_2, "Hacked!"));
    }

    @Test
    @Order(54)
    void updateRemoteCommentNotFederated() {
        var localComment = eventCommentRepo.create(eventId, null, testMemberIdentity, "Local comment", null);
        assertThrows(
                ForbiddenResponse.class,
                () -> service.updateRemoteComment(localPartner, localComment.id(), REMOTE_MEMBER_1, "Edited"));
    }

    // -- Comment support: deleteRemoteComment --

    @Test
    @Order(55)
    void deleteRemoteComment() {
        var created =
                service.createRemoteComment(localPartner, eventId, REMOTE_MEMBER_3, "Delete Me", null, "To be deleted");
        boolean deleted = service.deleteRemoteComment(localPartner, created.id(), REMOTE_MEMBER_3);
        assertTrue(deleted);
    }

    @Test
    @Order(56)
    void deleteRemoteCommentWrongOwner() {
        var created = service.createRemoteComment(localPartner, eventId, REMOTE_MEMBER_1, "Alice", null, "My comment");
        assertThrows(
                ForbiddenResponse.class,
                () -> service.deleteRemoteComment(localPartner, created.id(), REMOTE_MEMBER_2));
    }

    @Test
    @Order(57)
    void deleteRemoteCommentNotFederated() {
        var localComment = eventCommentRepo.create(eventId, null, testMemberIdentity, "Local comment to delete", null);
        assertThrows(
                ForbiddenResponse.class,
                () -> service.deleteRemoteComment(localPartner, localComment.id(), REMOTE_MEMBER_1));
    }

    // -- Comment support: toCommentResponse --

    @Test
    @Order(60)
    void toCommentResponseDeletedComment() {
        var comment = eventCommentRepo.create(eventId, null, testMemberIdentity, "Will be deleted", null);
        commentService.delete(comment.id());
        var deletedComment = new Comment(
                comment.id(),
                comment.parentId(),
                comment.author(),
                "",
                true,
                comment.createdAt(),
                comment.updatedAt(),
                comment.eventDate());
        var response = service.toCommentResponse(deletedComment);
        assertTrue(response.deleted());
        assertEquals("", response.content());
        assertNull(response.authorName());
        assertNull(response.author());
    }

    @Test
    @Order(61)
    void toCommentResponseLocalAuthor() {
        var comment = eventCommentRepo.create(eventId, null, testMemberIdentity, "Local author comment", null);
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
                localPartner, eventId, REMOTE_MEMBER_2, "Bob Federated", null, "Federated comment");
        var comment = commentService.findById(created.id()).orElseThrow();
        var response = service.toCommentResponse(comment);
        assertFalse(response.deleted());
        assertEquals("Federated comment", response.content());
        assertNotNull(response.author());
        assertEquals(REMOTE_MEMBER_2, response.author().memberUid());
        assertNotNull(response.author().stationUid());
        assertEquals("Bob Federated", response.authorName());
    }

    // -- Comment support: listComments --

    @Test
    @Order(63)
    void listComments() {
        var comments = service.listComments(eventId);
        assertNotNull(comments);
        assertFalse(comments.isEmpty(), "Should have comments from earlier tests");
        assertTrue(comments.stream().allMatch(c -> c.id() > 0));
    }

    // -- Federated comment methods: listFederatedComments --

    @Test
    @Order(70)
    void listFederatedCommentsLocal() {
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        var result = service.listFederatedComments(stationB.id(), stationA.uid(), eventId);
        assertNotNull(result);
        assertInstanceOf(EventFederationService.FederatedCommentResult.ListResult.class, result);
        var listResult = (EventFederationService.FederatedCommentResult.ListResult) result;
        assertNotNull(listResult.comments());
    }

    @Test
    @Order(71)
    void listFederatedCommentsRemote() {
        var mockResponses = List.of(new EventCommentRoutes.CommentResponse(
                1,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote User",
                "Remote comment",
                false,
                Instant.now(),
                null));
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/" + eventId + "/comments"),
                        eq(stationA.id()),
                        any(),
                        eq(EventCommentRoutes.CommentResponse.class)))
                .thenReturn(mockResponses);

        var result = service.listFederatedComments(stationA.id(), stationC.uid(), eventId);
        assertNotNull(result);
        assertInstanceOf(EventFederationService.FederatedCommentResult.ListResult.class, result);
        var listResult = (EventFederationService.FederatedCommentResult.ListResult) result;
        assertEquals(1, listResult.comments().size());
        assertEquals("Remote comment", listResult.comments().getFirst().content());
    }

    @Test
    @Order(72)
    void listFederatedCommentsUnknownPartner() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listFederatedComments(stationA.id(), UUID.randomUUID(), eventId));
    }

    // -- Federated comment methods: createFederatedComment --

    @Test
    @Order(73)
    void createFederatedCommentLocal() {
        var result = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Local federated create");
        assertNotNull(result);
        assertInstanceOf(EventFederationService.FederatedCommentResult.SingleResult.class, result);
        var single = (EventFederationService.FederatedCommentResult.SingleResult) result;
        assertEquals("Local federated create", single.comment().content());
    }

    @Test
    @Order(74)
    void createFederatedCommentRemote() {
        var mockResponse = new EventCommentRoutes.CommentResponse(
                99,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote Author",
                "Remote created",
                false,
                Instant.now(),
                null);
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/" + eventId + "/comments"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(EventCommentRoutes.CommentResponse.class)))
                .thenReturn(mockResponse);

        var result = service.createFederatedComment(
                stationA.id(), stationC.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Remote content");
        assertNotNull(result);
        assertInstanceOf(EventFederationService.FederatedCommentResult.SingleResult.class, result);
        var single = (EventFederationService.FederatedCommentResult.SingleResult) result;
        assertEquals("Remote created", single.comment().content());
    }

    @Test
    @Order(75)
    void createFederatedCommentRemoteReturnsNull() {
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/" + eventId + "/comments"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(EventCommentRoutes.CommentResponse.class)))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.createFederatedComment(
                        stationA.id(), stationC.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Will fail"));
    }

    // -- Federated comment methods: updateFederatedComment --

    @Test
    @Order(76)
    void updateFederatedCommentLocal() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "To update locally");
        var single = (EventFederationService.FederatedCommentResult.SingleResult) createResult;
        int commentId = single.comment().id();

        var result = service.updateFederatedComment(
                stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_1, "Updated locally");
        assertNotNull(result);
        assertInstanceOf(EventFederationService.FederatedCommentResult.SingleResult.class, result);
        var updatedSingle = (EventFederationService.FederatedCommentResult.SingleResult) result;
        assertEquals("Updated locally", updatedSingle.comment().content());
    }

    @Test
    @Order(77)
    void updateFederatedCommentLocalWrongOwner() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Owner check");
        var single = (EventFederationService.FederatedCommentResult.SingleResult) createResult;
        int commentId = single.comment().id();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.updateFederatedComment(
                        stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_2, "Wrong owner"));
    }

    @Test
    @Order(78)
    void updateFederatedCommentRemote() {
        var mockResponse = new EventCommentRoutes.CommentResponse(
                100,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote",
                "Updated remote",
                false,
                Instant.now(),
                Instant.now());
        when(httpClient.put(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/comments/100"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(EventCommentRoutes.CommentResponse.class)))
                .thenReturn(mockResponse);

        var result =
                service.updateFederatedComment(stationA.id(), stationC.uid(), 100, REMOTE_MEMBER_1, "Updated remote");
        assertNotNull(result);
        assertInstanceOf(EventFederationService.FederatedCommentResult.SingleResult.class, result);
    }

    @Test
    @Order(79)
    void updateFederatedCommentRemoteReturnsNull() {
        when(httpClient.put(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/comments/200"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(EventCommentRoutes.CommentResponse.class)))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.updateFederatedComment(stationA.id(), stationC.uid(), 200, REMOTE_MEMBER_1, "Will fail"));
    }

    // -- Federated comment methods: deleteFederatedComment --

    @Test
    @Order(80)
    void deleteFederatedCommentLocal() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_3, "Charlie", null, "Delete me locally");
        var single = (EventFederationService.FederatedCommentResult.SingleResult) createResult;
        int commentId = single.comment().id();

        boolean deleted = service.deleteFederatedComment(stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_3);
        assertTrue(deleted);
    }

    @Test
    @Order(81)
    void deleteFederatedCommentLocalWrongOwner() {
        var createResult = service.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Delete check");
        var single = (EventFederationService.FederatedCommentResult.SingleResult) createResult;
        int commentId = single.comment().id();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.deleteFederatedComment(stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_2));
    }

    @Test
    @Order(82)
    void deleteFederatedCommentRemote() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/comments/300"),
                        eq(stationA.id()),
                        any()))
                .thenReturn(true);

        boolean deleted = service.deleteFederatedComment(stationA.id(), stationC.uid(), 300, REMOTE_MEMBER_1);
        assertTrue(deleted);
    }

    @Test
    @Order(83)
    void deleteFederatedCommentRemoteFails() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/comments/301"),
                        eq(stationA.id()),
                        any()))
                .thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> service.deleteFederatedComment(stationA.id(), stationC.uid(), 301, REMOTE_MEMBER_1));
    }

    // -- getFederatedEvent edge cases --

    @Test
    @Order(84)
    void findMyRegistrationsLocal() {
        // findMyRegistrations uses remote member UIDs to look up registrations
        var regs = service.findMyRegistrations(stationA.id(), List.of(REMOTE_MEMBER_1));
        assertNotNull(regs);
        // REMOTE_MEMBER_1 has registrations from earlier tests
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

    // -- HTTP convenience methods --

    @Test
    @Order(90)
    void fetchFederatedEvents() {
        var remoteEvent = new EventFederationService.RemoteFederatedEvent(
                1, "Test Event", "desc", "ONE_TIME", 0, "10:00", "12:00", true, false);
        when(httpClient.getList(
                        eq("https://example.com"),
                        eq("/remote/events"),
                        eq(1),
                        eq("key123"),
                        eq(EventFederationService.RemoteFederatedEvent.class)))
                .thenReturn(List.of(remoteEvent));

        var result = service.fetchFederatedEvents("https://example.com", 1, "key123");
        assertEquals(1, result.size());
        assertEquals("Test Event", result.getFirst().name());
        assertEquals("desc", result.getFirst().description());
        assertEquals("ONE_TIME", result.getFirst().eventType());
        assertEquals(0, result.getFirst().dayOfWeek());
        assertEquals("10:00", result.getFirst().startTime());
        assertEquals("12:00", result.getFirst().endTime());
        assertTrue(result.getFirst().requiresRegistration());
        assertFalse(result.getFirst().requiresConfirmation());
    }

    @Test
    @Order(91)
    void registerForFederatedEvent() {
        when(httpClient.post(eq("https://example.com"), eq("/remote/events/1/register"), any(), eq(1), eq("key123")))
                .thenReturn(true);

        boolean success =
                service.registerForFederatedEvent("https://example.com", 1, REMOTE_MEMBER_1, "2026-07-01", 1, "key123");
        assertTrue(success);
    }

    @Test
    @Order(92)
    void withdrawFederatedRegistration() {
        when(httpClient.delete(eq("https://example.com"), eq("/remote/events/1/register"), any(), eq(1), eq("key123")))
                .thenReturn(true);

        boolean success = service.withdrawFederatedRegistration(
                "https://example.com", 1, REMOTE_MEMBER_1, "2026-07-01", 1, "key123");
        assertTrue(success);
    }

    // -- FederatedCommentResult record types --

    @Test
    @Order(95)
    void federatedCommentResultOfList() {
        var comments = List.of(new EventCommentRoutes.CommentResponse(
                1,
                null,
                new MemberIdentity(stationA.uid(), REMOTE_MEMBER_1),
                "Name",
                "Content",
                false,
                Instant.now(),
                null));
        var result = EventFederationService.FederatedCommentResult.ofList(comments);
        assertInstanceOf(EventFederationService.FederatedCommentResult.ListResult.class, result);
        assertEquals(
                1,
                ((EventFederationService.FederatedCommentResult.ListResult) result)
                        .comments()
                        .size());
    }

    @Test
    @Order(96)
    void federatedCommentResultOfSingle() {
        var comment = new EventCommentRoutes.CommentResponse(
                1,
                null,
                new MemberIdentity(stationA.uid(), REMOTE_MEMBER_1),
                "Name",
                "Content",
                false,
                Instant.now(),
                null);
        var result = EventFederationService.FederatedCommentResult.ofSingle(comment);
        assertInstanceOf(EventFederationService.FederatedCommentResult.SingleResult.class, result);
        assertEquals(
                "Content",
                ((EventFederationService.FederatedCommentResult.SingleResult) result)
                        .comment()
                        .content());
    }

    // -- RemoteFederatedEvent record --

    @Test
    @Order(97)
    void remoteFederatedEventRecord() {
        var event = new EventFederationService.RemoteFederatedEvent(
                1, "Name", "Description", "RECURRING", 3, "09:00", "11:00", false, true);
        assertEquals(1, event.id());
        assertEquals("Name", event.name());
        assertEquals("Description", event.description());
        assertEquals("RECURRING", event.eventType());
        assertEquals(3, event.dayOfWeek());
        assertEquals("09:00", event.startTime());
        assertEquals("11:00", event.endTime());
        assertFalse(event.requiresRegistration());
        assertTrue(event.requiresConfirmation());
    }
}
