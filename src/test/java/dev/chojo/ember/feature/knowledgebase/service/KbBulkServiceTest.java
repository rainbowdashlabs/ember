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
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers doing one thing to a whole selection: what part of it goes through, how the rest is named
 * back, and the tagging form that adds and removes rather than replacing.
 */
class KbBulkServiceTest extends RepositoryTestBase {
    private static KbBulkService service;
    private static KbAccessService accessService;
    private static KbTagService tagService;
    private static KbTrashService trashService;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        var federationRepo = new FederationRepository();
        var federation = new FederationService(federationRepo, stationRepo, new Api());
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
        var kbFederation = new KnowledgeBaseFederationService(
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
        tagService = new KbTagService(knowledgeBaseRepo);
        trashService = new KbTrashService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                searchService,
                accessService,
                new KbAuthorNameService(stationMemberRepo, accountRepo),
                pageRepo);
        service = new KbBulkService(
                knowledgeBaseRepo,
                new KbMoveService(knowledgeBaseRepo, accessService, kbFederation, stationRepo),
                tagService,
                accessService,
                trashService);

        station = stationRepo.create("KbBulkStation");
        account = accountRepo.create("kb-bulk@test.com", "Kb", "Bulker");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static MemberAccess manager() {
        return new MemberAccess(member.id(), StationUserType.MEMBER, List.of(), List.of(), true, true);
    }

    private static MemberAccess editor() {
        return new MemberAccess(member.id(), StationUserType.MEMBER, List.of(), List.of(), true, false);
    }

    private static KbFolder folder(Integer parentId, String name) {
        return knowledgeBaseRepo.createFolder(station.id(), parentId, name, "", member.id());
    }

