/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.KnowledgeBaseRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.FederationTestContracts;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavouriteTarget;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KbFavouriteRepository;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private static FederationTestTransport transport;
    private static Station station;
    private static Station stationB;
    private static Station stationC;
    private static Account account;
    private static StationMember member;
    private static FederationPartner requestingPartner;
    private static KbAccessService accessService;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = TestFederationServices.of(federationRepo, stationRepo);
        httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        var storageConfig = new Storage();
        var fileStorage = mock(KbFileStorageService.class);
        var searchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        contentService = new KbContentService(
                knowledgeBaseRepo, contentBlocks(), noCellDescriptions(), stationRepo, fileStorage, searchService);
        kbService = new KnowledgeBaseService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo, privateTags),
                new KbPresentationService(knowledgeBaseRepo, fileStorage, contentService, new TaskScheduler()),
                new KbLinkMetadataService(new OutboundHttp(new RemoteUrlValidator(new Federation(), new Demo()))),
                new PresentationCompressor(storageConfig),
                new PdfCompressor(storageConfig),
                new ClusterAutoShareService(new ClusterRepository(), new FederationRepository()));
        accessService = new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo, privateTags);
        transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        service = new KnowledgeBaseFederationService(
                kbService,
                contentService,
                searchService,
                federationService,
                federationRepo,
                transport.transport(),
                stationRepo,
                newCommentService(new DomainEventBus(Set.of())),
                mock(EventFederationRepository.class),
                memberNameResolver,
                new FederationFanout(new TaskScheduler()),
                new FederationEntityResolver(federationRepo),
                mock(KbPdfExportService.class),
                accessService);
        transport.serve(service);

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
        knowledgeBaseRepo.purgeFile(file.id());
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

        var level = service.browseSharedKb(station.id());
        assertTrue(level.folders().stream().anyMatch(shared -> shared.id() == folder.id()));
        assertTrue(
                level.files().stream().noneMatch(item -> item.file().id() == file.id()),
                "the article sits inside the shared folder, not loose beside it");

        var inside = service.browseFederatedKbFolder(station.id(), stationB.uid(), folder.id(), StationUserType.MEMBER);
        assertTrue(inside.files().stream().anyMatch(item -> item.remoteId() == file.id()));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.purgeFile(file.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
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

        var top = service.browseFederatedKb(station.id(), StationUserType.MEMBER);
        var served = top.folders().stream()
                .filter(candidate -> candidate.remoteId() == outer.id())
                .findFirst()
                .orElseThrow();
        assertEquals("Outer", served.title());
        assertEquals("the shared one", served.description());
        assertEquals(stationB.name(), served.stationName());

        assertTrue(
                top.folders().stream().noneMatch(candidate -> candidate.remoteId() == inner.id()),
                "the subfolder is offered inside the shared one, not beside it at the top");
        var opened = service.browseFederatedKbFolder(station.id(), stationB.uid(), outer.id(), StationUserType.MEMBER);
        assertTrue(opened.folders().stream().anyMatch(candidate -> candidate.remoteId() == inner.id()));

        var deepLevel =
                service.browseFederatedKbFolder(station.id(), stationB.uid(), inner.id(), StationUserType.MEMBER);
        assertTrue(deepLevel.files().stream().anyMatch(candidate -> candidate.remoteId() == deep.id()));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.purgeFile(deep.id());
        knowledgeBaseRepo.purgeFolder(inner.id());
        knowledgeBaseRepo.purgeFolder(outer.id());
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
        knowledgeBaseRepo.purgeFile(forOne.id());
    }

    /**
     * A folder for named stations holding an article for a different one is a contradiction, so it is
     * refused. Narrowing the article to nobody stands, because it says less than the folder, not more.
     */
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

        var naming = assertThrows(
                RefusalResponse.class,
                () -> service.shareEntry(
                        stationB.id(), inside.id(), null, ShareScope.SPECIFIC, List.of(servingSide.id() + 9999)));
        var widening = assertThrows(
                RefusalResponse.class,
                () -> service.shareEntry(stationB.id(), inside.id(), null, ShareScope.ALL_PARTNERS, List.of()));
        assertEquals(KnowledgeBaseRefusal.KB_SHARE_NAMES_STATIONS_ITS_FOLDER_DOES_NOT, naming.refusal());
        assertEquals(KnowledgeBaseRefusal.KB_SHARE_WIDER_THAN_ITS_FOLDER, widening.refusal());

        var narrowed = service.shareEntry(stationB.id(), inside.id(), null, ShareScope.SPECIFIC, List.of());

        federationRepo.deleteKbShare(narrowed.id(), stationB.id());
        federationRepo.deleteKbShare(folderShare.id(), stationB.id());
        knowledgeBaseRepo.purgeFile(inside.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    /**
     * A user type set on a shared entry means the reader's own type at their own station. The station
     * serving the entry never learns who is reading, so the audience travels and is applied at the far end.
     */
    @Test
    @Order(3)
    void aUserTypeOnASharedEntryIsTheReadersOwn() {
        var forTeam = knowledgeBaseRepo.createFile(
                stationB.id(), null, "TeamOnly", "desc", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
        var share = federationRepo.createKbShare(stationB.id(), forTeam.id(), null, ShareScope.ALL_PARTNERS);
        accessService.setRestrictions(
                null,
                forTeam.id(),
                new RestrictionSelection(
                        List.of(StationUserType.TEAM), List.of(), List.of(), List.of(), RestrictionMode.AND));

        assertTrue(service.browseFederatedKb(station.id(), StationUserType.TEAM).files().stream()
                .anyMatch(item -> item.remoteId() == forTeam.id()));
        assertTrue(service.browseFederatedKb(station.id(), StationUserType.MEMBER).files().stream()
                .noneMatch(item -> item.remoteId() == forTeam.id()));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.purgeFile(forTeam.id());
    }

    /**
     * Saying who an entry is for replaces what it said before, and the old share goes only once the new
     * one exists: a refusal in between would leave the entry shared with nobody and nobody the wiser.
     */
    @Test
    @Order(3)
    void sayingWhoAnEntryIsForReplacesWhatItSaidBefore() {
        var file = knowledgeBaseRepo.createFile(
                stationB.id(), null, "Audienced", "desc", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
        var servingSide = federationRepo
                .findPartnerByStationAndRemoteUid(stationB.id(), station.uid())
                .orElseThrow();

        service.setAudience(stationB.id(), file.id(), null, ShareScope.ALL_PARTNERS, List.of());
        var everybody = service.findAudiences(stationB.id()).stream()
                .filter(audience -> Objects.equals(audience.fileId(), file.id()))
                .toList();
        assertEquals(1, everybody.size());
        assertEquals(ShareScope.ALL_PARTNERS, everybody.getFirst().scope());
        assertTrue(everybody.getFirst().partnerIds().isEmpty());

        service.setAudience(stationB.id(), file.id(), null, ShareScope.SPECIFIC, List.of(servingSide.id()));
        var named = service.findAudiences(stationB.id()).stream()
                .filter(audience -> Objects.equals(audience.fileId(), file.id()))
                .toList();
        assertEquals(1, named.size(), "the old share is gone rather than standing beside the new one");
        assertEquals(ShareScope.SPECIFIC, named.getFirst().scope());
        assertEquals(List.of(servingSide.id()), named.getFirst().partnerIds());

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.setAudience(stationB.id(), file.id(), 1, ShareScope.ALL_PARTNERS, List.of()));
        assertEquals(KnowledgeBaseRefusal.KB_AUDIENCE_NEEDS_ONE_ENTRY, refused.refusal());

        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(4)
    void browseFederatedKbCarriesPartnerStationName() {
        var file = createFile(stationB.id(), "NamedFedFile");
        var share = federationRepo.createKbShare(stationB.id(), file.id(), null, ShareScope.ALL_PARTNERS);

        var items =
                service.browseFederatedKb(station.id(), StationUserType.MEMBER).files();
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
        knowledgeBaseRepo.purgeFile(file.id());
    }

    /**
     * A partner on this instance is reachable without leaving the process, which is no reason to
     * answer more than it shares: a search across partners returns what each of them shared and
     * nothing else, the same as it would over the wire.
     */
    @Test
    @Order(5)
    void searchFederatedKbFindsOnlyWhatAPartnerShares() {
        var file = createFile(stationB.id(), "Loeschangriff");
        knowledgeBaseRepo.storeTextContent(file.id(), "Ablauf beim Loeschangriff");
        knowledgeBaseRepo.updateSearchIndex(file.id(), "Loeschangriff Ablauf", "german");
        var folder = knowledgeBaseRepo.createFolder(stationB.id(), null, "Interner Ablauf", "", member.id());
        var kept = knowledgeBaseRepo.createFile(
                stationB.id(),
                folder.id(),
                "Interner Loeschangriff",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                member.id());
        knowledgeBaseRepo.updateSearchIndex(kept.id(), "Loeschangriff intern", "german");

        assertTrue(service.searchFederatedKb(station.id(), "Loeschangriff").isEmpty());

        var share = federationRepo.createKbShare(stationB.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        var results = service.searchFederatedKb(station.id(), "Loeschangriff");
        assertTrue(results.stream().anyMatch(result -> result.file().id() == file.id()));
        assertTrue(results.stream().noneMatch(result -> result.file().id() == kept.id()));
        assertTrue(results.stream().allMatch(result -> result.stationName() != null));

        federationRepo.deleteKbShare(share.id(), stationB.id());
        knowledgeBaseRepo.purgeFile(kept.id());
        knowledgeBaseRepo.purgeFile(file.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    @Test
    @Order(6)
    void browseSharedKbViaHttp() {
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/browse"),
                        any(),
                        eq(station.id()),
                        eq(KnowledgeBaseFederationService.RemoteKbBrowse.class)))
                .thenReturn(new KnowledgeBaseFederationService.RemoteKbBrowse(
                        List.of(),
                        List.of(new KnowledgeBaseFederationService.RemoteKbFileSummary(
                                99, "RemoteFile", "remote desc", "MARKDOWN", "now", List.of())),
                        List.of()));

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
                        eq(KnowledgeBaseFederationService.RemoteKbBrowse.class)))
                .thenReturn(new KnowledgeBaseFederationService.RemoteKbBrowse(
                        List.of(),
                        List.of(new KnowledgeBaseFederationService.RemoteKbFileSummary(
                                98, "TypeLess", "no type", null, "now", List.of())),
                        List.of()));

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
        var file = sharedFile("FedDetail");
        var result = service.getFederatedKbFile(station.id(), stationB.uid(), file.id());
        assertEquals(file.id(), result.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(21)
    void getFederatedKbFileRejectsForeignFile() {
        var file = createFile(station.id(), "OwnFile");
        assertThrows(RefusalResponse.class, () -> service.getFederatedKbFile(station.id(), stationB.uid(), file.id()));
        knowledgeBaseRepo.purgeFile(file.id());
    }

    /**
     * A partner on this instance used to open, read and copy any file of the station it is paired
     * with, shared or not, while a partner on another instance was refused.
     */
    @Test
    @Order(21)
    void anUnsharedFileOfAPartnerHereIsRefused() {
        var file = createFile(stationB.id(), "Private");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Intern");
        assertThrows(RefusalResponse.class, () -> service.getFederatedKbFile(station.id(), stationB.uid(), file.id()));
        assertThrows(
                RefusalResponse.class,
                () -> service.getFederatedKbFileContent(station.id(), stationB.uid(), file.id()));
        assertThrows(
                RefusalResponse.class, () -> service.copyKbFile(station.id(), stationB.uid(), file.id(), member.id()));
        knowledgeBaseRepo.purgeFile(file.id());
    }

    /**
     * A file of a station nobody here is paired with used to be copied straight from this instance's own
     * storage, text and all, by its number. Without a partnership there is nobody to ask, so it is refused.
     */
    @Test
    @Order(21)
    void aFileOfAnUnpairedStationIsNotCopied() {
        var stranger = stationRepo.create("KbFedStranger");
        var file = createFile(stranger.id(), "Fremd");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Geheim");

        assertThrows(
                RefusalResponse.class, () -> service.copyKbFile(station.id(), stranger.uid(), file.id(), member.id()));
        assertTrue(kbService.findFiles(station.id(), null).stream()
                .noneMatch(found -> found.name().equals("Fremd")));

        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(22)
    void getFederatedKbFileContentLocal() {
        var file = sharedFile("FedContent");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Content");
        assertTrue(service.getFederatedKbFileContent(station.id(), stationB.uid(), file.id())
                .contains("Content"));
        knowledgeBaseRepo.purgeFile(file.id());
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
        when(httpClient.get(eq(REMOTE_HOST), pathIs("/remote/kb/files/77"), any(), eq(station.id()), any()))
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
                        eq(RemoteKnowledgeBaseRoutes.FileContentResponse.class)))
                .thenReturn(null);

        var refused = assertThrows(
                RefusalResponse.class, () -> service.getFederatedKbFileContent(station.id(), stationC.uid(), 56));
        assertEquals(FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER, refused.refusal());
    }

    @Test
    @Order(26)
    void copyKbFileFromLocalPartner() {
        var file = sharedFile("CopySource");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Copy Me");

        var copied = service.copyKbFile(station.id(), stationB.uid(), file.id(), member.id());
        assertEquals("CopySource", copied.name());
        assertEquals(station.id(), copied.stationId());
        assertNotEquals(file.id(), copied.id());
        assertTrue(contentService.getMarkdownContent(copied.id()).orElseThrow().contains("Copy Me"));

        knowledgeBaseRepo.purgeFile(copied.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    /** Copying a partner's file one keeps at hand must not lose it from the favourites. */
    @Test
    @Order(27)
    void copyKbFileKeepsFavouriteMarking() {
        var file = sharedFile("FavouriteSource");
        knowledgeBaseRepo.storeTextContent(file.id(), "# Fav");
        var favouriteRepo = new KbFavouriteRepository();
        var favourites = new KbFavouriteService(favouriteRepo, kbService, accessService, service, stationRepo);
        favouriteRepo.addPartner(
                member.id(),
                KbFavouriteTarget.PARTNER_FILE,
                stationB.uid(),
                file.id(),
                "FavouriteSource",
                "MARKDOWN",
                "Station B");

        var copied = service.copyKbFile(station.id(), stationB.uid(), file.id(), member.id());
        favourites.carryOverToCopy(member.id(), copied.id());

        assertTrue(favouriteRepo
                .findLocal(member.id(), KbFavouriteTarget.FILE, copied.id())
                .isPresent());

        favourites
                .list(accessService.memberAccess(member.id(), StationUserType.MEMBER))
                .forEach(favourite -> favourites.unmark(member.id(), favourite.id()));
        knowledgeBaseRepo.purgeFile(copied.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(28)
    void copyKbFileFromRemotePartner() {
        var remoteFile = new RemoteKnowledgeBaseRoutes.RemoteKbFile(
                78,
                stationC.uid(),
                "RemoteCopySource",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null);
        when(httpClient.get(eq(REMOTE_HOST), pathIs("/remote/kb/files/78"), any(), eq(station.id()), any()))
                .thenReturn(remoteFile);
        when(httpClient.get(
                        eq(REMOTE_HOST),
                        pathIs("/remote/kb/files/78/content"),
                        any(),
                        eq(station.id()),
                        eq(RemoteKnowledgeBaseRoutes.FileContentResponse.class)))
                .thenReturn(new RemoteKnowledgeBaseRoutes.FileContentResponse(78, "# From remote"));

        var copied = service.copyKbFile(station.id(), stationC.uid(), 78, member.id());
        assertEquals("RemoteCopySource", copied.name());
        assertTrue(contentService.getMarkdownContent(copied.id()).orElseThrow().contains("From remote"));

        knowledgeBaseRepo.purgeFile(copied.id());
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
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(41)
    void searchForPartnerWithoutQuery() {
        assertTrue(service.searchForPartner(requestingPartner, null).isEmpty());
        assertTrue(service.searchForPartner(requestingPartner, "  ").isEmpty());
    }

    /**
     * A search answers what the station shares, by the same rule that decides whether a single
     * article may be opened: shared in its own right, or sitting below a shared folder. Anything
     * else stays out, however well it matches.
     */
    @Test
    @Order(42)
    void searchForPartnerOnlyReturnsSharedMatches() {
        var shared = createFile(station.id(), "Atemschutz");
        var unshared = createFile(station.id(), "Atemschutzgeraet");
        var folder = knowledgeBaseRepo.createFolder(station.id(), null, "Atemschutzordner", "", member.id());
        var inFolder = knowledgeBaseRepo.createFile(
                station.id(),
                folder.id(),
                "AtemschutzImOrdner",
                "desc",
                KbFileType.MARKDOWN,
                "text/markdown",
                0,
                null,
                member.id());
        knowledgeBaseRepo.updateSearchIndex(shared.id(), "Atemschutz", "german");
        knowledgeBaseRepo.updateSearchIndex(unshared.id(), "Atemschutz", "german");
        knowledgeBaseRepo.updateSearchIndex(inFolder.id(), "Atemschutz", "german");
        var share = federationRepo.createKbShare(station.id(), shared.id(), null, ShareScope.ALL_PARTNERS);

        var results = service.searchForPartner(requestingPartner, "Atemschutz");
        assertTrue(results.stream().anyMatch(result -> result.id() == shared.id()));
        assertFalse(results.stream().anyMatch(result -> result.id() == unshared.id()));
        assertFalse(results.stream().anyMatch(result -> result.id() == inFolder.id()));

        var folderShare = federationRepo.createKbShare(station.id(), null, folder.id(), ShareScope.ALL_PARTNERS);
        assertTrue(service.searchForPartner(requestingPartner, "Atemschutz").stream()
                .anyMatch(result -> result.id() == inFolder.id()));

        federationRepo.deleteKbShare(folderShare.id(), station.id());
        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.purgeFile(inFolder.id());
        knowledgeBaseRepo.purgeFile(shared.id());
        knowledgeBaseRepo.purgeFile(unshared.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    /**
     * An article aimed at named stations is answered to those and to nobody else. A search that
     * only asked whether the station shares an article at all would hand it to every partner.
     */
    @Test
    @Order(42)
    void searchForPartnerHonoursTheStationsAnEntryNames() {
        var aimed = createFile(station.id(), "Kettensaege");
        knowledgeBaseRepo.updateSearchIndex(aimed.id(), "Kettensaege", "german");
        var share = federationService.createKbShare(station.id(), aimed.id(), null, ShareScope.SPECIFIC, List.of());

        assertTrue(service.searchForPartner(requestingPartner, "Kettensaege").stream()
                .noneMatch(result -> result.id() == aimed.id()));

        federationRepo.setKbShareTargets(share.id(), List.of(requestingPartner.id()));
        assertTrue(service.searchForPartner(requestingPartner, "Kettensaege").stream()
                .anyMatch(result -> result.id() == aimed.id()));

        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.purgeFile(aimed.id());
    }

    @Test
    @Order(43)
    void fileForPartnerRejectsForeignStation() {
        var file = createFile(stationB.id(), "ForeignFile");
        var refused = assertThrows(RefusalResponse.class, () -> service.fileForPartner(requestingPartner, file.id()));
        assertEquals(KnowledgeBaseRefusal.REMOTE_KB_FILE_NOT_SHARED, refused.refusal());
        knowledgeBaseRepo.purgeFile(file.id());
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

        var refused = assertThrows(RefusalResponse.class, () -> service.fileForPartner(requestingPartner, file.id()));
        assertEquals(KnowledgeBaseRefusal.REMOTE_KB_FILE_NOT_SHARED, refused.refusal());

        var share = federationRepo.createKbShare(station.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        assertEquals(
                "UnsharedFile",
                service.fileForPartner(requestingPartner, file.id()).name());

        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.purgeFile(file.id());
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
        knowledgeBaseRepo.purgeFile(file.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    @Test
    @Order(44)
    void fileForPartnerAnswersNotFoundForUnknownFile() {
        var refused = assertThrows(RefusalResponse.class, () -> service.fileForPartner(requestingPartner, 999999));
        assertEquals(KnowledgeBaseRefusal.REMOTE_KB_FILE_NOT_SHARED, refused.refusal());
    }

    @Test
    @Order(45)
    void fileContentForPartner() {
        var file = createFile(station.id(), "ServedContent");
        var share = federationRepo.createKbShare(station.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        knowledgeBaseRepo.storeTextContent(file.id(), "served text");
        assertEquals("served text", service.fileContentForPartner(requestingPartner, file.id()));
        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(46)
    void fileContentForPartnerWithoutStoredText() {
        var file = createFile(station.id(), "EmptyContent");
        var share = federationRepo.createKbShare(station.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        assertEquals("", service.fileContentForPartner(requestingPartner, file.id()));
        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(60)
    void listCommentsMapsAuthors() {
        var file = createFile(station.id(), "CommentedFile");
        var comment = commentRepo.create(
                CommentEntityType.KB,
                file.id(),
                null,
                null,
                new MemberIdentity(stationB.uid(), UUID.randomUUID()),
                "Partner sagt hallo");

        var responses = service.listComments(file.id());
        assertEquals(1, responses.size());
        assertEquals(comment.id(), responses.getFirst().id());
        assertEquals("Partner sagt hallo", responses.getFirst().content());

        commentRepo.delete(CommentEntityType.KB, comment.id());
        knowledgeBaseRepo.purgeFile(file.id());
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

        commentRepo.delete(CommentEntityType.KB, comment.id());
        knowledgeBaseRepo.purgeFile(file.id());
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

        commentRepo.delete(CommentEntityType.KB, comment.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(63)
    void updateRemoteCommentRejectsForeignAuthor() {
        var file = createFile(station.id(), "RemoteProtected");
        var comment = service.createRemoteComment(
                file.id(), requestingPartner.id(), UUID.randomUUID(), "Alice", null, "Meins");
        var stranger = UUID.randomUUID();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.updateRemoteComment(requestingPartner, comment.id(), stranger, "Fremd"));
        assertEquals(KnowledgeBaseRefusal.REMOTE_KB_COMMENT_NOT_YOURS_TO_EDIT, refused.refusal());

        commentRepo.delete(CommentEntityType.KB, comment.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(64)
    void requireRemoteCommentAuthorAnswersNotFound() {
        var stranger = UUID.randomUUID();
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.requireRemoteCommentAuthor(
                        requestingPartner,
                        999999,
                        stranger,
                        KnowledgeBaseRefusal.REMOTE_KB_COMMENT_NOT_YOURS_TO_DELETE));
        assertEquals(KnowledgeBaseRefusal.REMOTE_KB_COMMENT_NOT_HERE, refused.refusal());
    }

    @Test
    @Order(80)
    void createAndListFederatedCommentsLocally() {
        var file = sharedFile("FederatedComments");
        var memberUid = UUID.randomUUID();

        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), memberUid, "Bob", null, "Frage");
        assertEquals("Frage", created.content());
        assertEquals(
                station.uid(),
                commentRepo
                        .findById(CommentEntityType.KB, created.id())
                        .orElseThrow()
                        .author()
                        .stationUid());

        var listed = service.listFederatedComments(station.id(), stationB.uid(), file.id());
        assertEquals(1, listed.size());
        assertEquals(created.id(), listed.getFirst().id());
        var asking = federationRepo
                .findPartnerByStationAndRemoteUid(station.id(), stationB.uid())
                .orElseThrow();
        transport.assertParity(
                asking, RemoteKnowledgeBaseRoutes.LIST_COMMENTS.at(file.id()), null, CommentResponse.class);
        transport.assertParity(
                asking,
                RemoteKnowledgeBaseRoutes.GET_FILE.at(file.id()),
                null,
                RemoteKnowledgeBaseRoutes.RemoteKbFile.class);

        commentRepo.delete(CommentEntityType.KB, created.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    /**
     * A partner on this instance used to read and write comments on any file of the station it is
     * paired with, shared or not, while a partner on another instance was refused.
     */
    @Test
    @Order(80)
    void commentsOnAFileNotSharedWithAPartnerHereAreRefused() {
        var file = createFile(stationB.id(), "NotShared");
        assertThrows(
                RefusalResponse.class, () -> service.listFederatedComments(station.id(), stationB.uid(), file.id()));
        assertThrows(
                RefusalResponse.class,
                () -> service.createFederatedComment(
                        station.id(), stationB.uid(), file.id(), UUID.randomUUID(), "Bob", null, "Nein"));
        assertTrue(commentRepo.findByTarget(CommentEntityType.KB, file.id()).isEmpty());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(81)
    void updateFederatedCommentLocally() {
        var file = sharedFile("FederatedEditable");
        var memberUid = UUID.randomUUID();
        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), memberUid, "Bob", null, "Erste");

        var updated = service.updateFederatedComment(station.id(), stationB.uid(), created.id(), memberUid, "Zweite");
        assertEquals("Zweite", updated.content());

        commentRepo.delete(CommentEntityType.KB, created.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(82)
    void updateFederatedCommentRejectsForeignAuthorLocally() {
        var file = sharedFile("FederatedProtected");
        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), UUID.randomUUID(), "Bob", null, "Meins");
        var stranger = UUID.randomUUID();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.updateFederatedComment(station.id(), stationB.uid(), created.id(), stranger, "Fremd"));
        assertEquals(KnowledgeBaseRefusal.REMOTE_KB_COMMENT_NOT_YOURS_TO_EDIT, refused.refusal());

        commentRepo.delete(CommentEntityType.KB, created.id());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(83)
    void deleteFederatedCommentLocally() {
        var file = sharedFile("FederatedDeletable");
        var memberUid = UUID.randomUUID();
        var created = service.createFederatedComment(
                station.id(), stationB.uid(), file.id(), memberUid, "Bob", null, "Weg damit");

        service.deleteFederatedComment(station.id(), stationB.uid(), created.id(), memberUid);
        assertTrue(commentRepo.findById(CommentEntityType.KB, created.id()).isEmpty());

        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    @Order(84)
    void federatedCommentsRejectUnknownPartner() {
        var unknown = UUID.randomUUID();
        var refused =
                assertThrows(RefusalResponse.class, () -> service.listFederatedComments(station.id(), unknown, 1));
        assertEquals(KnowledgeBaseRefusal.KB_COMMENT_PARTNER_NOT_HERE, refused.refusal());
    }

    @Test
    @Order(90)
    void listFederatedCommentsViaHttp() {
        when(httpClient.getList(eq(REMOTE_HOST), pathIs("/remote/kb/files/7/comments"), any(), eq(station.id()), any()))
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
                        eq(REMOTE_HOST), pathIs("/remote/kb/files/6/comments"), any(), any(), eq(station.id()), any()))
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
        when(httpClient.put(eq(REMOTE_HOST), pathIs("/remote/kb/comments/6"), any(), any(), eq(station.id()), any()))
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
                        eq(REMOTE_HOST), pathIs("/remote/kb/files/7/comments"), any(), any(), eq(station.id()), any()))
                .thenReturn(null);

        assertThrows(
                RefusalResponse.class,
                () -> service.createFederatedComment(station.id(), stationC.uid(), 7, memberUid, "Bob", null, "Hallo"));
    }

    @Test
    @Order(96)
    void updateFederatedCommentViaHttpFailure() {
        var memberUid = UUID.randomUUID();
        when(httpClient.put(eq(REMOTE_HOST), pathIs("/remote/kb/comments/7"), any(), any(), eq(station.id()), any()))
                .thenReturn(null);

        assertThrows(
                RefusalResponse.class,
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
                        eq(station.id())))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.deleteFederatedComment(station.id(), stationC.uid(), 8, memberUid));
    }

    @Test
    @Order(94)
    void deleteFederatedCommentViaHttpFailure() {
        var memberUid = UUID.randomUUID();
        when(httpClient.delete(eq(REMOTE_HOST), pathIs("/remote/kb/comments/9"), any(), any(), eq(station.id())))
                .thenReturn(false);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.deleteFederatedComment(station.id(), stationC.uid(), 9, memberUid));
        assertEquals(KnowledgeBaseRefusal.PARTNER_KB_COMMENT_NOT_DELETED, refused.refusal());
    }

    /** A file of station B shared with every partner. */
    private static KbFile sharedFile(String name) {
        var file = createFile(stationB.id(), name);
        federationRepo.createKbShare(stationB.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        return file;
    }

    @Test
    @Order(100)
    void federationRecords() {
        var summary = new KbFileSummary(1, 2, null, "Test", "Desc", KbFileType.MARKDOWN, Instant.now(), false);

        var shared = new KnowledgeBaseFederationService.SharedKbItem(summary, 2, 3, List.of());
        assertEquals(1, shared.file().id());
        assertEquals(2, shared.sourceStationId());
        assertEquals(3, shared.partnerId());

        var result = new KnowledgeBaseFederationService.FederatedSearchResult(summary, "snippet", "Station", "uid-123");
        assertEquals("snippet", result.snippet());
        assertEquals("Station", result.stationName());
        assertEquals("uid-123", result.stationUid());

        var item = new KnowledgeBaseFederationService.FederatedKbItem(
                4, "Title", "Desc", "Station", "uid-456", 6, List.of());
        assertEquals(4, item.remoteId());
        assertEquals("Title", item.title());
        assertEquals("uid-456", item.stationUid());

        var served = new KnowledgeBaseFederationService.RemoteKbFileSummary(
                7, "Name", "Desc", "MARKDOWN", "now", List.of("TEAM"));
        assertEquals(7, served.id());
        assertEquals("MARKDOWN", served.fileType());
        assertEquals(List.of("TEAM"), served.userTypes());

        var match = new KnowledgeBaseFederationService.RemoteKbSearchResultItem(8, "Name", "Desc", "Snippet");
        assertEquals(8, match.id());
        assertEquals("Snippet", match.snippet());

        var rendered = new KnowledgeBaseFederationService.RenderedPdf("Leitfaden.pdf", new byte[] {1, 2});
        assertEquals("Leitfaden.pdf", rendered.fileName());
        assertEquals(2, rendered.data().length);
    }
}
