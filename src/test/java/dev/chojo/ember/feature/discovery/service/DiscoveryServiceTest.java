/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiscoveryServiceTest extends RepositoryTestBase {

    private static FederationService federationService;
    private static FederationRepository federationRepo;
    private static Station stationA;
    private static Station stationB;
    private static Station stationC;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());

        stationA = stationRepo.create("DiscTestStationA");
        stationB = stationRepo.create("DiscTestStationB");
        stationC = stationRepo.create("DiscTestStationC");

        // Set visibility
        stationRepo.updateDiscoverySettings(stationA.id(), DiscoveryVisibility.PUBLIC, "Station A description", true);
        stationRepo.updateDiscoverySettings(
                stationB.id(), DiscoveryVisibility.INSTANCE, "Station B description", false);
        // stationC stays NONE (default)
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
    void discoverableExcludesNoneVisibility() {
        var discoverable = stationRepo.findDiscoverable(0, DiscoveryVisibility.INSTANCE, DiscoveryVisibility.PUBLIC);
        assertTrue(discoverable.stream().anyMatch(s -> s.id() == stationA.id()));
        assertTrue(discoverable.stream().anyMatch(s -> s.id() == stationB.id()));
        assertFalse(discoverable.stream().anyMatch(s -> s.id() == stationC.id()));
    }

    @Test
    @Order(2)
    void discoverableExcludesOwnStation() {
        var discoverable =
                stationRepo.findDiscoverable(stationA.id(), DiscoveryVisibility.INSTANCE, DiscoveryVisibility.PUBLIC);
        assertFalse(discoverable.stream().anyMatch(s -> s.id() == stationA.id()));
        assertTrue(discoverable.stream().anyMatch(s -> s.id() == stationB.id()));
    }

    @Test
    @Order(3)
    void pairingCodeIsDeterministic() {
        var code1 = federationService.generatePairingCode(stationA.uid());
        var code2 = federationService.generatePairingCode(stationA.uid());
        assertEquals(code1, code2);
    }

    @Test
    @Order(4)
    void pairingCodeStartsWithEmber() {
        var code = federationService.generatePairingCode(stationA.uid());
        assertTrue(code.startsWith("ember-"));
    }

    @Test
    @Order(5)
    void pairingCodeRoundTrips() {
        var code = federationService.generatePairingCode(stationA.uid());
        var parts = federationService.parsePairingCode(code);
        assertTrue(parts.isPresent());
        assertEquals(stationA.uid(), parts.get().stationUid());
        assertFalse(parts.get().isStationInvite());
    }

    @Test
    @Order(6)
    void stationInviteCodeRoundTrips() {
        var code = federationService.generateStationInvite(stationA.id(), stationA.uid());
        var parts = federationService.parsePairingCode(code);
        assertTrue(parts.isPresent());
        assertEquals(stationA.uid(), parts.get().stationUid());
        assertTrue(parts.get().isStationInvite());
        assertNotNull(parts.get().token());
    }

    @Test
    @Order(7)
    void stationInviteTokenCanBeConsumed() {
        var code = federationService.generateStationInvite(stationB.id(), stationB.uid());
        var parts = federationService.parsePairingCode(code).orElseThrow();
        assertTrue(federationService.consumeInviteToken(stationB.id(), parts.token()));
        // Second consume should fail (token already used)
        assertFalse(federationService.consumeInviteToken(stationB.id(), parts.token()));
    }

    @Test
    @Order(8)
    void invalidPairingCodeReturnsEmpty() {
        assertTrue(federationService.parsePairingCode("invalid").isEmpty());
        assertTrue(federationService.parsePairingCode("ember-").isEmpty());
        assertTrue(federationService.parsePairingCode("").isEmpty());
    }

    @Test
    @Order(10)
    void createPairRequest() {
        var partner = federationService.createPairRequest(stationA.id(), stationB.id());
        assertNotNull(partner);
        assertEquals(stationA.id(), partner.stationId());
        assertEquals(stationB.uid(), partner.partnerStationId());
        assertEquals("PENDING", partner.status().name());
    }

    @Test
    @Order(11)
    void findPendingRequests() {
        var requests = federationService.findPendingRequests(stationB.id());
        assertFalse(requests.isEmpty());
        assertTrue(requests.stream().anyMatch(p -> p.stationId() == stationA.id()));
    }

    @Test
    @Order(12)
    void acceptPairRequestCreatesBidirectionalFederation() {
        var requests = federationService.findPendingRequests(stationB.id());
        var request = requests.stream()
                .filter(p -> p.stationId() == stationA.id())
                .findFirst()
                .orElseThrow();

        var result = federationService.acceptPairRequest(request.id());
        assertNotNull(result);

        // Both sides should have ACTIVE partners
        var partnersA = federationService.findPartners(stationA.id());
        assertTrue(partnersA.stream()
                .anyMatch(p -> p.partnerStationId().equals(stationB.uid())
                        && p.status().name().equals("ACTIVE")));

        var partnersB = federationService.findPartners(stationB.id());
        assertTrue(partnersB.stream()
                .anyMatch(p -> p.partnerStationId().equals(stationA.uid())
                        && p.status().name().equals("ACTIVE")));
    }

    @Test
    @Order(13)
    void declinePairRequest() {
        var partner = federationService.createPairRequest(stationC.id(), stationA.id());
        var requests = federationService.findPendingRequests(stationA.id());
        assertTrue(requests.stream().anyMatch(p -> p.stationId() == stationC.id()));

        federationService.declinePairRequest(partner.id());

        var requestsAfter = federationService.findPendingRequests(stationA.id());
        assertFalse(requestsAfter.stream().anyMatch(p -> p.stationId() == stationC.id()));
    }
}