    private static KbFile file(Integer folderId, String name) {
        return knowledgeBaseRepo.createFile(
                station.id(), folderId, name, "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
    }

    private static void grant(Integer folderId, Integer fileId, KbAccessLevel level) {
        accessService.setGrants(
                folderId,
                fileId,
                List.of(new KbAccessService.GrantEntry(StationUserType.MEMBER, null, null, null, level)));
    }

    /**
     * The point of naming refusals: a reader who marked twenty entries is told which one stayed
     * behind rather than being sent to walk their own selection looking for it.
     */
    @Test
    void whatCanMoveMovesAndTheRestComesBackByName() {
        var target = folder(null, "bulk-target");
        var movable = folder(null, "bulk-movable");
        var article = file(null, "bulk-article");
        var locked = file(null, "bulk-locked");
        grant(null, locked.id(), KbAccessLevel.READ);

        var outcome = service.move(
                editor(), station.id(), List.of(movable.id()), List.of(article.id(), locked.id()), target.id());

        assertEquals(List.of(movable.id()), outcome.doneFolderIds());
        assertEquals(List.of(article.id()), outcome.doneFileIds());
        assertEquals(1, outcome.refusedTotal());
        assertEquals("bulk-locked", outcome.refused().getFirst().name());
        assertEquals(KbRefusalReason.NO_PERMISSION, outcome.refused().getFirst().reason());

        grant(null, locked.id(), null);
        accessService.setGrants(null, locked.id(), List.of());
        knowledgeBaseRepo.purgeFile(locked.id());
        knowledgeBaseRepo.purgeFolder(target.id());
    }

    /**
     * Past the point where a message stays readable the result stops naming entries, but the total
     * still says how many there were, so the count never disagrees with what happened.
     */
    @Test
    void aVeryLongListOfRefusalsStopsNamingButKeepsCounting() {
        var target = folder(null, "bulk-many-target");
        var missing = new ArrayList<Integer>();
        for (int i = 0; i < 14; i++) missing.add(900000 + i);

        var outcome = service.move(manager(), station.id(), List.of(), missing, target.id());

        assertEquals(14, outcome.refusedTotal());
        assertEquals(10, outcome.refused().size());
        assertTrue(outcome.doneFileIds().isEmpty());

        knowledgeBaseRepo.purgeFolder(target.id());
    }

    /**
     * The trap the setting form would walk into: tagging twenty entries with one name would strip
     * every other tag off all twenty.
     */
    @Test
    void taggingAddsWithoutWipingTheTagsAnEntryAlreadyHas() {
        var folder = folder(null, "bulk-tag-folder");
        var article = file(null, "bulk-tag-file");
        tagService.setFolderTags(folder.id(), List.of("bestand"), station.id());
        tagService.setFileTags(article.id(), List.of("bestand"), station.id());

        var outcome = service.tag(
                manager(), station.id(), List.of(folder.id()), List.of(article.id()), List.of("ausbildung"), List.of());

        assertEquals(0, outcome.refusedTotal());
        assertEquals(
                List.of("ausbildung", "bestand"),
                tagService.findFolderTags(folder.id()).stream()
                        .map(t -> t.name())
                        .toList());
        assertEquals(
                List.of("ausbildung", "bestand"),
                tagService.findFileTags(article.id()).stream()
                        .map(t -> t.name())
                        .toList());

        knowledgeBaseRepo.purgeFile(article.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    @Test
    void taggingRemovesOnlyTheNamesItIsGiven() {
        var folder = folder(null, "bulk-untag-folder");
        var article = file(null, "bulk-untag-file");
        tagService.setFolderTags(folder.id(), List.of("bestand", "ausbildung"), station.id());
        tagService.setFileTags(article.id(), List.of("bestand", "ausbildung"), station.id());

        service.tag(
                manager(),
                station.id(),
                List.of(folder.id()),
                List.of(article.id()),
                List.of(),
                List.of("bestand", "  ", "unbekannt"));

        assertEquals(
                List.of("ausbildung"),
                tagService.findFolderTags(folder.id()).stream()
                        .map(t -> t.name())
                        .toList());
        assertEquals(
                List.of("ausbildung"),
                tagService.findFileTags(article.id()).stream()
                        .map(t -> t.name())
                        .toList());

        knowledgeBaseRepo.purgeFile(article.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    @Test
    void taggingNeedsWriteOnEveryEntryItTouches() {
        var readOnlyFolder = folder(null, "bulk-tag-readonly-folder");
        var readOnlyFile = file(null, "bulk-tag-readonly-file");
        grant(readOnlyFolder.id(), null, KbAccessLevel.READ);
        grant(null, readOnlyFile.id(), KbAccessLevel.READ);

        var outcome = service.tag(
                editor(),
                station.id(),
                List.of(readOnlyFolder.id(), 900001),
                List.of(readOnlyFile.id(), 900002),
                List.of("ausbildung"),
                List.of());

        assertEquals(4, outcome.refusedTotal());
        assertTrue(outcome.doneFolderIds().isEmpty());
        assertTrue(outcome.doneFileIds().isEmpty());
        assertTrue(outcome.refused().stream()
                .anyMatch(entry -> entry.reason() == KbRefusalReason.NOT_FOUND && entry.name() == null));
        assertFalse(tagService.findFolderTags(readOnlyFolder.id()).stream()
                .anyMatch(tag -> tag.name().equals("ausbildung")));

        accessService.setGrants(readOnlyFolder.id(), null, List.of());
        accessService.setGrants(null, readOnlyFile.id(), List.of());
        knowledgeBaseRepo.purgeFile(readOnlyFile.id());
        knowledgeBaseRepo.purgeFolder(readOnlyFolder.id());
    }

    /**
     * The button this whole selection was waiting for. It asks for the same right a single delete
     * does, so one press reaches nothing twenty presses could not, and what it does is reversible,
     * which is why it may be pressed at all.
     */
    @Test
    void deletingASelectionPutsWhatItMayInTheTrashAndNamesTheRest() {
        var branch = folder(null, "bulk-delete-branch");
        var inside = file(branch.id(), "bulk-delete-inside");
        var article = file(null, "bulk-delete-article");
        var locked = file(null, "bulk-delete-locked");
        grant(null, locked.id(), KbAccessLevel.READ);

        var outcome = service.delete(
                editor(), station.id(), member.id(), List.of(branch.id()), List.of(article.id(), locked.id(), 900003));

        assertEquals(List.of(branch.id()), outcome.doneFolderIds());
        assertEquals(List.of(article.id()), outcome.doneFileIds());
        assertEquals(2, outcome.refusedTotal());
        assertTrue(outcome.refused().stream()
                .anyMatch(entry ->
                        "bulk-delete-locked".equals(entry.name()) && entry.reason() == KbRefusalReason.NO_PERMISSION));
        assertTrue(knowledgeBaseRepo.findFileById(inside.id()).isEmpty(), "the folder took what was in it");
        assertTrue(knowledgeBaseRepo.findFileById(locked.id()).isPresent());

        accessService.setGrants(null, locked.id(), List.of());
        knowledgeBaseRepo.purgeFile(locked.id());
        trashService.purgeFile(article.id());
        trashService.purgeFolder(branch.id());
    }
}
