/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedContentServiceTest extends RepositoryTestBase {

    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederatedContentService contentService;
    private static KnowledgeBaseService kbService;
    private static QuizService quizService;
    private static TestProtocolService protocolService;

    private static KnowledgeBaseRepository kbRepo;
    private static QuizCatalogRepository quizCatalogRepo;
    private static TestProtocolRepository protocolRepo;

    private static Station stationA;
    private static Station stationB;
    private static Account account;
    private static StationMember memberB;
    private static int partnerIdAtoB;

    // Real DB IDs for shared content (created in setup)
    private static int realKbFileId;
    private static int realQuizCatalogId;
    private static int realProtocolId;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        kbService = mock(KnowledgeBaseService.class);
        quizService = mock(QuizService.class);
        protocolService = mock(TestProtocolService.class);
        var httpClient = mock(FederationHttpClient.class);
        contentService = new FederatedContentService(
                federationRepo, federationService, httpClient, kbService, quizService, protocolService, stationRepo);

        kbRepo = new KnowledgeBaseRepository();
        quizCatalogRepo = new QuizCatalogRepository();
        protocolRepo = new TestProtocolRepository();

        stationA = stationRepo.create("FedContentTestA");
        stationB = stationRepo.create("FedContentTestB");

        // Create account and member for stationB (needed for KB createdBy)
        account = accountRepo.create("fedcontent@test.com", "FedContent", "Tester");
        memberB = stationMemberRepo.create(stationB.id(), account.id());

        // Create real DB records on stationB for FK constraints in share tables
        var kbFolder = kbRepo.createFolder(stationB.id(), null, "FedContentFolder", "desc", memberB.id());
        var kbFile = kbRepo.createFile(
                stationB.id(),
                kbFolder.id(),
                "SharedFile",
                "A shared file",
                KbFileType.MARKDOWN,
                null,
                0,
                null,
                memberB.id());
        realKbFileId = kbFile.id();

        var catalog = quizCatalogRepo.create(stationB.id(), "SharedCatalog", "A shared catalog", false);
        realQuizCatalogId = catalog.id();

        var protocol = protocolRepo.createProtocol(stationB.id(), "SharedProtocol", "A shared protocol", 70);
        realProtocolId = protocol.id();

        // Create bidirectional federation
        var invite = federationService.createInvite(stationA.id());
        var partner = federationService.acceptInvite(stationB.id(), stationA.id(), invite.publicKey(), null, null);
        partnerIdAtoB = partner.id();

        // Create shares on stationB
        federationRepo.createKbShare(stationB.id(), realKbFileId, null, "ALL_PARTNERS");
        federationRepo.createQuizShare(stationB.id(), realQuizCatalogId, "ALL_PARTNERS");
        federationRepo.createProtocolShare(stationB.id(), realProtocolId, "ALL_PARTNERS");

        setupMocks();
    }

    private static void setupMocks() {
        var now = Instant.now();

        // Mock KnowledgeBaseService - the content service calls kbService to get file details
        var kbFile = new KbFile(
                realKbFileId,
                stationB.id(),
                null,
                "SharedFile",
                "A shared file",
                KbFileType.MARKDOWN,
                "text/markdown",
                1024,
                null,
                null,
                null,
                0,
                memberB.id(),
                now,
                now,
                null,
                null);
        when(kbService.findFile(realKbFileId)).thenReturn(Optional.of(kbFile));
        when(kbService.getMarkdownContent(realKbFileId)).thenReturn(Optional.of("# Shared Content"));
        var copiedFile = new KbFile(
                9999,
                stationA.id(),
                null,
                "SharedFile",
                "A shared file",
                KbFileType.MARKDOWN,
                "text/markdown",
                1024,
                null,
                null,
                null,
                0,
                1,
                now,
                now,
                realKbFileId,
                stationB.id());
        when(kbService.createMarkdownFile(anyInt(), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(copiedFile);
        when(kbService.findFile(9999)).thenReturn(Optional.of(copiedFile));

        // Mock QuizService
        var catalog =
                new QuizCatalog(realQuizCatalogId, stationB.id(), "SharedCatalog", "A shared catalog", false, now, now);
        when(quizService.findCatalog(realQuizCatalogId)).thenReturn(Optional.of(catalog));
        when(quizService.createCatalog(anyInt(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new QuizCatalog(9998, stationA.id(), "SharedCatalog", "A shared catalog", false, now, now));
        when(quizService.findCategories(realQuizCatalogId))
                .thenReturn(List.of(new QuizCategory(10, realQuizCatalogId, "Cat1", "Category 1", 0)));
        when(quizService.createCategory(anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(new QuizCategory(20, 9998, "Cat1", "Category 1", 0));
        when(quizService.findQuestions(realQuizCatalogId)).thenReturn(List.of());

        // Mock TestProtocolService
        var protocol =
                new TestProtocol(realProtocolId, stationB.id(), "SharedProtocol", "A shared protocol", 70, now, now);
        when(protocolService.findProtocol(realProtocolId)).thenReturn(Optional.of(protocol));
        when(protocolService.createProtocol(anyInt(), anyString(), anyString(), any()))
                .thenReturn(new TestProtocol(9997, stationA.id(), "SharedProtocol", "A shared protocol", 70, now, now));
        when(protocolService.findSections(realProtocolId)).thenReturn(List.of());
        when(protocolService.findAllItemsByProtocol(realProtocolId)).thenReturn(List.of());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        accountRepo.delete(account.id());
    }

    // -- Browse shared content --

    @Test
    @Order(1)
    void browseSharedKbReturnsFiles() {
        var items = contentService.browseSharedKb(stationA.id());
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.file() != null && i.file().id() == realKbFileId));
    }

    @Test
    @Order(2)
    void browseSharedQuizReturnsCatalogs() {
        var items = contentService.browseSharedQuiz(stationA.id());
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.catalog().id() == realQuizCatalogId));
    }

    @Test
    @Order(3)
    void browseSharedProtocolsReturnsProtocols() {
        var items = contentService.browseSharedProtocols(stationA.id());
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.protocol().id() == realProtocolId));
    }

    // -- No results when suspended --

    @Test
    @Order(10)
    void browseReturnsEmptyWhenPartnerSuspended() {
        federationService.suspendPartner(partnerIdAtoB);

        assertTrue(contentService.browseSharedKb(stationA.id()).isEmpty());
        assertTrue(contentService.browseSharedQuiz(stationA.id()).isEmpty());
        assertTrue(contentService.browseSharedProtocols(stationA.id()).isEmpty());

        // Restore
        federationService.resumePartner(partnerIdAtoB);
    }

    // -- No results when capability disabled --

    @Test
    @Order(20)
    void browseKbReturnsEmptyWhenCapabilityDisabled() {
        federationService.setCapability(partnerIdAtoB, "KB_SHARE", "IMPORT", false);
        assertTrue(contentService.browseSharedKb(stationA.id()).isEmpty());
        federationService.setCapability(partnerIdAtoB, "KB_SHARE", "IMPORT", true);
    }

    @Test
    @Order(21)
    void browseQuizReturnsEmptyWhenCapabilityDisabled() {
        federationService.setCapability(partnerIdAtoB, "QUIZ_SHARE", "IMPORT", false);
        assertTrue(contentService.browseSharedQuiz(stationA.id()).isEmpty());
        federationService.setCapability(partnerIdAtoB, "QUIZ_SHARE", "IMPORT", true);
    }

    @Test
    @Order(22)
    void browseProtocolsReturnsEmptyWhenCapabilityDisabled() {
        federationService.setCapability(partnerIdAtoB, "PROTOCOL_SHARE", "IMPORT", false);
        assertTrue(contentService.browseSharedProtocols(stationA.id()).isEmpty());
        federationService.setCapability(partnerIdAtoB, "PROTOCOL_SHARE", "IMPORT", true);
    }

    // -- Copy content --

    @Test
    @Order(30)
    void copyKbFile() {
        var copied = contentService.copyKbFile(realKbFileId, stationA.id(), 1);
        assertNotNull(copied);
        assertEquals(stationA.id(), copied.stationId());
        assertEquals(realKbFileId, copied.sourceFileId());
        assertEquals(stationB.id(), copied.sourceStationId());
        verify(kbService)
                .createMarkdownFile(
                        eq(stationA.id()),
                        isNull(),
                        eq("SharedFile"),
                        eq("A shared file"),
                        eq("# Shared Content"),
                        eq(1));
        verify(kbService).setSourceReference(9999, realKbFileId, stationB.id());
    }

    @Test
    @Order(31)
    void copyQuizCatalog() {
        var copied = contentService.copyQuizCatalog(realQuizCatalogId, stationA.id());
        assertNotNull(copied);
        assertEquals(stationA.id(), copied.stationId());
        verify(quizService).createCatalog(eq(stationA.id()), eq("SharedCatalog"), eq("A shared catalog"), eq(false));
        verify(quizService).createCategory(anyInt(), eq("Cat1"), eq("Category 1"), eq(0));
    }

    @Test
    @Order(32)
    void copyProtocol() {
        var copied = contentService.copyProtocol(realProtocolId, stationA.id());
        assertNotNull(copied);
        assertEquals(stationA.id(), copied.stationId());
        verify(protocolService)
                .createProtocol(eq(stationA.id()), eq("SharedProtocol"), eq("A shared protocol"), eq(70));
    }

    // -- Metadata cache is updated during browse --

    // -- Local partner uses direct DB, not HTTP --

    @Test
    @Order(35)
    void localPartnerDoesNotUseHttpClient() {
        // Both partners are local (remoteHost=null), so browseSharedKb should use direct DB access
        var partner = federationRepo.findPartnerById(partnerIdAtoB).orElseThrow();
        assertNull(partner.remoteHost());
        assertFalse(partner.isRemote());

        // Browse should succeed without any HTTP calls
        var items = contentService.browseSharedKb(stationA.id());
        assertFalse(items.isEmpty());
        // The httpClient mock was never configured for browseSharedKb, so if HTTP were used it would fail or return
        // empty
    }

    // -- Metadata cache is updated during browse --

    @Test
    @Order(40)
    void browseKbUpdatesMetadataCache() {
        contentService.browseSharedKb(stationA.id());
        var cached = federationRepo.findCachedMetadata(partnerIdAtoB, "KB");
        assertTrue(cached.stream()
                .anyMatch(c -> c.remoteId() == realKbFileId && c.title().equals("SharedFile")));
    }

    @Test
    @Order(41)
    void browseQuizUpdatesMetadataCache() {
        contentService.browseSharedQuiz(stationA.id());
        var cached = federationRepo.findCachedMetadata(partnerIdAtoB, "QUIZ");
        assertTrue(cached.stream()
                .anyMatch(c -> c.remoteId() == realQuizCatalogId && c.title().equals("SharedCatalog")));
    }

    @Test
    @Order(42)
    void browseProtocolsUpdatesMetadataCache() {
        contentService.browseSharedProtocols(stationA.id());
        var cached = federationRepo.findCachedMetadata(partnerIdAtoB, "PROTOCOL");
        assertTrue(cached.stream()
                .anyMatch(c -> c.remoteId() == realProtocolId && c.title().equals("SharedProtocol")));
    }
}
