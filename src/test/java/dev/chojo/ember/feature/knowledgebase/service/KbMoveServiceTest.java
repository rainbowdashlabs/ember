/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.FederationTestContracts;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.KbRefusalReason;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.knowledgebase.service.KbMoveService.KbReach;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers moving a knowledge-base entry: the checks that keep the tree a tree, the ones that keep a
 * move from widening a share somebody narrowed on purpose, and the reach preview the dialog shows
 * before anything happens.
 */
class KbMoveServiceTest extends RepositoryTestBase {
    private static KbMoveService service;
    private static KbAccessService accessService;
    private static KnowledgeBaseFederationService federationService;
    private static FederationRepository federationRepo;
    private static FederationService federation;
    private static Station station;
    private static Station partnerStation;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federation = new FederationService(federationRepo, stationRepo, new Api());
        var httpClient = mock(FederationHttpClient.class);
        var storageConfig = new Storage();
        var fileStorage = mock(KbFileStorageService.class);
        var searchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        var contentService = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                fileStorage,
                searchService);
        accessService = new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo);
        var kbService = new KnowledgeBaseService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                accessService,
                new KbPresentationService(knowledgeBaseRepo, fileStorage, contentService),
                new KbLinkMetadataService(new RemoteUrlValidator(new Federation(), new Demo())),
                new PresentationCompressor(storageConfig),
                new PdfCompressor(storageConfig),
                new ClusterAutoShareService(new ClusterRepository(), new FederationRepository()));
        federationService = new KnowledgeBaseFederationService(
                kbService,
                contentService,
                searchService,
                federation,
                federationRepo,
                httpClient,
                stationRepo,
                new KbCommentRepository(),
                mock(EventFederationRepository.class),
                memberNameResolver,
                new FederationFanout(),
                new FederationEntityResolver(federationRepo, stationRepo, httpClient),
                mock(KbPdfExportService.class),
                accessService);
        service = new KbMoveService(knowledgeBaseRepo, accessService, federationService, stationRepo);

        station = stationRepo.create("KbMoveStation");
        partnerStation = stationRepo.create("KbMovePartnerStation");
        account = accountRepo.create("kb-move@test.com", "Kb", "Mover");
        member = stationMemberRepo.create(station.id(), account.id());

        var keyPair = federation.generateKeyPair();
        federation.acceptInvite(station.id(), partnerStation.id(), federation.encodePublicKey(keyPair), null, null);
        var partner = federationRepo
                .findPartnerByStationAndRemoteUid(station.id(), partnerStation.uid())
                .orElseThrow();
        federation.setCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT, true);
        FederationTestContracts.storeCurrentContractOnRemotePartners(federation, federationRepo, station.id());
    }

    @AfterAll
    static void cleanup() {
        for (var partner : federation.findPartners(station.id())) federationRepo.deletePartner(partner.id());
        for (var partner : federation.findPartners(partnerStation.id())) federationRepo.deletePartner(partner.id());
        stationRepo.delete(station.id());
        stationRepo.delete(partnerStation.id());
        accountRepo.delete(account.id());
    }

    private static MemberAccess manager() {
        return new MemberAccess(member.id(), StationUserType.MEMBER, List.of(), List.of(), true, true);
    }

    private static MemberAccess reader() {
        return new MemberAccess(member.id(), StationUserType.MEMBER, List.of(), List.of(), false, false);
    }

    private static KbFolder folder(Integer parentId, String name) {
        return knowledgeBaseRepo.createFolder(station.id(), parentId, name, "", member.id());
    }

    private static KbFile file(Integer folderId, String name) {
        return knowledgeBaseRepo.createFile(
                station.id(), folderId, name, "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
    }

    @Test
    void aFolderMovesIntoAnotherOne() {
        var source = folder(null, "move-a-source");
        var target = folder(null, "move-a-target");

        var result = service.moveFolder(manager(), station.id(), source.id(), target.id());

        assertTrue(result.moved());
        assertEquals("move-a-source", result.name());
        assertEquals(
                target.id(),
                knowledgeBaseRepo.findFolderById(source.id()).orElseThrow().parentId());

        knowledgeBaseRepo.purgeFolder(target.id());
    }

    @Test
    void anArticleMovesIntoAFolderAndBackToTheRoot() {
        var target = folder(null, "move-file-target");
        var article = file(null, "move-file");

        assertTrue(service.moveFile(manager(), station.id(), article.id(), target.id())
                .moved());
        assertEquals(
                target.id(),
                knowledgeBaseRepo.findFileById(article.id()).orElseThrow().folderId());
        assertTrue(service.moveFile(manager(), station.id(), article.id(), null).moved());
        assertNull(knowledgeBaseRepo.findFileById(article.id()).orElseThrow().folderId());

        knowledgeBaseRepo.purgeFile(article.id());
        knowledgeBaseRepo.purgeFolder(target.id());
    }

    /**
     * A folder cannot be hung inside itself or inside anything under it. Without this the ancestry
     * walk that every permission check runs would loop rather than answer.
     */
    @Test
    void aFolderCannotBeHungInsideItself() {
        var top = folder(null, "cycle-top");
        var inner = folder(top.id(), "cycle-inner");
        var deep = folder(inner.id(), "cycle-deep");

        assertEquals(
                KbRefusalReason.TARGET_INSIDE,
                service.moveFolder(manager(), station.id(), top.id(), top.id()).reason());
        assertEquals(
                KbRefusalReason.TARGET_INSIDE,
                service.moveFolder(manager(), station.id(), top.id(), deep.id()).reason());
        assertEquals(
                top.id(),
                knowledgeBaseRepo.findFolderById(inner.id()).orElseThrow().parentId());

        knowledgeBaseRepo.purgeFolder(top.id());
    }

    @Test
    void aNameAlreadyInTheTargetIsRefusedByName() {
        var target = folder(null, "collision-target");
        var occupant = folder(target.id(), "Einsatz");
        var moving = folder(null, "Einsatz");

        var result = service.moveFolder(manager(), station.id(), moving.id(), target.id());

        assertFalse(result.moved());
        assertEquals(KbRefusalReason.NAME_TAKEN, result.reason());
        assertEquals("Einsatz", result.name());
        assertNull(knowledgeBaseRepo.findFolderById(moving.id()).orElseThrow().parentId());
        assertEquals(
                target.id(),
                knowledgeBaseRepo.findFolderById(occupant.id()).orElseThrow().parentId());

        knowledgeBaseRepo.purgeFolder(moving.id());
        knowledgeBaseRepo.purgeFolder(target.id());
    }

    @Test
    void movingNeedsFullRightsOnTheEntry() {
        var restricted = folder(null, "no-rights-folder");
        var article = file(null, "no-rights-file");
        accessService.setGrants(
                restricted.id(),
                null,
                List.of(new KbAccessService.GrantEntry(StationUserType.MEMBER, null, null, null, KbAccessLevel.WRITE)));
        accessService.setGrants(
                null,
                article.id(),
                List.of(new KbAccessService.GrantEntry(StationUserType.MEMBER, null, null, null, KbAccessLevel.WRITE)));

        assertEquals(
                KbRefusalReason.NO_PERMISSION,
                service.moveFolder(reader(), station.id(), restricted.id(), null)
                        .reason());
        assertEquals(
                KbRefusalReason.NO_PERMISSION,
                service.moveFile(reader(), station.id(), article.id(), null).reason());

        accessService.setGrants(restricted.id(), null, List.of());
        accessService.setGrants(null, article.id(), List.of());
        knowledgeBaseRepo.purgeFile(article.id());
        knowledgeBaseRepo.purgeFolder(restricted.id());
    }

    @Test
    void anEntryOfAnotherStationIsNotFound() {
        var elsewhere = knowledgeBaseRepo.createFolder(partnerStation.id(), null, "foreign", "", member.id());

        assertEquals(
                KbRefusalReason.NOT_FOUND,
                service.moveFolder(manager(), station.id(), elsewhere.id(), null)
                        .reason());
        assertEquals(
                KbRefusalReason.NOT_FOUND,
                service.moveFile(manager(), station.id(), 999999, null).reason());

        knowledgeBaseRepo.purgeFolder(elsewhere.id());
    }

    /**
     * A folder open to every partner cannot be slid under one aimed at named stations. Creating that
     * share is already refused, and a move that produced the same state would be a second way past
     * a rule somebody set on purpose.
     */
    @Test
    void aMoveCannotStretchAShareFurtherThanTheFolderAboveReaches() {
        var narrow = folder(null, "aim-narrow");
        var open = folder(null, "aim-open");
        var partner = federation.findPartners(station.id()).getFirst();
        var narrowShare = federationRepo.createKbShare(station.id(), null, narrow.id(), ShareScope.SPECIFIC);
        federationRepo.setKbShareTargets(narrowShare.id(), List.of(partner.id()));
        var openShare = federationRepo.createKbShare(station.id(), null, open.id(), ShareScope.ALL_PARTNERS);

        var result = service.moveFolder(manager(), station.id(), open.id(), narrow.id());

        assertFalse(result.moved());
        assertEquals(KbRefusalReason.SHARE_TOO_WIDE, result.reason());

        federationRepo.deleteKbShare(openShare.id(), station.id());
        federationRepo.deleteKbShare(narrowShare.id(), station.id());
        knowledgeBaseRepo.purgeFolder(open.id());
        knowledgeBaseRepo.purgeFolder(narrow.id());
    }

    @Test
    void aTargetThatCannotBeWrittenInIsRefusedOnceForTheWholeSelection() {
        var target = folder(null, "target-check");
        accessService.setGrants(
                target.id(),
                null,
                List.of(new KbAccessService.GrantEntry(StationUserType.MEMBER, null, null, null, KbAccessLevel.READ)));

        assertNull(service.checkTarget(manager(), station.id(), null));
        assertNull(service.checkTarget(manager(), station.id(), target.id()));
        assertEquals(KbRefusalReason.NO_PERMISSION, service.checkTarget(reader(), station.id(), target.id()));
        assertEquals(KbRefusalReason.NOT_FOUND, service.checkTarget(manager(), station.id(), 999999));

        accessService.setGrants(target.id(), null, List.of());
        knowledgeBaseRepo.purgeFolder(target.id());
    }

    /**
     * The dangerous direction: a station that publishes by default turns an article nobody ever
     * published into a public page as soon as it lands in a public folder.
     */
    @Test
    void thePreviewSaysWhenAMoveWouldPublishAnArticle() {
        stationRepo.updatePublicKbMode(station.id(), PublicKbMode.ALLOW_ALL);
        var hidden = folder(null, "preview-hidden");
        accessService.setPublicVisibility(hidden.id(), null, false);
        var article = file(hidden.id(), "preview-article");

        var preview = service.preview(station.id(), null, article.id(), null);

        assertEquals(KbReach.INTERNAL, preview.before());
        assertEquals(KbReach.PUBLIC, preview.after());

        accessService.removePublicVisibility(hidden.id(), null);
        stationRepo.updatePublicKbMode(station.id(), PublicKbMode.OFF);
        knowledgeBaseRepo.purgeFile(article.id());
        knowledgeBaseRepo.purgeFolder(hidden.id());
    }

    @Test
    void thePreviewMarksAnEntryOnlySomeReadersReachAsNarrow() {
        var restricted = folder(null, "preview-restricted");
        accessService.setRestrictions(
                restricted.id(),
                null,
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));

        var preview = service.preview(station.id(), restricted.id(), null, null);

        assertEquals(KbReach.NARROW, preview.before());
        assertEquals(KbReach.NARROW, preview.after());

        accessService.setRestrictions(restricted.id(), null, RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFolder(restricted.id());
    }

    @Test
    void thePreviewSaysWhenAMoveWouldHandAnArticleToEveryPartner() {
        var shared = folder(null, "preview-shared");
        var share = federationRepo.createKbShare(station.id(), null, shared.id(), ShareScope.ALL_PARTNERS);
        var article = file(null, "preview-moving");

        var preview = service.preview(station.id(), null, article.id(), shared.id());

        assertEquals(KbReach.INTERNAL, preview.before());
        assertEquals(KbReach.FEDERATED, preview.after());

        federationRepo.deleteKbShare(share.id(), station.id());
        knowledgeBaseRepo.purgeFile(article.id());
        knowledgeBaseRepo.purgeFolder(shared.id());
    }
}
