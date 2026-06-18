/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.PageFile;
import dev.chojo.ember.feature.page.repository.PageFileMetaRepository;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageFileMetaRepositoryTest extends RepositoryTestBase {

    private static PageFileMetaRepository metaRepo;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int pageId;

    @BeforeAll
    static void setupClass() {
        metaRepo = new PageFileMetaRepository();
        station = stationRepo.create("PageMetaStation");
        account = accountRepo.create("page-meta@test.com", "Page", "Meta");
        member = stationMemberRepo.create(station.id(), account.id());
        var page = pageRepo.create(station.id(), "Meta Page", "meta-page", null, member.id());
        pageId = page.id();
    }

    @AfterAll
    static void cleanupClass() {
        pageRepo.delete(pageId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private PageFile createFile(String hash) {
        return pageRepo.createFile(pageId, station.id(), hash, hash + ".png", "image/png", 8);
    }

    @Test
    void folderCrudAndChildren() {
        var root = metaRepo.createFolder(station.id(), null, "Root", 0);
        assertNotNull(root);
        assertEquals("Root", root.name());
        try {
            var child = metaRepo.createFolder(station.id(), root.id(), "Sub", 1);
            try {
                var found = metaRepo.findFolder(root.id());
                assertTrue(found.isPresent());
                assertEquals("Root", found.orElseThrow().name());

                var all = metaRepo.findFoldersByStation(station.id());
                assertTrue(all.stream().anyMatch(f -> f.id() == root.id()));
                assertTrue(all.stream().anyMatch(f -> f.id() == child.id()));

                assertTrue(metaRepo.updateFolder(root.id(), null, "Renamed", 2));
                assertEquals(
                        "Renamed", metaRepo.findFolder(root.id()).orElseThrow().name());

                assertFalse(metaRepo.updateFolder(99999, null, "X", 0));
                assertFalse(metaRepo.deleteFolder(99999));
            } finally {
                metaRepo.deleteFolder(child.id());
            }
            assertTrue(metaRepo.findFolder(child.id()).isEmpty());
        } finally {
            metaRepo.deleteFolder(root.id());
        }
    }

    @Test
    void moveFileToFolder() {
        var folder = metaRepo.createFolder(station.id(), null, "Move-Target", 0);
        var file = createFile("hash-move");
        try {
            assertTrue(metaRepo.moveFileToFolder(file.id(), folder.id()));
            var fetched = pageRepo.findFile(file.id()).orElseThrow();
            assertEquals(folder.id(), fetched.folderId());
            assertTrue(metaRepo.moveFileToFolder(file.id(), null));
            assertNull(pageRepo.findFile(file.id()).orElseThrow().folderId());
            assertFalse(metaRepo.moveFileToFolder(99999, folder.id()));
        } finally {
            pageRepo.deleteFile(file.id());
            metaRepo.deleteFolder(folder.id());
        }
    }

    @Test
    void tagCrud() {
        var tag = metaRepo.createTag(station.id(), "Spotlight", "#abcdef");
        try {
            var found = metaRepo.findTag(tag.id());
            assertTrue(found.isPresent());
            assertEquals("Spotlight", found.orElseThrow().name());

            var all = metaRepo.findTagsByStation(station.id());
            assertTrue(all.stream().anyMatch(t -> t.id() == tag.id()));

            assertTrue(metaRepo.updateTag(tag.id(), "Featured", "#123456"));
            assertEquals("Featured", metaRepo.findTag(tag.id()).orElseThrow().name());

            assertFalse(metaRepo.updateTag(99999, "X", "#000000"));
            assertFalse(metaRepo.deleteTag(99999));
        } finally {
            metaRepo.deleteTag(tag.id());
        }
        assertTrue(metaRepo.findTag(tag.id()).isEmpty());
    }

    @Test
    void assignTagAndFindAssignments() {
        var tag = metaRepo.createTag(station.id(), "TagAssign", null);
        var fileA = createFile("hash-A");
        var fileB = createFile("hash-B");
        try {
            metaRepo.assignTag(fileA.id(), tag.id());
            metaRepo.assignTag(fileA.id(), tag.id());
            metaRepo.assignTag(fileB.id(), tag.id());

            assertTrue(metaRepo.findTagAssignments(List.of()).isEmpty());
            assertTrue(metaRepo.findTagAssignments(null).isEmpty());

            var map = metaRepo.findTagAssignments(List.of(fileA.id(), fileB.id()));
            assertTrue(map.get(fileA.id()).contains(tag.id()));
            assertTrue(map.get(fileB.id()).contains(tag.id()));

            assertTrue(metaRepo.unassignTag(fileA.id(), tag.id()));
            assertFalse(metaRepo.unassignTag(fileA.id(), tag.id()));
        } finally {
            pageRepo.deleteFile(fileA.id());
            pageRepo.deleteFile(fileB.id());
            metaRepo.deleteTag(tag.id());
        }
    }

    @Test
    void findFolderMissing() {
        assertTrue(metaRepo.findFolder(99999).isEmpty());
        assertTrue(metaRepo.findTag(99999).isEmpty());
    }
}
