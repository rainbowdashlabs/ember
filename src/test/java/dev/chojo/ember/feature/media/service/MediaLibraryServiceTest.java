/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MediaLibraryServiceTest extends RepositoryTestBase {
    private static MediaLibraryService media;
    private static Station station;
    private static Account account;
    private static Account otherAccount;
    private static StationMember member;
    private static StationMember otherMember;
    private static int pageId;
    private static int containerId;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend();
        var storageService = new StorageService(new StorageBackendResolver(backend), backend);
        var storageConfig = new Storage();
        var storage = new MediaStorageService(storageService, stationRepo, backend);
        media = new MediaLibraryService(
                mediaFileRepo,
                mediaMetaRepo,
                storage,
                new MediaVariantService(storage, storageConfig),
                new MediaReferenceRegistry(contentContainerRepo),
                new StorageQuotaService(
                        storageUsageRepo,
                        new StationStorageConfigRepository(),
                        storageConfig,
                        new DomainEventBus(Set.of())));
        station = stationRepo.create("MediaLibraryStation");
        account = accountRepo.create("media-lib@test.com", "Media", "Author");
        otherAccount = accountRepo.create("media-lib-2@test.com", "Media", "Second");
        member = stationMemberRepo.create(station.id(), account.id());
        otherMember = stationMemberRepo.create(station.id(), otherAccount.id());
        pageId = pageRepo.create(station.id(), "Media Page", "media-page", null, member.id())
                .id();
        containerId = contentContainerRepo.create(station.id()).id();
        pageRepo.setContainer(pageId, containerId);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
        accountRepo.delete(otherAccount.id());
    }

    private static byte[] bytes(String seed) {
        return ("media-" + seed).getBytes();
    }

    @Test
    void uploadStoresFileAndRecordsUploader() throws Exception {
        var file = media.upload(station.id(), pageId, member.id(), "one.png", "image/png", bytes("one"));
        try {
            assertEquals("one.png", file.fileName());
            assertTrue(media.readById(file.id()).isPresent());
            assertTrue(media.mayRelease(file.id(), member.id()));
        } finally {
            media.deleteFile(file.id());
        }
    }

    @Test
    void identicalBytesReuseTheFileAndAddAnUploader() throws Exception {
        var first = media.upload(station.id(), pageId, member.id(), "same.png", "image/png", bytes("same"));
        var second = media.upload(station.id(), pageId, otherMember.id(), "copy.png", "image/png", bytes("same"));
        try {
            assertEquals(first.id(), second.id(), "identical bytes are stored once");
            assertTrue(
                    media.listOwnUploads(station.id(), otherMember.id()).stream()
                            .anyMatch(l -> l.file().id() == first.id()),
                    "the second uploader must see the thing they just uploaded");
            assertTrue(media.listOwnUploads(station.id(), member.id()).stream()
                    .anyMatch(l -> l.file().id() == first.id()));
        } finally {
            media.deleteFile(first.id());
        }
    }

    @Test
    void ownUploadsAreScopedToTheMember() throws Exception {
        var mine = media.upload(station.id(), null, member.id(), "mine.png", "image/png", bytes("mine"));
        var theirs = media.upload(station.id(), null, otherMember.id(), "theirs.png", "image/png", bytes("theirs"));
        try {
            var own = media.listOwnUploads(station.id(), member.id());
            assertTrue(own.stream().anyMatch(l -> l.file().id() == mine.id()));
            assertFalse(own.stream().anyMatch(l -> l.file().id() == theirs.id()));

            var whole = media.listLibrary(station.id());
            assertTrue(whole.stream().anyMatch(l -> l.file().id() == mine.id()));
            assertTrue(whole.stream().anyMatch(l -> l.file().id() == theirs.id()));
            assertEquals(
                    member.id(),
                    whole.stream()
                            .filter(l -> l.file().id() == mine.id())
                            .findFirst()
                            .orElseThrow()
                            .uploadedBy());
        } finally {
            media.deleteFile(mine.id());
            media.deleteFile(theirs.id());
        }
    }

    @Test
    void releasingAnUploadKeepsTheFileWhileAnotherMemberClaimsIt() throws Exception {
        var file = media.upload(station.id(), null, member.id(), "shared.png", "image/png", bytes("shared"));
        media.upload(station.id(), null, otherMember.id(), "shared.png", "image/png", bytes("shared"));
        try {
            assertTrue(media.releaseUpload(file.id(), member.id()));
            assertTrue(media.findFile(file.id()).isPresent(), "another member still claims the file");
            assertFalse(media.mayRelease(file.id(), member.id()));

            assertTrue(media.releaseUpload(file.id(), otherMember.id()));
            assertTrue(media.findFile(file.id()).isEmpty(), "nobody claims it and nothing points at it");
        } finally {
            media.findFile(file.id()).ifPresent(f -> media.deleteFile(f.id()));
        }
    }

    @Test
    void releasingAnUploadKeepsAFileThatIsStillUsed() throws Exception {
        var file = media.upload(station.id(), null, member.id(), "used.png", "image/png", bytes("used"));
        int rowId = contentContainerRepo.insertRow(containerId, 0);
        contentContainerRepo.insertCell(rowId, 0, 100.0, CellContentType.IMAGE, file.contentHash(), CellConfig.EMPTY);
        try {
            assertTrue(media.releaseUpload(file.id(), member.id()));
            assertTrue(media.findFile(file.id()).isPresent(), "a cell still points at it");
        } finally {
            contentContainerRepo.deleteRows(containerId);
            media.deleteFile(file.id());
        }
    }

    @Test
    void releasingWhatTheMemberDidNotUploadDoesNothing() throws Exception {
        var file = media.upload(station.id(), null, member.id(), "notmine.png", "image/png", bytes("notmine"));
        try {
            assertFalse(media.releaseUpload(file.id(), otherMember.id()));
            assertTrue(media.findFile(file.id()).isPresent());
        } finally {
            media.deleteFile(file.id());
        }
    }

    @Test
    void anUploadedFileIsNeverPruned() throws Exception {
        var owned = media.upload(station.id(), null, member.id(), "owned.png", "image/png", bytes("owned"));
        var unowned = media.upload(station.id(), null, null, "unowned.png", "image/png", bytes("unowned"));
        try {
            assertFalse(media.findUnusedFileIds(station.id()).contains(owned.id()));
            assertTrue(media.findUnusedFileIds(station.id()).contains(unowned.id()));

            assertTrue(media.pruneUnusedFiles(station.id()) >= 1);
            assertTrue(media.findFile(owned.id()).isPresent(), "an image may outlive the first place it was used");
            assertTrue(media.findFile(unowned.id()).isEmpty());
        } finally {
            media.findFile(owned.id()).ifPresent(f -> media.deleteFile(f.id()));
        }
    }

    @Test
    void aReferencedFileIsNotUnused() throws Exception {
        var file = media.upload(station.id(), null, null, "cell.png", "image/png", bytes("cell"));
        int rowId = contentContainerRepo.insertRow(containerId, 0);
        contentContainerRepo.insertCell(rowId, 0, 100.0, CellContentType.IMAGE, file.contentHash(), CellConfig.EMPTY);
        try {
            assertFalse(media.findUnusedFileIds(station.id()).contains(file.id()));
        } finally {
            contentContainerRepo.deleteRows(containerId);
            media.deleteFile(file.id());
        }
    }

    @Test
    void readingByHashOnlyAnswersForFilesTheStationHolds() throws Exception {
        var file = media.upload(station.id(), null, null, "hash.png", "image/png", bytes("hash"));
        try {
            assertTrue(media.read(station.id(), file.contentHash()).isPresent());
            assertTrue(media.read(station.id(), null).isEmpty());
            assertTrue(media.read(station.id(), "  ").isEmpty());
            assertTrue(media.read(station.id(), "no-such-hash").isEmpty());
            assertTrue(media.readById(-1).isEmpty());
        } finally {
            media.deleteFile(file.id());
        }
    }

    @Test
    void imageSizeLimitIsEnforced() {
        byte[] tooLarge = new byte[6 * 1024 * 1024];
        assertThrows(
                StorageQuotaService.StorageQuotaExceededException.class,
                () -> media.upload(station.id(), null, member.id(), "big.png", "image/png", tooLarge));
    }

    @Test
    void folderAndTagOperations() throws Exception {
        var folder = media.createFolder(station.id(), null, "Documents", 0);
        assertTrue(media.listFolders(station.id()).stream().anyMatch(f -> f.id() == folder.id()));
        assertTrue(media.updateFolder(station.id(), folder.id(), null, "Docs", 1));
        assertFalse(media.updateFolder(99999, folder.id(), null, "Nope", 0));
        assertFalse(media.deleteFolder(99999, folder.id()));

        var tag = media.createTag(station.id(), "Hero", "#ff0000");
        assertTrue(media.listTags(station.id()).stream().anyMatch(t -> t.id() == tag.id()));
        assertTrue(media.updateTag(station.id(), tag.id(), "Heroes", "#00ff00"));
        assertFalse(media.updateTag(99999, tag.id(), "X", "Y"));

        var file = media.upload(station.id(), pageId, member.id(), "tagged.png", "image/png", bytes("tagged"));

        assertTrue(media.assignTag(station.id(), file.id(), tag.id()));
        assertFalse(media.assignTag(99999, file.id(), tag.id()));
        assertFalse(media.assignTag(station.id(), 99999, tag.id()));
        assertFalse(media.assignTag(station.id(), file.id(), 99999));

        assertTrue(media.moveFileToFolder(station.id(), file.id(), folder.id()));
        assertFalse(media.moveFileToFolder(99999, file.id(), folder.id()));

        assertTrue(media.unassignTag(station.id(), file.id(), tag.id()));
        assertFalse(media.unassignTag(99999, file.id(), tag.id()));

        assertTrue(media.deleteFile(file.id()));
        assertTrue(media.deleteTag(station.id(), tag.id()));
        assertFalse(media.deleteTag(99999, tag.id()));
        assertTrue(media.deleteFolder(station.id(), folder.id()));
    }

    @Test
    void fileMetaUpdatesAreStationScoped() throws Exception {
        var file = media.upload(station.id(), pageId, member.id(), "meta.png", "image/png", bytes("meta"));
        try {
            assertTrue(media.updateFileMeta(station.id(), file.id(), "alt", "desc"));
            assertFalse(media.updateFileMeta(99999, file.id(), "alt", "desc"));
            assertFalse(media.updateFileMeta(station.id(), 99999, "alt", "desc"));
        } finally {
            media.deleteFile(file.id());
        }
    }

    @Test
    void deletingWhatIsNotThereAnswersFalse() {
        assertFalse(media.deleteFile(99999));
        assertFalse(media.releaseUpload(99999, member.id()));
        assertTrue(media.findFile(99999).isEmpty());
    }
}
