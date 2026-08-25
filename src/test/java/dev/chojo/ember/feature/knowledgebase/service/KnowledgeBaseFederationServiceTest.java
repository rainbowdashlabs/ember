/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.FederationTestContracts;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathContains;
import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the federated half of the knowledge base: fan-out browsing and search across partners,
 * single-file resolution, copying, the server-to-server views served to a requesting partner, and
 * the comment proxy in both its local and its remote branch.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeBaseFederationServiceTest extends RepositoryTestBase {
    private static final String REMOTE_HOST = "https://remote-kb.example.com";

    private static KnowledgeBaseService kbService;
    private static KbContentService contentService;
    private static KnowledgeBaseFederationService service;
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;
    private static KbCommentRepository commentRepo;
    private static Station station;
    private static Station stationB;
    private static Station stationC;
    private static Account account;
    private static StationMember member;
    private static FederationPartner requestingPartner;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);
        commentRepo = new KbCommentRepository();
        var storageConfig = new Storage();
        var fileStorage = mock(KbFileStorageService.class);
        var searchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        contentService = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                fileStorage,
                searchService);
        kbService = new KnowledgeBaseService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo),
                new KbPresentationService(knowledgeBaseRepo, fileStorage, contentService),
                new KbLinkMetadataService(new RemoteUrlValidator(new Federation(), new Demo())),
                new PresentationCompressor(storageConfig),
                new PdfCompressor(storageConfig),
                new ClusterAutoShareService(new ClusterRepository(), new FederationRepository()));
        service = new KnowledgeBaseFederationService(
                kbService,
                contentService,
                searchService,
                federationService,
                federationRepo,
                httpClient,
                stationRepo,
                commentRepo,
                mock(EventFederationRepository.class),
                memberNameResolver,
                new FederationFanout(),
                new FederationEntityResolver(federationRepo, stationRepo, httpClient),
                mock(KbPdfExportService.class));

        station = stationRepo.create("KbFedStation");
        stationB = stationRepo.create("KbFedStationB");
        stationC = stationRepo.create("KbFedStationC");
        account = accountRepo.create("kb-fed@test.com", "Kb", "FedTester");
        member = stationMemberRepo.create(station.id(), account.id());

        var localKeyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                station.id(), stationB.id(), federationService.encodePublicKey(localKeyPair), null, null);
        requestingPartner = federationRepo
                .findPartnerByStationAndRemoteUid(station.id(), stationB.uid())
                .orElseThrow();
        federationService.setCapability(requestingPartner.id(), CapabilityType.KB_SHARE, Direction.IMPORT, true);

        var remoteKeyPair = federationService.generateKeyPair();
        var remotePartner = federationService.acceptInvite(
                station.id(), stationC.id(), federationService.encodePublicKey(remoteKeyPair), REMOTE_HOST, null);
        federationService.setCapability(remotePartner.id(), CapabilityType.KB_SHARE, Direction.IMPORT, true);
        FederationTestContracts.storeCurrentContractOnRemotePartners(federationService, federationRepo, station.id());
    }

    @AfterAll
    static void cleanup() {
        for (var partner : federationService.findPartners(station.id())) federationRepo.deletePartner(partner.id());
        for (var partner : federationService.findPartners(stationB.id())) federationRepo.deletePartner(partner.id());
        for (var partner : federationService.findPartners(stationC.id())) federationRepo.deletePartner(partner.id());
        stationRepo.delete(station.id());
        stationRepo.delete(stationB.id());
        stationRepo.delete(stationC.id());
        accountRepo.delete(account.id());
    }

    private static KbFile createFile(int stationId, String name) {
        return knowledgeBaseRepo.createFile(
                stationId, null, name, "desc", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
    }

    @Test
    @Order(1)
    void browseSharedKbEmptyWithoutShares() {
        var level = service.browseSharedKb(station.id());
        assertTrue(level.folders().isEmpty());
        assertTrue(level.files().isEmpty());
    }

    @Test
    @Order(2)
    void browseSharedKbWithFileShare() {
        var file = createFile(stationB.id(), "FedFile");
        var share = federationRepo.createKbShare(stationB.id(), file.id(), null, ShareScope.ALL_PARTNERS);

        var items = service.browseSharedKb(station.id()).files();
        assertTrue(items.stream().anyMatch(item -> item.file().id() == file.id()));
        assertTrue(items.stream().allMatch(item -> item.sourceStationId() == stationB.id()));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(3)
    void browseSharedKbWithFolderShare() {
        var folder = knowledgeBaseRepo.createFolder(stationB.id(), null, "Shared", "", member.id());
        var file = knowledgeBaseRepo.createFile(
                stationB.id(),
                folder.id(),
                "InFolder",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                member.id());
        var share = federationRepo.createKbShare(stationB.id(), null, folder.id(), ShareScope.ALL_PARTNERS);

        // A shared folder arrives as a folder now, with its article inside rather than loose beside it
        var level = service.browseSharedKb(station.id());
        assertTrue(level.folders().stream().anyMatch(shared -> shared.id() == folder.id()));
        assertTrue(level.files().stream().noneMatch(item -> item.file().id() == file.id()));

        var inside = service.browseFederatedKbFolder(station.id(), stationB.uid(), folder.id());
        assertTrue(inside.files().stream().anyMatch(item -> item.remoteId() == file.id()));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.deleteFile(file.id());
        knowledgeBaseRepo.deleteFolder(folder.id());
    }

    /**
     * Sharing a folder shares what is under it, to the bottom. The article here sits two levels down, so
     * it is reached by neither its own share nor a direct parent, which is all the check used to match.
     */
    @Test
    @Order(3)
    void aSharedFolderCarriesItsSubfoldersAndWhatIsDeepInThem() {
        var outer = knowledgeBaseRepo.createFolder(stationB.id(), null, "Outer", "the shared one", member.id());
        var inner = knowledgeBaseRepo.createFolder(stationB.id(), outer.id(), "Inner", "", member.id());
        var deep = knowledgeBaseRepo.createFile(
                stationB.id(),
                inner.id(),
                "DeepFile",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                member.id());
        var share = federationRepo.createKbShare(stationB.id(), null, outer.id(), ShareScope.ALL_PARTNERS);

        var top = service.browseFederatedKb(station.id());
        var served = top.folders().stream()
                .filter(candidate -> candidate.remoteId() == outer.id())
                .findFirst()
                .orElseThrow();
        assertEquals("Outer", served.title());
        assertEquals("the shared one", served.description());
        assertEquals(stationB.name(), served.stationName());

        // The subfolder is offered inside the shared one, not beside it at the top
        assertTrue(top.folders().stream().noneMatch(candidate -> candidate.remoteId() == inner.id()));
        var opened = service.browseFederatedKbFolder(station.id(), stationB.uid(), outer.id());
        assertTrue(opened.folders().stream().anyMatch(candidate -> candidate.remoteId() == inner.id()));

        var deepLevel = service.browseFederatedKbFolder(station.id(), stationB.uid(), inner.id());
        assertTrue(deepLevel.files().stream().anyMatch(candidate -> candidate.remoteId() == deep.id()));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.deleteFile(deep.id());
        knowledgeBaseRepo.deleteFolder(inner.id());
        knowledgeBaseRepo.deleteFolder(outer.id());
    }

    /**
     * An entry for named stations reaches those and nobody else. Both sides of a pairing exist as rows of
     * their own, and the aim is written against the serving station's row, so the reader's row is the
     * wrong one to look for and this is where that goes wrong if it goes wrong.
     */
    @Test
    @Order(3)
    void anEntryForNamedStationsReachesThoseAndNoOther() {
        var forOne = knowledgeBaseRepo.createFile(
                stationB.id(),
                null,
                "ForStationOnly",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                member.id());
        var servingSide = federationRepo
                .findPartnerByStationAndRemoteUid(stationB.id(), station.uid())
                .orElseThrow();
        var share = federationService.createKbShare(
                stationB.id(), forOne.id(), null, ShareScope.SPECIFIC, List.of(servingSide.id()));

        assertTrue(service.browseSharedKb(station.id()).files().stream()
                .anyMatch(item -> item.file().id() == forOne.id()));

        federationRepo.setKbShareTargets(share.id(), List.of());
        assertTrue(service.browseSharedKb(station.id()).files().stream()
                .noneMatch(item -> item.file().id() == forOne.id()));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.deleteFile(forOne.id());
    }

    /** A folder for named stations holding an article for a different one is a contradiction, so it is refused. */
    @Test
    @Order(3)
    void anArticleCannotReachPastTheFolderHoldingIt() {
        var folder = knowledgeBaseRepo.createFolder(stationB.id(), null, "Narrow", "", member.id());
        var inside = knowledgeBaseRepo.createFile(
                stationB.id(),
                folder.id(),
                "Inside",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                member.id());
        var servingSide = federationRepo
                .findPartnerByStationAndRemoteUid(stationB.id(), station.uid())
                .orElseThrow();
        var folderShare =
                service.shareEntry(stationB.id(), null, folder.id(), ShareScope.SPECIFIC, List.of(servingSide.id()));

        assertThrows(
                BadRequestResponse.class,
                () -> service.shareEntry(
                        stationB.id(), inside.id(), null, ShareScope.SPECIFIC, List.of(servingSide.id() + 9999)));
        assertThrows(
                BadRequestResponse.class,
                () -> service.shareEntry(stationB.id(), inside.id(), null, ShareScope.ALL_PARTNERS, List.of()));

        // Narrowing to nobody is allowed: it says less than the folder above, not more
        var narrowed = service.shareEntry(stationB.id(), inside.id(), null, ShareScope.SPECIFIC, List.of());

        federationRepo.deleteKbShare(narrowed.id(), stationB.id());
        federationRepo.deleteKbShare(folderShare.id(), stationB.id());
        knowledgeBaseRepo.deleteFile(inside.id());
        knowledgeBaseRepo.deleteFolder(folder.id());
    }

    @Test
    @Order(4)
    void browseFederatedKbCarriesPartnerStationName() {
        var file = createFile(stationB.id(), "NamedFedFile");
        var share = federationRepo.createKbShare(stationB.id(), file.id(), null, ShareScope.ALL_PARTNERS);

        var items = service.browseFederatedKb(station.id()).files();
        var item = items.stream()
                .filter(candidate -> candidate.remoteId() == file.id())
                .findFirst()
                .orElseThrow();
        assertEquals("NamedFedFile", item.title());
        assertEquals("desc", item.description());
        assertEquals(stationB.name(), item.stationName());
        assertEquals(stationB.uid().toString(), item.stationUid());
        assertEquals(requestingPartner.id(), item.partnerId());

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(5)
    void searchFederatedKbFindsPartnerContent() {
        var file = createFile(stationB.id(), "Loeschangriff");
        knowledgeBaseRepo.storeTextContent(file.id(), "Ablauf beim Loeschangriff");
        knowledgeBaseRepo.updateSearchIndex(file.id(), "Loeschangriff Ablauf", "simple");

        var results = service.searchFederatedKb(station.id(), "Loeschangriff");
        assertTrue(results.stream().anyMatch(result -> result.file().id() == file.id()));
        assertTrue(results.stream().allMatch(result -> result.stationName() != null));

        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(6)
    void browseSharedKbViaHttp() {
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/browse"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(KnowledgeBaseFederationService.RemoteKbBrowse.class)))
                .thenReturn(new KnowledgeBaseFederationService.RemoteKbBrowse(
                        List.of(),
                        List.of(new KnowledgeBaseFederationService.RemoteKbFileSummary(
                                99, "RemoteFile", "remote desc", "MARKDOWN", "now"))));

        var items = service.browseSharedKb(station.id()).files();
        assertTrue(items.stream().anyMatch(item -> item.file().name().equals("RemoteFile")));
    }

    @Test
    @Order(7)
    void browseSharedKbViaHttpDefaultsMissingFileType() {
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/browse"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(KnowledgeBaseFederationService.RemoteKbBrowse.class)))
                .thenReturn(new KnowledgeBaseFederationService.RemoteKbBrowse(
                        List.of(),
                        List.of(new KnowledgeBaseFederationService.RemoteKbFileSummary(
                                98, "TypeLess", "no type", null, "now"))));

        var items = service.browseSharedKb(station.id()).files();
        var item = items.stream()
                .filter(candidate -> candidate.file().id() == 98)
                .findFirst()
                .orElseThrow();
        assertEquals(KbFileType.MARKDOWN, item.file().fileType());
    }

    @Test
    @Order(8)
    void searchFederatedKbViaHttp() {
        when(httpClient.getList(
                        eq(REMOTE_HOST),
                        pathContains("/remote/kb/search"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(KnowledgeBaseFederationService.RemoteKbSearchResultItem.class)))
                .thenReturn(List.of(new KnowledgeBaseFederationService.RemoteKbSearchResultItem(
                        88, "SearchResult", "found desc", "matched snippet")));

        var results = service.searchFederatedKb(station.id(), "test");
        assertTrue(results.stream().anyMatch(result -> result.file().name().equals("SearchResult")));
        assertTrue(results.stream().anyMatch(result -> "matched snippet".equals(result.snippet())));
    }

    @Test
    @Order(20)
    void getFederatedKbFileLocal() {
        var file = createFile(stationB.id(), "FedDetail");
        var result = service.getFederatedKbFile(station.id(), stationB.uid(), file.id());
        assertEquals(file.id(), result.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(21)
    void getFederatedKbFileRejectsForeignFile() {
        var file = createFile(station.id(), "OwnFile");
        assertThrows(
                BadRequestResponse.class, () -> service.getFederatedKbFile(station.id(), stationB.uid(), file.id()));
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(22)
    void getFederatedKbFileContentLocal() {
        var file = createFile(stationB.id(), "FedContent");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Content");
        assertTrue(service.getFederatedKbFileContent(station.id(), stationB.uid(), file.id())
                .contains("Content"));
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(23)
    void getFederatedKbFileRemote() {
        var remoteFile = new RemoteKnowledgeBaseRoutes.RemoteKbFile(
                77,
                stationC.uid(),
                "RemoteDetail",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null);
        when(httpClient.get(eq(REMOTE_HOST), pathIs("/remote/kb/files/77"), any(), eq(station.id()), any(), any()))
                .thenReturn(remoteFile);

        var resolved = service.getFederatedKbFile(station.id(), stationC.uid(), 77);
        assertEquals("RemoteDetail", resolved.name());
        assertEquals(stationC.uid(), resolved.stationUid());
    }

    @Test
    @Order(24)
    void getFederatedKbFileContentRemote() {
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/files/55/content"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(RemoteKnowledgeBaseRoutes.FileContentResponse.class)))
                .thenReturn(new RemoteKnowledgeBaseRoutes.FileContentResponse(55, "# Remote Content"));

        assertEquals("# Remote Content", service.getFederatedKbFileContent(station.id(), stationC.uid(), 55));
    }

    @Test
    @Order(25)
    void getFederatedKbFileContentRemoteWithoutAnswer() {
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/files/56/content"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(RemoteKnowledgeBaseRoutes.FileContentResponse.class)))
                .thenReturn(null);

        assertEquals("", service.getFederatedKbFileContent(station.id(), stationC.uid(), 56));
    }

    @Test
    @Order(26)
    void copyKbFileFromLocalPartner() {
        var file = createFile(stationB.id(), "CopySource");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Copy Me");

        var copied = service.copyKbFile(file.id(), station.id(), member.id());
        assertEquals("CopySource", copied.name());
        assertEquals(station.id(), copied.stationId());
        assertNotEquals(file.id(), copied.id());
        assertTrue(contentService.getMarkdownContent(copied.id()).orElseThrow().contains("Copy Me"));

        knowledgeBaseRepo.deleteFile(copied.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(27)
    void copyKbFileKeepsFavouriteMarking() {
        var file = createFile(stationB.id(), "FavouriteSource");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Fav");
        knowledgeBaseRepo.addFavourite(member.id(), file.id());

        var copied = service.copyKbFile(file.id(), station.id(), member.id());
        assertTrue(knowledgeBaseRepo.isFavourite(member.id(), copied.id()));

        knowledgeBaseRepo.removeFavourite(member.id(), copied.id());
        knowledgeBaseRepo.removeFavourite(member.id(), file.id());
        knowledgeBaseRepo.deleteFile(copied.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(28)
    void copyKbFileFromRemotePartner() {
        var file = createFile(stationC.id(), "RemoteCopySource");
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathContains("/content"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(RemoteKnowledgeBaseRoutes.FileContentResponse.class)))
                .thenReturn(new RemoteKnowledgeBaseRoutes.FileContentResponse(file.id(), "# From remote"));

        var copied = service.copyKbFile(file.id(), station.id(), member.id());
        assertEquals("RemoteCopySource", copied.name());
        assertTrue(contentService.getMarkdownContent(copied.id()).orElseThrow().contains("From remote"));

        knowledgeBaseRepo.deleteFile(copied.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(40)
    void browseForPartnerListsSharedFiles() {
        var file = createFile(station.id(), "ServedFile");
        var share = federationRepo.createKbShare(station.id(), file.id(), null, ShareScope.ALL_PARTNERS);

        var served = service.browseForPartner(requestingPartner).files();
        var entry = served.stream()
                .filter(candidate -> candidate.id() == file.id())
                .findFirst()
                .orElseThrow();
        assertEquals("ServedFile", entry.name());
        assertEquals("desc", entry.description());
        assertEquals("MARKDOWN", entry.fileType());
        assertNotNull(entry.updatedAt());

        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(41)
    void searchForPartnerWithoutQuery() {
        assertTrue(service.searchForPartner(requestingPartner, null).isEmpty());
        assertTrue(service.searchForPartner(requestingPartner, "  ").isEmpty());
    }

    @Test
    @Order(42)
    void searchForPartnerOnlyReturnsSharedMatches() {
        var shared = createFile(station.id(), "Atemschutz");
        var unshared = createFile(station.id(), "Atemschutzgeraet");
        knowledgeBaseRepo.updateSearchIndex(shared.id(), "Atemschutz", "simple");
        knowledgeBaseRepo.updateSearchIndex(unshared.id(), "Atemschutz", "simple");
        var share = federationRepo.createKbShare(station.id(), shared.id(), null, ShareScope.ALL_PARTNERS);

        var results = service.searchForPartner(requestingPartner, "Atemschutz");
        assertTrue(results.stream().anyMatch(result -> result.id() == shared.id()));
        assertFalse(results.stream().anyMatch(result -> result.id() == unshared.id()));

        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.deleteFile(shared.id());
        knowledgeBaseRepo.deleteFile(unshared.id());
    }

    @Test
    @Order(43)
    void fileForPartnerRejectsForeignStation() {
        var file = createFile(stationB.id(), "ForeignFile");
        assertThrows(NotFoundResponse.class, () -> service.fileForPartner(requestingPartner, file.id()));
        knowledgeBaseRepo.deleteFile(file.id());
    }

    /**
     * Belonging to the station a partner is paired with is not the same as being shared with it.
     * File ids run in sequence, so without this a partner reads the whole knowledge base by
     * counting, whatever the station chose to share.
     */
    @Test
    @Order(43)
    void fileForPartnerRefusesAFileThatIsNotShared() {
        var file = createFile(station.id(), "UnsharedFile");

        assertThrows(NotFoundResponse.class, () -> service.fileForPartner(requestingPartner, file.id()));

        var share = federationRepo.createKbShare(station.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        assertEquals(
                "UnsharedFile",
                service.fileForPartner(requestingPartner, file.id()).name());

        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    /**
     * A folder share carries the files in it, which is what the same-instance browse treats as
     * shared too.
     */
    @Test
    @Order(43)
    void fileForPartnerAcceptsAFileInASharedFolder() {
        var folder = knowledgeBaseRepo.createFolder(station.id(), null, "SharedFolder", "", member.id());
        var file = knowledgeBaseRepo.createFile(
                station.id(),
                folder.id(),
                "FolderFile",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                member.id());
        var share = federationRepo.createKbShare(station.id(), null, folder.id(), ShareScope.ALL_PARTNERS);

        assertEquals(
                "FolderFile",
                service.fileForPartner(requestingPartner, file.id()).name());

        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.deleteFile(file.id());
        knowledgeBaseRepo.deleteFolder(folder.id());
    }

    @Test
    @Order(44)
    void fileForPartnerAnswersNotFoundForUnknownFile() {
        assertThrows(NotFoundResponse.class, () -> service.fileForPartner(requestingPartner, 999999));
    }

    @Test
    @Order(45)
    void fileContentForPartner() {
        var file = createFile(station.id(), "ServedContent");
        var share = federationRepo.createKbShare(station.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        knowledgeBaseRepo.storeTextContent(file.id(), "served text");
        assertEquals("served text", service.fileContentForPartner(requestingPartner, file.id()));
        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(46)
    void fileContentForPartnerWithoutStoredText() {
        var file = createFile(station.id(), "EmptyContent");
        var share = federationRepo.createKbShare(station.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        assertEquals("", service.fileContentForPartner(requestingPartner, file.id()));
        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(60)
    void listCommentsMapsAuthors() {
        var file = createFile(station.id(), "CommentedFile");
        var comment = commentRepo.create(
                file.id(), null, new MemberIdentity(stationB.uid(), UUID.randomUUID()), "Partner sagt hallo");

        var responses = service.listComments(file.id());
        assertEquals(1, responses.size());
        assertEquals(comment.id(), responses.getFirst().id());
        assertEquals("Partner sagt hallo", responses.getFirst().content());

        commentRepo.delete(comment.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(61)
    void createRemoteComment() {
        var file = createFile(station.id(), "RemoteCommented");
        var remoteMemberUid = UUID.randomUUID();

        var comment =
                service.createRemoteComment(file.id(), requestingPartner.id(), remoteMemberUid, "Alice", null, "Hi");
        assertEquals("Hi", comment.content());
        assertNotNull(comment.author());
        assertEquals(remoteMemberUid, comment.author().memberUid());
        assertEquals(stationB.uid(), comment.author().stationUid());

        commentRepo.delete(comment.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(62)
    void updateRemoteCommentByAuthor() {
        var file = createFile(station.id(), "RemoteEditable");
        var remoteMemberUid = UUID.randomUUID();
        var comment =
                service.createRemoteComment(file.id(), requestingPartner.id(), remoteMemberUid, "Alice", null, "Erste");

        var updated = service.updateRemoteComment(requestingPartner, comment.id(), remoteMemberUid, "Zweite");
        assertEquals("Zweite", updated.content());

        commentRepo.delete(comment.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(63)
    void updateRemoteCommentRejectsForeignAuthor() {
        var file = createFile(station.id(), "RemoteProtected");
        var comment = service.createRemoteComment(
                file.id(), requestingPartner.id(), UUID.randomUUID(), "Alice", null, "Meins");
        var stranger = UUID.randomUUID();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.updateRemoteComment(requestingPartner, comment.id(), stranger, "Fremd"));

        commentRepo.delete(comment.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(64)
    void requireRemoteCommentAuthorAnswersNotFound() {
        var stranger = UUID.randomUUID();
        assertThrows(
                NotFoundResponse.class,
                () -> service.requireRemoteCommentAuthor(requestingPartner, 999999, stranger, "delete"));
    }

    @Test
    @Order(80)
    void createAndListFederatedCommentsLocally() {
        var file = createFile(stationB.id(), "FederatedComments");
        var memberUid = UUID.randomUUID();

        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), memberUid, "Bob", null, "Frage");
        assertEquals("Frage", created.content());

        var listed = service.listFederatedComments(station.id(), stationB.uid(), file.id());
        assertEquals(1, listed.size());
        assertEquals(created.id(), listed.getFirst().id());

        commentRepo.delete(created.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(81)
    void updateFederatedCommentLocally() {
        var file = createFile(stationB.id(), "FederatedEditable");
        var memberUid = UUID.randomUUID();
        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), memberUid, "Bob", null, "Erste");

        var updated = service.updateFederatedComment(station.id(), stationB.uid(), created.id(), memberUid, "Zweite");
        assertEquals("Zweite", updated.content());

        commentRepo.delete(created.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(82)
    void updateFederatedCommentRejectsForeignAuthorLocally() {
        var file = createFile(stationB.id(), "FederatedProtected");
        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), UUID.randomUUID(), "Bob", null, "Meins");
        var stranger = UUID.randomUUID();

        assertThrows(
                ForbiddenResponse.class,
                () -> service.updateFederatedComment(station.id(), stationB.uid(), created.id(), stranger, "Fremd"));

        commentRepo.delete(created.id());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(83)
    void deleteFederatedCommentLocally() {
        var file = createFile(stationB.id(), "FederatedDeletable");
        var memberUid = UUID.randomUUID();
        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), memberUid, "Bob", null, "Weg damit");

        assertTrue(service.deleteFederatedComment(station.id(), stationB.uid(), created.id(), memberUid));
        assertTrue(commentRepo.findById(created.id()).isEmpty());

        knowledgeBaseRepo.deleteFile(file.id());
    }

    @Test
    @Order(84)
    void federatedCommentsRejectUnknownPartner() {
        var unknown = UUID.randomUUID();
        assertThrows(NotFoundResponse.class, () -> service.listFederatedComments(station.id(), unknown, 1));
    }

    @Test
    @Order(90)
    void listFederatedCommentsViaHttp() {
        when(httpClient.getList(
                        eq(REMOTE_HOST), pathIs("/remote/kb/files/7/comments"), any(), eq(station.id()), any(), any()))
                .thenReturn(List.of());

        assertTrue(
                service.listFederatedComments(station.id(), stationC.uid(), 7).isEmpty());
    }

    @Test
    @Order(91)
    void createFederatedCommentViaHttp() {
        var memberUid = UUID.randomUUID();
        var remoteResponse =
                new CommentResponse(42, null, 7, null, null, null, "Bob", "Hallo", false, Instant.now(), null, null);
        when(httpClient.post(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/files/6/comments"),
                        any(),
                        any(),
                        eq(station.id()),
                        any(),
                        any()))
                .thenReturn(remoteResponse);

        var created = service.createFederatedComment(station.id(), stationC.uid(), 6, memberUid, "Bob", null, "Hallo");
        assertEquals(42, created.id());
        assertEquals("Hallo", created.content());
    }

    @Test
    @Order(92)
    void updateFederatedCommentViaHttp() {
        var memberUid = UUID.randomUUID();
        var remoteResponse =
                new CommentResponse(43, null, 7, null, null, null, "Bob", "Neu", false, Instant.now(), null, null);
        when(httpClient.put(
                        eq(REMOTE_HOST), pathIs("/remote/kb/comments/6"), any(), any(), eq(station.id()), any(), any()))
                .thenReturn(remoteResponse);

        var updated = service.updateFederatedComment(station.id(), stationC.uid(), 6, memberUid, "Neu");
        assertEquals(43, updated.id());
        assertEquals("Neu", updated.content());
    }

    @Test
    @Order(95)
    void createFederatedCommentViaHttpFailure() {
        var memberUid = UUID.randomUUID();
        when(httpClient.post(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/files/7/comments"),
                        any(),
                        any(),
                        eq(station.id()),
                        any(),
                        any()))
                .thenReturn(null);

        assertThrows(
                InternalServerErrorResponse.class,
                () -> service.createFederatedComment(station.id(), stationC.uid(), 7, memberUid, "Bob", null, "Hallo"));
    }

    @Test
    @Order(96)
    void updateFederatedCommentViaHttpFailure() {
        var memberUid = UUID.randomUUID();
        when(httpClient.put(
                        eq(REMOTE_HOST), pathIs("/remote/kb/comments/7"), any(), any(), eq(station.id()), any(), any()))
                .thenReturn(null);

        assertThrows(
                InternalServerErrorResponse.class,
                () -> service.updateFederatedComment(station.id(), stationC.uid(), 7, memberUid, "Neu"));
    }

    /**
     * The partner authorises the delete against the acting member, so the request has to carry that
     * member's uid in its body. Sending a bodyless DELETE makes the partner reject the call.
     */
    @Test
    @Order(93)
    void deleteFederatedCommentViaHttp() {
        var memberUid = UUID.randomUUID();
        when(httpClient.delete(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/comments/8"),
                        argThat(body -> body != null && body.toString().contains(memberUid.toString())),
                        any(),
                        eq(station.id()),
                        any()))
                .thenReturn(true);

        assertTrue(service.deleteFederatedComment(station.id(), stationC.uid(), 8, memberUid));
    }

    @Test
    @Order(94)
    void deleteFederatedCommentViaHttpFailure() {
        var memberUid = UUID.randomUUID();
        when(httpClient.delete(eq(REMOTE_HOST), pathIs("/remote/kb/comments/9"), any(), any(), eq(station.id()), any()))
                .thenReturn(false);

        assertThrows(
                InternalServerErrorResponse.class,
                () -> service.deleteFederatedComment(station.id(), stationC.uid(), 9, memberUid));
    }

    @Test
    @Order(100)
    void federationRecords() {
        var summary = new KbFileSummary(1, 2, null, "Test", "Desc", KbFileType.MARKDOWN, Instant.now(), false);

        var shared = new KnowledgeBaseFederationService.SharedKbItem(summary, 2, 3);
        assertEquals(1, shared.file().id());
        assertEquals(2, shared.sourceStationId());
        assertEquals(3, shared.partnerId());

        var result = new KnowledgeBaseFederationService.FederatedSearchResult(summary, "snippet", "Station", "uid-123");
        assertEquals("snippet", result.snippet());
        assertEquals("Station", result.stationName());
        assertEquals("uid-123", result.stationUid());

        var item = new KnowledgeBaseFederationService.FederatedKbItem(4, "Title", "Desc", "Station", "uid-456", 6);
        assertEquals(4, item.remoteId());
        assertEquals("Title", item.title());
        assertEquals("uid-456", item.stationUid());

        var served = new KnowledgeBaseFederationService.RemoteKbFileSummary(7, "Name", "Desc", "MARKDOWN", "now");
        assertEquals(7, served.id());
        assertEquals("MARKDOWN", served.fileType());

        var match = new KnowledgeBaseFederationService.RemoteKbSearchResultItem(8, "Name", "Desc", "Snippet");
        assertEquals(8, match.id());
        assertEquals("Snippet", match.snippet());

        var rendered = new KnowledgeBaseFederationService.RenderedPdf("Leitfaden.pdf", new byte[] {1, 2});
        assertEquals("Leitfaden.pdf", rendered.fileName());
        assertEquals(2, rendered.data().length);
    }
}
