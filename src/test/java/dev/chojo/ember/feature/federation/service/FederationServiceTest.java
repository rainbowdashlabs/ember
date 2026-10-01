/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationMetadataCache;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederationServiceTest extends RepositoryTestBase {

    private static FederationService service;
    private static FederationRepository federationRepo;
    private static Station stationA;
    private static Station stationB;
    private static int partnerIdAtoB;
    private static StationMember memberA;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        service = new FederationService(federationRepo, stationRepo, TestStationKeys.store(), new Api());

        stationA = stationRepo.create("FedSvcTestStationA");
        stationB = stationRepo.create("FedSvcTestStationB");

        var accountA = accountRepo.create("fed-svc-a@test.com", "Fed", "A");
        memberA = stationMemberRepo.create(stationA.id(), accountA.id());
    }

    @AfterAll
    static void cleanup() {
        for (var p : service.findPartners(stationA.id())) federationRepo.deletePartner(p.id());
        for (var p : service.findPartners(stationB.id())) federationRepo.deletePartner(p.id());
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
        assertEquals(stationB.uid(), partner.partnerStationId());
        assertNotNull(partner.partnerPublicKey());
        assertNull(partner.remoteHost());
        assertFalse(partner.isRemote());
        partnerIdAtoB = partner.id();

        var reversePartners = service.findPartners(stationB.id());
        assertTrue(reversePartners.stream()
                .anyMatch(p -> p.partnerStationId().equals(stationA.uid())
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

    @Test
    @Order(10)
    void hasCapabilityReturnsTrueWhenEnabled() {
        assertTrue(service.hasCapability(
                federationRepo.findPartnerById(partnerIdAtoB).orElseThrow(),
                CapabilityType.KB_SHARE,
                Direction.IMPORT));
        assertTrue(service.hasCapability(
                federationRepo.findPartnerById(partnerIdAtoB).orElseThrow(),
                CapabilityType.QUIZ_SHARE,
                Direction.EXPORT));
    }

    @Test
    @Order(11)
    void setCapabilityDisables() {
        service.setCapability(partnerIdAtoB, CapabilityType.KB_SHARE, Direction.IMPORT, false);
        assertFalse(service.hasCapability(
                federationRepo.findPartnerById(partnerIdAtoB).orElseThrow(),
                CapabilityType.KB_SHARE,
                Direction.IMPORT));
    }

    @Test
    @Order(12)
    void setCapabilityReenables() {
        service.setCapability(partnerIdAtoB, CapabilityType.KB_SHARE, Direction.IMPORT, true);
        assertTrue(service.hasCapability(
                federationRepo.findPartnerById(partnerIdAtoB).orElseThrow(),
                CapabilityType.KB_SHARE,
                Direction.IMPORT));
    }

    @Test
    @Order(13)
    void findCapabilitiesReturnsAll() {
        var caps = service.findCapabilities(partnerIdAtoB);
        assertFalse(caps.isEmpty());
    }

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

    @Test
    @Order(30)
    void logAndRetrieveChanges() {
        Instant before = Instant.EPOCH;
        service.logChange(stationA.id(), ContentType.KB, 42, ChangeType.CREATED);
        service.logChange(stationA.id(), ContentType.QUIZ, 7, ChangeType.UPDATED);

        var changes = service.getChangesSince(stationA.id(), before);
        assertTrue(changes.size() >= 2, "Expected at least 2 changes, got " + changes.size());
        assertTrue(changes.stream().anyMatch(c -> c.contentType() == ContentType.KB && c.contentId() == 42));
    }

    @Test
    @Order(41)
    void contractCoversAllCapabilities() {
        var contract = FederationContractVersions.current();
        assertNotNull(contract.core());
        for (var capability : CapabilityType.values()) {
            assertNotNull(contract.featureHash(capability), "Missing surface hash for " + capability);
        }
    }

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

        var found = federationRepo.findPartnerById(partner.id()).orElseThrow();
        assertEquals("https://remote-a.example.com", found.remoteHost());
        assertTrue(found.isRemote());

        var reversePartners = service.findPartners(stationA.id());
        var reverse = reversePartners.stream()
                .filter(p -> p.partnerStationId().equals(stationC.uid()))
                .findFirst()
                .orElseThrow();
        assertEquals("https://remote-c.example.com", reverse.remoteHost());
        assertTrue(reverse.isRemote());

        service.endFederation(partner.id());
        stationRepo.delete(stationC.id());
    }

    @Test
    @Order(61)
    void updateRemoteHost() {
        var stationD = stationRepo.create("FedSvcTestStationD");
        var keyPair = service.generateKeyPair();
        var partner = service.acceptInvite(stationA.id(), stationD.id(), service.encodePublicKey(keyPair), null, null);

        var reverse = service.findPartners(stationA.id()).stream()
                .filter(p -> p.partnerStationId().equals(stationD.uid()))
                .findFirst()
                .orElseThrow();
        assertFalse(reverse.isRemote());

        service.updateRemoteHost(stationD.uid(), "https://new-host.example.com");

        var updated = service.findPartners(stationA.id()).stream()
                .filter(p -> p.partnerStationId().equals(stationD.uid()))
                .findFirst()
                .orElseThrow();
        assertEquals("https://new-host.example.com", updated.remoteHost());
        assertTrue(updated.isRemote());

        service.endFederation(partner.id());
        stationRepo.delete(stationD.id());
    }

    @Test
    @Order(62)
    void isRemoteOnCreatedPartners() {
        var stationE = stationRepo.create("FedSvcTestStationE");
        var stationF = stationRepo.create("FedSvcTestStationF");

        var local = federationRepo.createPartner(stationE.id(), stationF.uid(), "LOCAL-CODE", "pubKey", null);
        assertFalse(local.isRemote());
        assertNull(local.remoteHost());
        federationRepo.deletePartner(local.id());

        var remote = federationRepo.createPartner(
                stationE.id(), stationF.uid(), "REMOTE-CODE", "pubKey", "https://remote.example.com");
        assertTrue(remote.isRemote());
        assertEquals("https://remote.example.com", remote.remoteHost());
        federationRepo.deletePartner(remote.id());

        stationRepo.delete(stationE.id());
        stationRepo.delete(stationF.id());
    }

    @Test
    @Order(70)
    void parsePairingCodeInvalid() {
        assertTrue(service.parsePairingCode("not-ember-prefix").isEmpty());
        assertTrue(service.parsePairingCode("ember-").isEmpty());
        assertTrue(service.parsePairingCode("ember-bad").isEmpty());
        assertTrue(service.parsePairingCode("").isEmpty());
    }

    @Test
    @Order(71)
    void generateStationInviteCode() {
        var code = service.generateStationInvite(stationA.id(), stationA.uid());
        assertNotNull(code);
        assertTrue(code.startsWith("ember-"));
        var parts = service.parsePairingCode(code);
        assertTrue(parts.isPresent());
        assertTrue(parts.get().isStationInvite());
        assertEquals(stationA.uid(), parts.get().stationUid());
        assertNotNull(parts.get().token());
    }

    @Test
    @Order(72)
    void consumeInviteTokenValid() {
        var code = service.generateStationInvite(stationA.id(), stationA.uid());
        var parts = service.parsePairingCode(code).orElseThrow();
        assertTrue(service.consumeInviteToken(stationA.id(), parts.token()));
        assertFalse(service.consumeInviteToken(stationA.id(), parts.token()));
    }

    @Test
    @Order(73)
    void consumeInviteTokenInvalid() {
        assertFalse(service.consumeInviteToken(stationA.id(), "nonexistent-token"));
    }

    @Test
    @Order(74)
    void getInstanceHost() {
        assertNotNull(service.getInstanceHost());
    }

    @Test
    @Order(75)
    void ensureStationKeyIsStable() {
        String publicKey = service.ensureStationKey(stationA.id());
        assertNotNull(publicKey);
        assertFalse(publicKey.isEmpty());
        assertEquals(publicKey, service.ensureStationKey(stationA.id()));
    }

    @Test
    @Order(80)
    void createAndAcceptPairRequest() {
        var stationG = stationRepo.create("FedSvcTestStationG");
        var stationH = stationRepo.create("FedSvcTestStationH");

        var request = service.createPairRequest(stationG.id(), stationH.id());
        assertNotNull(request);
        assertEquals(FederationPartner.FederationStatus.PENDING, request.status());

        var pending = service.findPendingRequests(stationH.id());
        assertTrue(pending.stream().anyMatch(p -> p.stationId() == stationG.id()));

        var accepted = service.acceptPairRequest(request.id());
        assertNotNull(accepted);
        assertEquals(FederationPartner.FederationStatus.ACTIVE, accepted.status());

        service.endFederation(accepted.id());
        stationRepo.delete(stationG.id());
        stationRepo.delete(stationH.id());
    }

    @Test
    @Order(81)
    void declinePairRequest() {
        var stationI = stationRepo.create("FedSvcTestStationI");
        var stationJ = stationRepo.create("FedSvcTestStationJ");

        var request = service.createPairRequest(stationI.id(), stationJ.id());
        service.declinePairRequest(request.id());
        assertTrue(service.findPartner(request.id()).isEmpty());

        stationRepo.delete(stationI.id());
        stationRepo.delete(stationJ.id());
    }

    @Test
    @Order(85)
    void kbShareCrud() {
        var shares = service.findKbShares(stationA.id());
        assertNotNull(shares);
    }

    @Test
    @Order(86)
    void quizShareCrud() {
        var shares = service.findQuizShares(stationA.id());
        assertNotNull(shares);
    }

    @Test
    @Order(87)
    void protocolShareCrud() {
        var shares = service.findProtocolShares(stationA.id());
        assertNotNull(shares);
    }

    @Test
    @Order(88)
    void metadataCacheOperations() {
        var cached = service.getCachedMetadata(partnerIdAtoB, ContentType.KB);
        assertNotNull(cached);

        service.refreshMetadataCache(
                partnerIdAtoB,
                ContentType.KB,
                List.of(new FederationMetadataCache(
                        0, partnerIdAtoB, ContentType.KB, 42, "Test File", "Description", Instant.now())));

        var refreshed = service.getCachedMetadata(partnerIdAtoB, ContentType.KB);
        assertTrue(refreshed.stream().anyMatch(c -> c.remoteId() == 42));
    }

    @Test
    @Order(89)
    void endFederationNonExistent() {
        assertFalse(service.endFederation(99999));
    }

    @Test
    @Order(99)
    void endFederationDeletesBothDirections() {
        assertTrue(service.endFederation(partnerIdAtoB));
        assertTrue(service.findPartner(partnerIdAtoB).isEmpty());

        var reversePartners = service.findPartners(stationB.id());
        assertTrue(reversePartners.stream().noneMatch(p -> p.partnerStationId().equals(stationA.uid())));
    }

    @Test
    @Order(100)
    void acceptPairRequestRejectsNonPending() {
        var stationK = stationRepo.create("FedSvcTestStationK");
        var stationL = stationRepo.create("FedSvcTestStationL");

        var keyPair = service.generateKeyPair();
        var partner = service.acceptInvite(stationK.id(), stationL.id(), service.encodePublicKey(keyPair), null, null);

        assertThrows(IllegalStateException.class, () -> service.acceptPairRequest(partner.id()));

        service.endFederation(partner.id());
        stationRepo.delete(stationK.id());
        stationRepo.delete(stationL.id());
    }

    @Test
    @Order(101)
    void pairingCodePartsIsStationInviteWithNullToken() {
        var parts = new FederationService.PairingCodeParts(stationA.uid(), "localhost", null);
        assertFalse(parts.isStationInvite());
    }

    @Test
    @Order(102)
    void pairingCodePartsIsStationInviteWithBlankToken() {
        var parts = new FederationService.PairingCodeParts(stationA.uid(), "localhost", "");
        assertFalse(parts.isStationInvite());
    }

    @Test
    @Order(103)
    void kbShareCreateAndDelete() {
        var folder = knowledgeBaseRepo.createFolder(stationA.id(), null, "FedTestFolder", "", memberA.id());
        var share = service.createKbShare(stationA.id(), null, folder.id(), ShareScope.ALL_PARTNERS);
        assertNotNull(share);
        assertTrue(service.deleteKbShare(share.id(), stationA.id()));
        assertFalse(service.deleteKbShare(share.id(), stationA.id()), "already deleted");
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    @Test
    @Order(104)
    void quizShareCreateAndDelete() {
        var catalog = quizCatalogRepo.create(stationA.id(), "Test Quiz Catalog", "desc", false, CatalogMetadata.none());
        var share = service.createQuizShare(stationA.id(), catalog.id(), ShareScope.ALL_PARTNERS);
        assertNotNull(share);
        assertTrue(service.deleteQuizShare(share.id(), stationA.id()));
    }

    @Test
    @Order(105)
    void protocolShareCreateAndDelete() {
        var protocol = testProtocolRepo.createProtocol(stationA.id(), "Test Protocol", "", null);
        var share = service.createProtocolShare(stationA.id(), protocol.id(), ShareScope.ALL_PARTNERS);
        assertNotNull(share);
        assertTrue(service.deleteProtocolShare(share.id(), stationA.id()));
    }
}
