/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
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

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederationServiceTest extends RepositoryTestBase {

    private static FederationService service;
    private static FederationRepository federationRepo;
    private static Station stationA;
    private static Station stationB;
    private static int partnerIdAtoB;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        service = new FederationService(federationRepo, stationRepo, new Api());

        stationA = stationRepo.create("FedSvcTestStationA");
        stationB = stationRepo.create("FedSvcTestStationB");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
    }

    @Test
    @Order(1)
    void generatePairingCodeFormat() {
        String code = service.generatePairingCode(stationA.uid());
        assertNotNull(code);
        assertTrue(code.startsWith("ember-"), "Code should start with ember-");
        var parts = service.parsePairingCode(code);
        assertTrue(parts.isPresent(), "Generated pairing code should be parseable");
        assertEquals(stationA.uid(), parts.get().stationUid());
        assertFalse(parts.get().isStationInvite(), "Pairing code should not be a station invite");
    }

    @Test
    @Order(2)
    void generatePairingCodeDeterministic() {
        String code1 = service.generatePairingCode(stationA.uid());
        String code2 = service.generatePairingCode(stationA.uid());
        assertEquals(code1, code2, "Pairing code should be deterministic for the same station");
    }

    @Test
    @Order(3)
    void generateKeyPairProducesValidKeys() {
        var keyPair = service.generateKeyPair();
        assertNotNull(keyPair);
        String publicKey = service.encodePublicKey(keyPair);
        assertNotNull(publicKey);
        assertFalse(publicKey.isEmpty());
    }

    @Test
    @Order(4)
    void acceptInviteCreatesBidirectionalPartners() {
        var keyPair = service.generateKeyPair();
        var partner = service.acceptInvite(stationB.id(), stationA.id(), service.encodePublicKey(keyPair), null, null);

        assertNotNull(partner);
        assertEquals(FederationPartner.FederationStatus.ACTIVE, partner.status());
        assertEquals(stationA.id(), partner.stationId());
        assertEquals(stationB.id(), partner.partnerStationId());
        assertNotNull(partner.partnerPublicKey());
        assertNull(partner.remoteHost());
        assertFalse(partner.isRemote());
        partnerIdAtoB = partner.id();

        // Verify reverse partner exists
        var reversePartners = service.findPartners(stationB.id());
        assertTrue(reversePartners.stream()
                .anyMatch(p -> p.partnerStationId() == stationA.id()
                        && p.status() == FederationPartner.FederationStatus.ACTIVE));
    }

    @Test
    @Order(5)
    void findPartnersByStation() {
        var partners = service.findPartners(stationA.id());
        assertFalse(partners.isEmpty());
        assertTrue(partners.stream().anyMatch(p -> p.id() == partnerIdAtoB));
    }

    @Test
    @Order(6)
    void findPartnerById() {
        var found = service.findPartner(partnerIdAtoB);
        assertTrue(found.isPresent());
        assertEquals(partnerIdAtoB, found.get().id());
    }

    // -- Capabilities --

    @Test
    @Order(10)
    void hasCapabilityReturnsTrueWhenEnabled() {
        // Capabilities are initialized with all enabled during acceptInvite
        assertTrue(service.hasCapability(partnerIdAtoB, "KB_SHARE", "IMPORT"));
        assertTrue(service.hasCapability(partnerIdAtoB, "QUIZ_SHARE", "EXPORT"));
    }

    @Test
    @Order(11)
    void setCapabilityDisables() {
        service.setCapability(partnerIdAtoB, "KB_SHARE", "IMPORT", false);
        assertFalse(service.hasCapability(partnerIdAtoB, "KB_SHARE", "IMPORT"));
    }

    @Test
    @Order(12)
    void setCapabilityReenables() {
        service.setCapability(partnerIdAtoB, "KB_SHARE", "IMPORT", true);
        assertTrue(service.hasCapability(partnerIdAtoB, "KB_SHARE", "IMPORT"));
    }

    @Test
    @Order(13)
    void findCapabilitiesReturnsAll() {
        var caps = service.findCapabilities(partnerIdAtoB);
        assertFalse(caps.isEmpty());
    }

    // -- Suspend / Resume --

    @Test
    @Order(20)
    void suspendPartner() {
        assertTrue(service.suspendPartner(partnerIdAtoB));
        var partner = service.findPartner(partnerIdAtoB).orElseThrow();
        assertEquals(FederationPartner.FederationStatus.SUSPENDED, partner.status());
    }

    @Test
    @Order(21)
    void resumePartner() {
        assertTrue(service.resumePartner(partnerIdAtoB));
        var partner = service.findPartner(partnerIdAtoB).orElseThrow();
        assertEquals(FederationPartner.FederationStatus.ACTIVE, partner.status());
    }

    // -- Change Logging --

    @Test
    @Order(30)
    void logAndRetrieveChanges() {
        Instant before = Instant.now().minusSeconds(1);
        service.logChange(stationA.id(), "KB", 42, "CREATED");
        service.logChange(stationA.id(), "QUIZ", 7, "UPDATED");

        var changes = service.getChangesSince(stationA.id(), before);
        assertTrue(changes.size() >= 2);
        assertTrue(changes.stream().anyMatch(c -> c.contentType().equals("KB") && c.contentId() == 42));
    }

    // -- Federation Version (tested via partner entity) --

    @Test
    @Order(41)
    void supportedCapabilitiesNotEmpty() {
        var caps = service.getSupportedCapabilities();
        assertFalse(caps.isEmpty());
        assertTrue(caps.contains("KB_SHARE"));
        assertTrue(caps.contains("QUIZ_SHARE"));
        assertTrue(caps.contains("PROTOCOL_SHARE"));
    }

    // -- Keypair --

    @Test
    @Order(50)
    void generateKeyPairAndEncodePublicKey() {
        var keyPair = service.generateKeyPair();
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());

        String encoded = service.encodePublicKey(keyPair);
        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());
    }

    // -- Remote Host --

    @Test
    @Order(60)
    void acceptInviteWithRemoteHosts() {
        var stationC = stationRepo.create("FedSvcTestStationC");
        var keyPair = service.generateKeyPair();
        var partner = service.acceptInvite(
                stationA.id(),
                stationC.id(),
                service.encodePublicKey(keyPair),
                "https://remote-c.example.com",
                "https://remote-a.example.com");

        assertNotNull(partner);
        assertEquals(FederationPartner.FederationStatus.ACTIVE, partner.status());

        // The partner record from C's POV should show A as remote
        var found = federationRepo.findPartnerById(partner.id()).orElseThrow();
        assertEquals("https://remote-a.example.com", found.remoteHost());
        assertTrue(found.isRemote());

        // The reverse partner (A -> C) should show C as remote
        var reversePartners = service.findPartners(stationA.id());
        var reverse = reversePartners.stream()
                .filter(p -> p.partnerStationId() == stationC.id())
                .findFirst()
                .orElseThrow();
        assertEquals("https://remote-c.example.com", reverse.remoteHost());
        assertTrue(reverse.isRemote());

        // Cleanup
        service.endFederation(partner.id());
        stationRepo.delete(stationC.id());
    }

    @Test
    @Order(61)
    void updateRemoteHost() {
        var stationD = stationRepo.create("FedSvcTestStationD");
        var keyPair = service.generateKeyPair();
        var partner = service.acceptInvite(stationA.id(), stationD.id(), service.encodePublicKey(keyPair), null, null);

        // Initially local
        var reverse = service.findPartners(stationA.id()).stream()
                .filter(p -> p.partnerStationId() == stationD.id())
                .findFirst()
                .orElseThrow();
        assertFalse(reverse.isRemote());

        // Update remote host for stationD (it moved to a remote server)
        service.updateRemoteHost(stationD.id(), "https://new-host.example.com");

        // Now the partner record pointing at stationD should have the new host
        var updated = service.findPartners(stationA.id()).stream()
                .filter(p -> p.partnerStationId() == stationD.id())
                .findFirst()
                .orElseThrow();
        assertEquals("https://new-host.example.com", updated.remoteHost());
        assertTrue(updated.isRemote());

        // Cleanup
        service.endFederation(partner.id());
        stationRepo.delete(stationD.id());
    }

    @Test
    @Order(62)
    void isRemoteOnCreatedPartners() {
        var stationE = stationRepo.create("FedSvcTestStationE");
        var stationF = stationRepo.create("FedSvcTestStationF");

        var local = federationRepo.createPartner(stationE.id(), stationF.id(), "LOCAL-CODE", "pubKey", null);
        assertFalse(local.isRemote());
        assertNull(local.remoteHost());
        federationRepo.deletePartner(local.id());

        var remote = federationRepo.createPartner(
                stationE.id(), stationF.id(), "REMOTE-CODE", "pubKey", "https://remote.example.com");
        assertTrue(remote.isRemote());
        assertEquals("https://remote.example.com", remote.remoteHost());
        federationRepo.deletePartner(remote.id());

        stationRepo.delete(stationE.id());
        stationRepo.delete(stationF.id());
    }

    // -- End Federation --

    @Test
    @Order(99)
    void endFederationDeletesBothDirections() {
        assertTrue(service.endFederation(partnerIdAtoB));
        assertTrue(service.findPartner(partnerIdAtoB).isEmpty());

        // Reverse partner should also be deleted
        var reversePartners = service.findPartners(stationB.id());
        assertTrue(reversePartners.stream().noneMatch(p -> p.partnerStationId() == stationA.id()));
    }
}
