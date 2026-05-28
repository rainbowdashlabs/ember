/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFederationServiceTest extends RepositoryTestBase {

    private static EventFederationService service;
    private static EventService eventService;
    private static FederationService federationService;
    private static FederationRepository federationRepo;
    private static EventFederationRepository eventFederationRepo;

    private static Station stationA;
    private static Station stationB;
    private static int partnerId;
    private static int eventId;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        eventFederationRepo = new EventFederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        var eventBus = new DomainEventBus(Set.of());
        eventService = new EventService(eventRepo, restrictionRepo, eventBus);
        service = new EventFederationService(
                eventFederationRepo, federationService, null, federationRepo, stationRepo, eventService);

        stationA = stationRepo.create("EventFedSvcStationA");
        stationB = stationRepo.create("EventFedSvcStationB");

        // Create bidirectional federation partnership
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationA.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerId = partner.id();

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
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
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
        var reg = service.registerFederated(eventId, partnerId, "remote-member-1", LocalDate.of(2026, 7, 1));
        assertNotNull(reg);
        assertEquals(eventId, reg.eventId());
        assertEquals(partnerId, reg.partnerId());
        assertEquals("remote-member-1", reg.remoteMemberId());
        assertEquals(LocalDate.of(2026, 7, 1), reg.eventDate());
        assertNotNull(reg.status());
    }

    @Test
    @Order(11)
    void findRegistrationById() {
        var reg = service.registerFederated(eventId, partnerId, "remote-member-2", LocalDate.of(2026, 7, 2));
        var found = service.findRegistrationById(reg.id());
        assertTrue(found.isPresent());
        assertEquals(reg.id(), found.get().id());
        assertEquals("remote-member-2", found.get().remoteMemberId());
    }

    @Test
    @Order(12)
    void findRegistrationByIdMissing() {
        assertTrue(service.findRegistrationById(999999).isEmpty());
    }

    @Test
    @Order(13)
    void updateRegistrationStatus() {
        var reg = service.registerFederated(eventId, partnerId, "remote-member-3", LocalDate.of(2026, 7, 3));
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
        assertTrue(regs.stream().anyMatch(r -> r.remoteMemberId().equals("remote-member-1")));
    }

    @Test
    @Order(15)
    void findRegistrationsByPartner() {
        var regs = service.findRegistrationsByPartner(partnerId);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().anyMatch(r -> r.remoteMemberId().equals("remote-member-1")));
    }

    @Test
    @Order(16)
    void withdrawRegistration() {
        service.registerFederated(eventId, partnerId, "to-withdraw", LocalDate.of(2026, 8, 1));
        boolean withdrawn = service.withdrawRegistration(eventId, partnerId, "to-withdraw", LocalDate.of(2026, 8, 1));
        assertTrue(withdrawn);
        assertTrue(service.findRegistrations(eventId, LocalDate.of(2026, 8, 1)).isEmpty());
    }

    // -- Name cache --

    @Test
    @Order(20)
    void cacheAndGetName() {
        service.cacheName(partnerId, "remote-member-1", "Alice Smith");
        var cached = service.getCachedName(partnerId, "remote-member-1");
        assertTrue(cached.isPresent());
        assertEquals("Alice Smith", cached.get());
    }

    @Test
    @Order(21)
    void getCachedNameMissing() {
        var cached = service.getCachedName(partnerId, "nonexistent-member");
        assertTrue(cached.isEmpty());
    }

    @Test
    @Order(22)
    void cacheNameUpdatesExisting() {
        service.cacheName(partnerId, "remote-member-1", "Alice Updated");
        var cached = service.getCachedName(partnerId, "remote-member-1");
        assertTrue(cached.isPresent());
        assertEquals("Alice Updated", cached.get());
    }

    @Test
    @Order(23)
    void invalidateName() {
        service.cacheName(partnerId, "to-invalidate", "Bob");
        service.invalidateName(partnerId, "to-invalidate");
        assertTrue(service.getCachedName(partnerId, "to-invalidate").isEmpty());
    }

    // -- Federated browsing / get --

    @Test
    @Order(30)
    void browseFederatedEventsWithShare() {
        service.setShare(eventId, "ALL_PARTNERS", List.of());
        var items = service.browseFederatedEvents(stationB.id());
        assertFalse(items.isEmpty(), "Should find shared events from stationA when browsing from stationB");
        assertTrue(
                items.stream().anyMatch(item -> {
                    @SuppressWarnings("unchecked")
                    var eventMap = (Map<String, Object>) item.event();
                    return eventId == (int) eventMap.get("id");
                }),
                "Should contain the shared event");
    }

    @Test
    @Order(31)
    void browseFederatedEventsNoShares() {
        service.removeShare(eventId);
        var items = service.browseFederatedEvents(stationB.id());
        assertTrue(
                items.stream().noneMatch(item -> {
                    @SuppressWarnings("unchecked")
                    var eventMap = (Map<String, Object>) item.event();
                    return eventId == (int) eventMap.get("id");
                }),
                "Should not find the event when share is removed");
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
        service.removeShare(eventId);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getFederatedEvent(stationB.id(), stationA.uid(), eventId));
    }

    @Test
    @Order(34)
    void federatedEventItemRecord() {
        var item = new EventFederationService.FederatedEventItem(42, "TestStation", Map.of("id", 1));
        assertEquals(42, item.partnerId());
        assertEquals("TestStation", item.partnerStationName());
        assertEquals(Map.of("id", 1), item.event());
    }
}
