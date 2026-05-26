/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
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
import dev.chojo.ember.feature.restriction.RestrictionMode;
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
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationB.id(), stationA.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerIdAtoB = partner.id();

        // Create shares on stationB
        federationRepo.createKbShare(stationB.id(), realKbFileId, null, ShareScope.ALL_PARTNERS);
        federationRepo.createQuizShare(stationB.id(), realQuizCatalogId, ShareScope.ALL_PARTNERS);
        federationRepo.createProtocolShare(stationB.id(), realProtocolId, ShareScope.ALL_PARTNERS);

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
                null,
                RestrictionMode.AND,
                false);
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
                stationB.id(),
                RestrictionMode.AND,
                false);
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
        federationService.setCapability(partnerIdAtoB, CapabilityType.KB_SHARE, Direction.IMPORT, false);
        assertTrue(contentService.browseSharedKb(stationA.id()).isEmpty());
        federationService.setCapability(partnerIdAtoB, CapabilityType.KB_SHARE, Direction.IMPORT, true);
    }

    @Test
    @Order(21)
    void browseQuizReturnsEmptyWhenCapabilityDisabled() {
        federationService.setCapability(partnerIdAtoB, CapabilityType.QUIZ_SHARE, Direction.IMPORT, false);
        assertTrue(contentService.browseSharedQuiz(stationA.id()).isEmpty());
        federationService.setCapability(partnerIdAtoB, CapabilityType.QUIZ_SHARE, Direction.IMPORT, true);
    }

    @Test
    @Order(22)
    void browseProtocolsReturnsEmptyWhenCapabilityDisabled() {
        federationService.setCapability(partnerIdAtoB, CapabilityType.PROTOCOL_SHARE, Direction.IMPORT, false);
        assertTrue(contentService.browseSharedProtocols(stationA.id()).isEmpty());
        federationService.setCapability(partnerIdAtoB, CapabilityType.PROTOCOL_SHARE, Direction.IMPORT, true);
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

    // -- Browse with no partners returns empty --

    @Test
    @Order(50)
    void browseSharedKbNoPartnersReturnsEmpty() {
        // Create isolated station with no federation
        var isolated = stationRepo.create("FedContentIsolated");
        assertTrue(contentService.browseSharedKb(isolated.id()).isEmpty());
        assertTrue(contentService.browseSharedQuiz(isolated.id()).isEmpty());
        assertTrue(contentService.browseSharedProtocols(isolated.id()).isEmpty());
        stationRepo.delete(isolated.id());
    }

    // -- Browse KB with folder share --

    @Test
    @Order(51)
    void browseSharedKbWithFolderShare() {
        // Create a folder share
        var folder = kbRepo.createFolder(stationB.id(), null, "SharedFolder", "desc", memberB.id());
        var folderFile = kbRepo.createFile(
                stationB.id(),
                folder.id(),
                "FolderFile",
                "File in folder",
                KbFileType.MARKDOWN,
                "text/markdown",
                100,
                null,
                memberB.id());

        federationRepo.createKbShare(stationB.id(), null, folder.id(), ShareScope.ALL_PARTNERS);

        // Mock folder and file lookups
        var now = Instant.now();
        var mockFolder = new KbFolder(
                folder.id(), stationB.id(), null, "SharedFolder", "desc", null, 0, memberB.id(), now, now, null, false);
        when(kbService.findFolder(folder.id())).thenReturn(Optional.of(mockFolder));

        var mockFile = new KbFile(
                folderFile.id(),
                stationB.id(),
                folder.id(),
                "FolderFile",
                "File in folder",
                KbFileType.MARKDOWN,
                "text/markdown",
                100,
                null,
                null,
                null,
                0,
                memberB.id(),
                now,
                now,
                null,
                null,
                RestrictionMode.AND,
                false);
        when(kbService.findFiles(stationB.id(), folder.id())).thenReturn(List.of(mockFile));

        var items = contentService.browseSharedKb(stationA.id());
        // Should contain both the folder and the file
        assertTrue(items.stream().anyMatch(i -> i.folder() != null && i.folder().id() == folder.id()));
        assertTrue(items.stream().anyMatch(i -> i.file() != null && i.file().id() == folderFile.id()));

        kbRepo.deleteFile(folderFile.id());
        kbRepo.deleteFolder(folder.id());
    }

    // -- Copy KB file records source reference --

    @Test
    @Order(52)
    void copyKbFileWithFavourite() {
        when(kbService.isFavourite(1, realKbFileId)).thenReturn(true);
        var copied = contentService.copyKbFile(realKbFileId, stationA.id(), 1);
        assertNotNull(copied);
        verify(kbService).addFavourite(1, 9999);
    }

    // -- HTTP partner browse --

    @Test
    @Order(60)
    void browseSharedKbViaHttpReturnsItems() {
        // Create a new partner with a remote host so the HTTP path is taken
        var httpClient = mock(FederationHttpClient.class);
        var svc = new FederatedContentService(
                federationRepo, federationService, httpClient, kbService, quizService, protocolService, stationRepo);

        var stationC = stationRepo.create("FedHttpStationC");
        var stationD = stationRepo.create("FedHttpStationD");

        // Set acceptingRemoteHost so the partner from stationC's POV is remote
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationD.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote.example.com");

        // Enable KB capability
        federationService.setCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT, true);

        // Mock HTTP client to return a file
        var remoteFile = new FederationHttpClient.RemoteKbFile(realKbFileId, "RemoteShared", "Desc", "MARKDOWN");
        when(httpClient.fetchSharedKbFiles(anyString(), anyInt(), any())).thenReturn(List.of(remoteFile));

        var items = svc.browseSharedKb(stationC.id());
        assertFalse(items.isEmpty());
        assertTrue(
                items.stream().anyMatch(i -> i.file() != null && i.file().name().equals("RemoteShared")));

        stationRepo.delete(stationC.id());
        stationRepo.delete(stationD.id());
    }

    @Test
    @Order(61)
    void browseSharedQuizViaHttpReturnsItems() {
        var httpClient = mock(FederationHttpClient.class);
        var svc = new FederatedContentService(
                federationRepo, federationService, httpClient, kbService, quizService, protocolService, stationRepo);

        var stationE = stationRepo.create("FedHttpStationE");
        var stationF = stationRepo.create("FedHttpStationF");

        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationF.id(),
                stationE.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote2.example.com");

        federationService.setCapability(partner.id(), CapabilityType.QUIZ_SHARE, Direction.IMPORT, true);

        var remoteCatalog = new FederationHttpClient.RemoteQuizCatalog(realQuizCatalogId, "RemoteCatalog", "CatDesc");
        when(httpClient.fetchSharedQuizCatalogs(anyString(), anyInt(), any())).thenReturn(List.of(remoteCatalog));

        // quizService.findCatalog is already mocked to return the catalog for realQuizCatalogId
        var items = svc.browseSharedQuiz(stationE.id());
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.catalog().id() == realQuizCatalogId));

        stationRepo.delete(stationE.id());
        stationRepo.delete(stationF.id());
    }

    @Test
    @Order(62)
    void browseSharedProtocolsViaHttpReturnsItems() {
        var httpClient = mock(FederationHttpClient.class);
        var svc = new FederatedContentService(
                federationRepo, federationService, httpClient, kbService, quizService, protocolService, stationRepo);

        var stationG = stationRepo.create("FedHttpStationG");
        var stationH = stationRepo.create("FedHttpStationH");

        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationH.id(),
                stationG.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote3.example.com");

        federationService.setCapability(partner.id(), CapabilityType.PROTOCOL_SHARE, Direction.IMPORT, true);

        var remoteProto = new FederationHttpClient.RemoteProtocol(realProtocolId, "RemoteProto", "ProtoDesc");
        when(httpClient.fetchSharedProtocols(anyString(), anyInt(), any())).thenReturn(List.of(remoteProto));

        // protocolService.findProtocol is already mocked to return the protocol for realProtocolId
        var items = svc.browseSharedProtocols(stationG.id());
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.protocol().id() == realProtocolId));

        stationRepo.delete(stationG.id());
        stationRepo.delete(stationH.id());
    }

    @Test
    @Order(63)
    void browseSharedKbViaHttpEmptyResponse() {
        var httpClient = mock(FederationHttpClient.class);
        var svc = new FederatedContentService(
                federationRepo, federationService, httpClient, kbService, quizService, protocolService, stationRepo);

        var stationI = stationRepo.create("FedHttpEmptyI");
        var stationJ = stationRepo.create("FedHttpEmptyJ");

        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationJ.id(),
                stationI.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote4.example.com");

        federationService.setCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT, true);

        // Return empty list from HTTP
        when(httpClient.fetchSharedKbFiles(anyString(), anyInt(), any())).thenReturn(List.of());

        var items = svc.browseSharedKb(stationI.id());
        assertTrue(items.isEmpty());

        stationRepo.delete(stationI.id());
        stationRepo.delete(stationJ.id());
    }

    @Test
    @Order(64)
    void copyKbFileViaHttp() {
        var httpClient = mock(FederationHttpClient.class);
        var svc = new FederatedContentService(
                federationRepo, federationService, httpClient, kbService, quizService, protocolService, stationRepo);

        var stationK = stationRepo.create("FedHttpCopyK");
        var stationL = stationRepo.create("FedHttpCopyL");

        var keyPair = federationService.generateKeyPair();
        // stationL has the file (remoteStationId = stationL), stationK wants to copy it
        // acceptingRemoteHost is stationL's host from stationK's perspective
        var partner = federationService.acceptInvite(
                stationL.id(),
                stationK.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote5.example.com");

        // kbService.findFile returns a file with stationL's id
        var now = Instant.now();
        var remoteFile = new KbFile(
                realKbFileId,
                stationL.id(),
                null,
                "RemoteFile",
                "Remote desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                100,
                null,
                null,
                null,
                0,
                1,
                now,
                now,
                null,
                null,
                RestrictionMode.AND,
                false);
        when(kbService.findFile(realKbFileId)).thenReturn(Optional.of(remoteFile));
        when(httpClient.fetchKbFileContent(anyString(), anyInt(), anyInt(), any()))
                .thenReturn("# Remote Content");

        var copied = svc.copyKbFile(realKbFileId, stationK.id(), 1);
        assertNotNull(copied);
        verify(httpClient).fetchKbFileContent(anyString(), eq(realKbFileId), eq(stationK.id()), any());

        // Restore mock
        var originalFile = new KbFile(
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
                null,
                RestrictionMode.AND,
                false);
        when(kbService.findFile(realKbFileId)).thenReturn(Optional.of(originalFile));

        stationRepo.delete(stationK.id());
        stationRepo.delete(stationL.id());
    }

    @Test
    @Order(66)
    void copyQuizCatalogWithQuestions() {
        // Set up mock to return questions with categories to cover lines 289-302
        var now = Instant.now();
        when(quizService.findQuestions(realQuizCatalogId))
                .thenReturn(List.of(
                        new dev.chojo.ember.feature.quiz.entity.QuizQuestion(
                                100,
                                realQuizCatalogId,
                                10,
                                dev.chojo.ember.feature.quiz.entity.QuestionType.MULTIPLE_CHOICE,
                                "Q1",
                                "Desc Q1",
                                null,
                                5.0,
                                false,
                                null,
                                "{}",
                                0,
                                now,
                                now),
                        new dev.chojo.ember.feature.quiz.entity.QuizQuestion(
                                101,
                                realQuizCatalogId,
                                null,
                                dev.chojo.ember.feature.quiz.entity.QuestionType.FREE_ANSWER,
                                "Q2",
                                "Desc Q2",
                                null,
                                3.0,
                                true,
                                null,
                                "{}",
                                1,
                                now,
                                now)));

        var copied = contentService.copyQuizCatalog(realQuizCatalogId, stationA.id());
        assertNotNull(copied);
        // Verify questions were created — one with mapped category, one with null
        verify(quizService, atLeastOnce())
                .createQuestion(
                        anyInt(),
                        eq(20),
                        any(),
                        anyString(),
                        anyString(),
                        any(),
                        anyDouble(),
                        anyBoolean(),
                        any(dev.chojo.ember.feature.quiz.entity.QuestionConfig.class),
                        anyInt());
        verify(quizService, atLeastOnce())
                .createQuestion(
                        anyInt(),
                        isNull(),
                        any(),
                        eq("Q2"),
                        anyString(),
                        any(),
                        anyDouble(),
                        anyBoolean(),
                        any(dev.chojo.ember.feature.quiz.entity.QuestionConfig.class),
                        anyInt());

        // Restore empty questions mock
        when(quizService.findQuestions(realQuizCatalogId)).thenReturn(List.of());
    }

    @Test
    @Order(67)
    void copyProtocolWithSectionsAndItems() {
        // Set up mock to return sections (root + child) and items to cover lines 314-348
        var now = Instant.now();
        var rootSection = new dev.chojo.ember.feature.protocol.entity.TestProtocolSection(
                1, realProtocolId, null, "Root Section", "Root desc", 100, 70, 0);
        var childSection = new dev.chojo.ember.feature.protocol.entity.TestProtocolSection(
                2, realProtocolId, 1, "Child Section", "Child desc", 50, 40, 0);
        when(protocolService.findSections(realProtocolId)).thenReturn(List.of(rootSection, childSection));

        var newRootSection = new dev.chojo.ember.feature.protocol.entity.TestProtocolSection(
                10, 9997, null, "Root Section", "Root desc", 100, 70, 0);
        var newChildSection = new dev.chojo.ember.feature.protocol.entity.TestProtocolSection(
                11, 9997, 10, "Child Section", "Child desc", 50, 40, 0);
        when(protocolService.createSection(eq(9997), isNull(), eq("Root Section"), anyString(), any(), any(), anyInt()))
                .thenReturn(newRootSection);
        when(protocolService.createSection(eq(9997), eq(10), eq("Child Section"), anyString(), any(), any(), anyInt()))
                .thenReturn(newChildSection);

        var item = new dev.chojo.ember.feature.protocol.entity.TestProtocolItem(100, 1, "Item 1", "Item desc", 10.0, 0);
        when(protocolService.findAllItemsByProtocol(realProtocolId)).thenReturn(List.of(item));

        var copied = contentService.copyProtocol(realProtocolId, stationA.id());
        assertNotNull(copied);
        // Verify sections and items were created
        verify(protocolService)
                .createSection(eq(9997), isNull(), eq("Root Section"), anyString(), any(), any(), anyInt());
        verify(protocolService)
                .createSection(eq(9997), eq(10), eq("Child Section"), anyString(), any(), any(), anyInt());
        verify(protocolService).createItem(eq(10), eq("Item 1"), eq("Item desc"), eq(10.0), eq(0));

        // Restore
        when(protocolService.findSections(realProtocolId)).thenReturn(List.of());
        when(protocolService.findAllItemsByProtocol(realProtocolId)).thenReturn(List.of());
    }

    @Test
    @Order(65)
    void browseSharedKbViaHttpNullFileType() {
        // Verify that a remote KB file with null fileType defaults to MARKDOWN
        var httpClient = mock(FederationHttpClient.class);
        var svc = new FederatedContentService(
                federationRepo, federationService, httpClient, kbService, quizService, protocolService, stationRepo);

        var stationM = stationRepo.create("FedHttpNullTypeM");
        var stationN = stationRepo.create("FedHttpNullTypeN");

        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationN.id(),
                stationM.id(),
                federationService.encodePublicKey(keyPair),
                null,
                "https://remote6.example.com");

        federationService.setCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT, true);

        // fileType is null — should default to MARKDOWN
        var remoteFile = new FederationHttpClient.RemoteKbFile(realKbFileId + 100, "NullTypeFile", "desc", null);
        when(httpClient.fetchSharedKbFiles(anyString(), anyInt(), any())).thenReturn(List.of(remoteFile));

        var items = svc.browseSharedKb(stationM.id());
        assertFalse(items.isEmpty());
        assertEquals(KbFileType.MARKDOWN, items.get(0).file().fileType());

        stationRepo.delete(stationM.id());
        stationRepo.delete(stationN.id());
    }
}
