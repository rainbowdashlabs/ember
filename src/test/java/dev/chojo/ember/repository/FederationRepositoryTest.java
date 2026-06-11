/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederationRepositoryTest extends RepositoryTestBase {

    private static FederationRepository federationRepo;
    private static KnowledgeBaseRepository kbRepo;
    private static QuizCatalogRepository quizCatalogRepo;
    private static TestProtocolRepository protocolRepo;

    private static Station stationA;
    private static Station stationB;
    private static Station stationC;
    private static Account account;
    private static StationMember member;
    private static int partnerId;
    private static int kbFileId;
    private static int quizCatalogId;
    private static int protocolId;
    private static int kbShareId;
    private static int quizShareId;
    private static int protocolShareId;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        kbRepo = new KnowledgeBaseRepository();
        quizCatalogRepo = new QuizCatalogRepository();
        protocolRepo = new TestProtocolRepository();

        stationA = stationRepo.create("FedRepoTestStationA");
        stationB = stationRepo.create("FedRepoTestStationB");
        stationC = stationRepo.create("FedRepoTestStationC");

        // Create account and member for createdBy references
        account = accountRepo.create("fedrepo@test.com", "Fed", "Tester");
        member = stationMemberRepo.create(stationA.id(), account.id());

        // Create KB file for sharing tests
        var folder = kbRepo.createFolder(stationA.id(), null, "FedFolder", "Test folder", member.id());
        var file = kbRepo.createFile(
                stationA.id(), folder.id(), "FedFile", "Test file", KbFileType.MARKDOWN, null, 0, null, member.id());
        kbFileId = file.id();

        // Create quiz catalog for sharing tests
        var catalog = quizCatalogRepo.create(stationA.id(), "FedCatalog", "Test catalog", false);
        quizCatalogId = catalog.id();

        // Create protocol for sharing tests
        var protocol = protocolRepo.createProtocol(stationA.id(), "FedProtocol", "Test protocol", 70);
        protocolId = protocol.id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        stationRepo.delete(stationC.id());
        accountRepo.delete(account.id());
    }

    // -- Partner CRUD --

    @Test
    @Order(1)
    void createPartner() {
        var partner =
                federationRepo.createPartner(stationA.id(), stationB.uid(), "EMBER-TEST-CODE", "publicKeyA", null);
        assertNotNull(partner);
        assertTrue(partner.id() > 0);
        assertEquals(stationA.id(), partner.stationId());
        assertEquals(stationB.uid(), partner.partnerStationId());
        assertEquals("EMBER-TEST-CODE", partner.inviteCode());
        assertEquals("publicKeyA", partner.publicKey());
        assertEquals(FederationPartner.FederationStatus.PENDING, partner.status());
        assertNull(partner.remoteHost());
        assertFalse(partner.isRemote());
        partnerId = partner.id();
    }

    @Test
    @Order(2)
    void findPartnerById() {
        var found = federationRepo.findPartnerById(partnerId);
        assertTrue(found.isPresent());
        assertEquals(partnerId, found.get().id());
    }

    @Test
    @Order(3)
    void findPartnersByStation() {
        var partners = federationRepo.findPartners(stationA.id());
        assertFalse(partners.isEmpty());
        assertTrue(partners.stream().anyMatch(p -> p.id() == partnerId));
    }

    @Test
    @Order(4)
    void findByInviteCode() {
        var found = federationRepo.findByInviteCode("EMBER-TEST-CODE");
        assertTrue(found.isPresent());
        assertEquals(partnerId, found.get().id());
    }

    @Test
    @Order(5)
    void findByInviteCodeNotFound() {
        var found = federationRepo.findByInviteCode("EMBER-XXXX-ZZZZ");
        assertTrue(found.isEmpty());
    }

    @Test
    @Order(6)
    void activatePartnerSetsPublicKeyAndClearsInviteCode() {
        assertTrue(federationRepo.activatePartner(partnerId, "partnerPublicKeyB"));

        var activated = federationRepo.findPartnerById(partnerId).orElseThrow();
        assertEquals(FederationPartner.FederationStatus.ACTIVE, activated.status());
        assertEquals("partnerPublicKeyB", activated.partnerPublicKey());
        assertNull(activated.inviteCode());
    }

    @Test
    @Order(7)
    void updatePartnerStatus() {
        assertTrue(federationRepo.updatePartnerStatus(partnerId, "SUSPENDED"));
        var suspended = federationRepo.findPartnerById(partnerId).orElseThrow();
        assertEquals(FederationPartner.FederationStatus.SUSPENDED, suspended.status());

        // Restore to ACTIVE for subsequent tests
        assertTrue(federationRepo.updatePartnerStatus(partnerId, "ACTIVE"));
    }

    @Test
    @Order(8)
    void findPartnerByRemoteStationUid() {
        var found = federationRepo.findPartnerByRemoteStationUid(stationB.uid());
        assertTrue(found.isPresent());
        assertEquals(partnerId, found.get().id());
    }

    // -- Capabilities --

    @Test
    @Order(10)
    void upsertAndFindCapabilities() {
        federationRepo.upsertCapability(partnerId, CapabilityType.KB_SHARE, Direction.IMPORT, true);
        federationRepo.upsertCapability(partnerId, CapabilityType.KB_SHARE, Direction.EXPORT, false);
        federationRepo.upsertCapability(partnerId, CapabilityType.QUIZ_SHARE, Direction.IMPORT, true);

        var caps = federationRepo.findCapabilities(partnerId);
        assertEquals(3, caps.size());
        assertTrue(caps.stream()
                .anyMatch(c -> c.capability().equals(CapabilityType.KB_SHARE)
                        && c.direction().equals(Direction.IMPORT)
                        && c.enabled()));
        assertTrue(caps.stream()
                .anyMatch(c -> c.capability().equals(CapabilityType.KB_SHARE)
                        && c.direction().equals(Direction.EXPORT)
                        && !c.enabled()));
    }

    @Test
    @Order(11)
    void upsertCapabilityUpdatesExisting() {
        federationRepo.upsertCapability(partnerId, CapabilityType.KB_SHARE, Direction.EXPORT, true);
        var caps = federationRepo.findCapabilities(partnerId);
        assertTrue(caps.stream()
                .anyMatch(c -> c.capability().equals(CapabilityType.KB_SHARE)
                        && c.direction().equals(Direction.EXPORT)
                        && c.enabled()));
    }

    // -- KB Shares --

    @Test
    @Order(20)
    void createAndFindKbShare() {
        var share = federationRepo.createKbShare(stationA.id(), kbFileId, null, ShareScope.ALL_PARTNERS);
        assertNotNull(share);
        assertTrue(share.id() > 0);
        assertEquals(kbFileId, share.fileId());
        assertNull(share.folderId());
        kbShareId = share.id();

        var shares = federationRepo.findKbShares(stationA.id());
        assertFalse(shares.isEmpty());
        assertTrue(shares.stream().anyMatch(s -> s.id() == kbShareId));
    }

    @Test
    @Order(21)
    void deleteKbShare() {
        assertTrue(federationRepo.deleteKbShare(kbShareId));
        var shares = federationRepo.findKbShares(stationA.id());
        assertTrue(shares.stream().noneMatch(s -> s.id() == kbShareId));
    }

    // -- Quiz Shares --

    @Test
    @Order(30)
    void createAndFindQuizShare() {
        var share = federationRepo.createQuizShare(stationA.id(), quizCatalogId, ShareScope.ALL_PARTNERS);
        assertNotNull(share);
        assertTrue(share.id() > 0);
        assertEquals(quizCatalogId, share.catalogId());
        quizShareId = share.id();

        var shares = federationRepo.findQuizShares(stationA.id());
        assertFalse(shares.isEmpty());
        assertTrue(shares.stream().anyMatch(s -> s.id() == quizShareId));
    }

    @Test
    @Order(31)
    void deleteQuizShare() {
        assertTrue(federationRepo.deleteQuizShare(quizShareId));
        var shares = federationRepo.findQuizShares(stationA.id());
        assertTrue(shares.stream().noneMatch(s -> s.id() == quizShareId));
    }

    // -- Protocol Shares --

    @Test
    @Order(40)
    void createAndFindProtocolShare() {
        var share = federationRepo.createProtocolShare(stationA.id(), protocolId, ShareScope.ALL_PARTNERS);
        assertNotNull(share);
        assertTrue(share.id() > 0);
        assertEquals(protocolId, share.protocolId());
        protocolShareId = share.id();

        var shares = federationRepo.findProtocolShares(stationA.id());
        assertFalse(shares.isEmpty());
        assertTrue(shares.stream().anyMatch(s -> s.id() == protocolShareId));
    }

    @Test
    @Order(41)
    void deleteProtocolShare() {
        assertTrue(federationRepo.deleteProtocolShare(protocolShareId));
        var shares = federationRepo.findProtocolShares(stationA.id());
        assertTrue(shares.stream().noneMatch(s -> s.id() == protocolShareId));
    }

    // -- Metadata Cache --

    @Test
    @Order(50)
    void upsertAndFindMetadataCache() {
        federationRepo.upsertMetadataCache(partnerId, ContentType.KB, 100, "Test File", "A description");
        federationRepo.upsertMetadataCache(partnerId, ContentType.KB, 101, "Another File", "Another desc");

        var cached = federationRepo.findCachedMetadata(partnerId, ContentType.KB);
        assertEquals(2, cached.size());
        assertTrue(
                cached.stream().anyMatch(c -> c.remoteId() == 100 && c.title().equals("Test File")));
        assertTrue(
                cached.stream().anyMatch(c -> c.remoteId() == 101 && c.title().equals("Another File")));
    }

    @Test
    @Order(51)
    void upsertMetadataCacheUpdatesExisting() {
        federationRepo.upsertMetadataCache(partnerId, ContentType.KB, 100, "Updated Title", "Updated desc");
        var cached = federationRepo.findCachedMetadata(partnerId, ContentType.KB);
        assertTrue(
                cached.stream().anyMatch(c -> c.remoteId() == 100 && c.title().equals("Updated Title")));
    }

    @Test
    @Order(52)
    void clearMetadataCache() {
        federationRepo.clearMetadataCache(partnerId);
        var cached = federationRepo.findCachedMetadata(partnerId, ContentType.KB);
        assertTrue(cached.isEmpty());
    }

    // -- Change Log --

    @Test
    @Order(60)
    void logAndFindChanges() {
        Instant before = Instant.EPOCH;
        federationRepo.logChange(stationA.id(), ContentType.KB, 1, ChangeType.CREATED);
        federationRepo.logChange(stationA.id(), ContentType.QUIZ, 2, ChangeType.UPDATED);

        var changes = federationRepo.findChangesSince(stationA.id(), before);
        assertTrue(changes.size() >= 2, "Expected at least 2 changes, got " + changes.size() + ": " + changes);
        assertTrue(
                changes.stream()
                        .anyMatch(c -> c.contentType() == ContentType.KB && c.changeType() == ChangeType.CREATED),
                "No KB/CREATED entry found in: " + changes);
        assertTrue(
                changes.stream()
                        .anyMatch(c -> c.contentType() == ContentType.QUIZ && c.changeType() == ChangeType.UPDATED),
                "No QUIZ/UPDATED entry found in: " + changes);
    }

    @Test
    @Order(61)
    void findChangesSinceFiltersOldEntries() {
        Instant afterAll = Instant.now().plusSeconds(60);
        var changes = federationRepo.findChangesSince(stationA.id(), afterAll);
        assertTrue(changes.isEmpty());
    }

    // -- Webhook URL --

    @Test
    @Order(70)
    void setAndGetWebhookUrl() {
        federationRepo.setWebhookUrl(partnerId, "https://example.com/webhook");
        assertEquals("https://example.com/webhook", federationRepo.getWebhookUrl(partnerId));
    }

    // -- Remote Host --

    @Test
    @Order(71)
    void createPartnerWithRemoteHost() {
        var remote = federationRepo.createPartner(
                stationA.id(), stationC.uid(), "EMBER-REMOTE-CODE", "publicKeyRemote", "https://remote.example.com");
        assertNotNull(remote);
        assertEquals("https://remote.example.com", remote.remoteHost());
        assertTrue(remote.isRemote());
        // Cleanup
        federationRepo.deletePartner(remote.id());
    }

    @Test
    @Order(72)
    void updateRemoteHost() {
        assertTrue(federationRepo.updateRemoteHost(partnerId, "https://new-host.example.com"));
        var updated = federationRepo.findPartnerById(partnerId).orElseThrow();
        assertEquals("https://new-host.example.com", updated.remoteHost());
        assertTrue(updated.isRemote());

        // Reset to null
        assertTrue(federationRepo.updateRemoteHost(partnerId, null));
        var reset = federationRepo.findPartnerById(partnerId).orElseThrow();
        assertNull(reset.remoteHost());
        assertFalse(reset.isRemote());
    }

    @Test
    @Order(73)
    void updateRemoteHostForPartnerStation() {
        // Create a partner from stationC to stationB, alongside the existing stationA->stationB partner
        var extra =
                federationRepo.createPartner(stationC.id(), stationB.uid(), "EMBER-EXTRA-CODE", "pubKeyExtra", null);

        federationRepo.updateRemoteHostForPartnerStation(stationB.uid(), "https://moved.example.com");

        var main = federationRepo.findPartnerById(partnerId).orElseThrow();
        assertEquals("https://moved.example.com", main.remoteHost());

        var extraUpdated = federationRepo.findPartnerById(extra.id()).orElseThrow();
        assertEquals("https://moved.example.com", extraUpdated.remoteHost());

        // Reset
        federationRepo.updateRemoteHostForPartnerStation(stationB.uid(), null);
        federationRepo.deletePartner(extra.id());
    }

    // -- Count Pending Requests --

    @Test
    @Order(80)
    void countPendingRequestsNone() {
        // stationA has no pending requests directed at it (partner is stationA -> stationB, status ACTIVE)
        int count = federationRepo.countPendingRequests(stationA.uid());
        assertEquals(0, count);
    }

    @Test
    @Order(81)
    void countPendingRequestsWithPending() {
        // Create a pending partner from stationC to stationA (so stationA's uid is the partner_station_id)
        var pending =
                federationRepo.createPartner(stationC.id(), stationA.uid(), "EMBER-PEND-CODE", "pubKeyPending", null);
        int count = federationRepo.countPendingRequests(stationA.uid());
        assertEquals(1, count);
        // Cleanup
        federationRepo.deletePartner(pending.id());
    }

    // -- Delete Partner --

    @Test
    @Order(50)
    void findAllActiveRemotePartnersEmpty() {
        // No remote partners (all local), so should return empty
        var remote = federationRepo.findAllActiveRemotePartners();
        assertTrue(remote.isEmpty());
    }

    @Test
    @Order(51)
    void backfillPartnerVersions() {
        // Partner was created with current version, so backfill should update 0 rows
        int updated = federationRepo.backfillPartnerVersions("new-version");
        assertEquals(0, updated);
    }

    @Test
    @Order(99)
    void deletePartner() {
        assertTrue(federationRepo.deletePartner(partnerId));
        assertTrue(federationRepo.findPartnerById(partnerId).isEmpty());
    }
}
