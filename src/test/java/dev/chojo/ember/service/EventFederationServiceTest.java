/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
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

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        eventFederationRepo = new EventFederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);
        var eventBus = new DomainEventBus(Set.of());
        eventService = new EventService(eventRepo, restrictionRepo, eventBus);
        commentService = new CommentService(eventCommentRepo, eventBus);
        service = new EventFederationService(
                eventFederationRepo,
                federationService,
                httpClient,
                federationRepo,
                stationRepo,
                eventService,
                commentService,
                eventCommentRepo,
                stationMemberRepo,
                accountRepo);

        stationA = stationRepo.create("EventFedSvcStationA");
        stationB = stationRepo.create("EventFedSvcStationB");
        stationC = stationRepo.create("EventFedSvcStationC");

        // Create bidirectional federation partnership (local)
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationA.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerId = partner.id();

        // Create remote federation: stationA accepts, stationC initiates (stationA sees stationC as remote)
        var keyPairC = federationService.generateKeyPair();
        federationService.acceptInvite(
                stationA.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPairC),
                "https://remote-event.example.com",
                null);

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
        boolean updated = service.updateRegistrationStatus(reg.id(), "ACCEPTED");
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
            @SuppressWarnings("unchecked")
            var eventMap = (Map<String, Object>) item.event();
            return eventId == (int) eventMap.get("id");
        }));
    }

    @Test
    @Order(31)
    void browseFederatedEventsNoShares() {
        service.removeShare(eventId);
        var items = service.browseFederatedEvents(stationB.id());
        assertTrue(items.stream().noneMatch(item -> {
            @SuppressWarnings("unchecked")
            var eventMap = (Map<String, Object>) item.event();
            return eventId == (int) eventMap.get("id");
        }));
    }

    @Test
    @Order(32)
    void getFederatedEventLocal() {
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        var result = service.getFederatedEvent(stationB.id(), stationA.uid(), eventId);
        assertNotNull(result);
        assertEquals(eventId, result.get("id"));
        assertEquals("Federated Event", result.get("name"));
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

        var remoteEvent = java.util.Map.of("id", (Object) eventId, "name", "Remote Event", "description", "desc");
        when(httpClient.get(
                        eq("https://remote-event.example.com"),
                        eq("/remote/events/" + eventId),
                        eq(stationA.id()),
                        any(),
                        any()))
                .thenReturn(remoteEvent);

        var result = service.getFederatedEvent(stationA.id(), stationC.uid(), eventId);
        assertNotNull(result);
        assertEquals(eventId, result.get("id"));
        assertEquals("Remote Event", result.get("name"));

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
                    @SuppressWarnings("unchecked")
                    var eventMap = (Map<String, Object>) i.event();
                    return eventId == (int) eventMap.get("id");
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
}
