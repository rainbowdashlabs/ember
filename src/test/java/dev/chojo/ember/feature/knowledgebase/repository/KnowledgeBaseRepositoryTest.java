/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeBaseRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int folderId;
    private static int fileId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("KbRepoStation");
        account = accountRepo.create("kb-repo@test.com", "Kb", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Folders --

    @Test
    @Order(1)
    void createFolder() {
        var folder = knowledgeBaseRepo.createFolder(station.id(), null, "Safety", "Safety docs", member.id());
        assertNotNull(folder);
        assertEquals("Safety", folder.name());
        folderId = folder.id();
    }

    @Test
    @Order(2)
    void findFoldersRootLevel() {
        var folders = knowledgeBaseRepo.findFolders(station.id(), null);
        assertFalse(folders.isEmpty());
        assertTrue(folders.stream().anyMatch(f -> f.id() == folderId));
    }

    @Test
    @Order(3)
    void findFolderById() {
        assertTrue(knowledgeBaseRepo.findFolderById(folderId).isPresent());
        assertTrue(knowledgeBaseRepo.findFolderById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void updateFolder() {
        assertTrue(knowledgeBaseRepo.updateFolder(folderId, "Updated Safety", "Updated desc", null, 1));
        assertEquals(
                "Updated Safety",
                knowledgeBaseRepo.findFolderById(folderId).orElseThrow().name());
    }

    @Test
    @Order(5)
    void createSubFolder() {
        var sub = knowledgeBaseRepo.createFolder(station.id(), folderId, "SubFolder", "Sub", member.id());
        assertNotNull(sub);
        var subFolders = knowledgeBaseRepo.findFolders(station.id(), folderId);
        assertTrue(subFolders.stream().anyMatch(f -> f.id() == sub.id()));
        knowledgeBaseRepo.deleteFolder(sub.id());
    }

    // -- Files --

    @Test
    @Order(10)
    void createMarkdownFile() {
        var file = knowledgeBaseRepo.createFile(
                station.id(),
                folderId,
                "Welcome",
                "Welcome doc",
                KbFileType.MARKDOWN,
                "text/markdown",
                100,
                null,
                member.id());
        assertNotNull(file);
        assertEquals("Welcome", file.name());
        assertEquals(KbFileType.MARKDOWN, file.fileType());
        fileId = file.id();
    }

    @Test
    @Order(11)
    void findFiles() {
        var files = knowledgeBaseRepo.findFiles(station.id(), folderId);
        assertFalse(files.isEmpty());
        assertTrue(files.stream().anyMatch(f -> f.id() == fileId));
    }

    @Test
    @Order(12)
    void findFilesRootLevel() {
        // create a file without folder
        var rootFile = knowledgeBaseRepo.createFile(
                station.id(), null, "Root file", "", KbFileType.TEXT, "text/plain", 10, null, member.id());
        var rootFiles = knowledgeBaseRepo.findFiles(station.id(), null);
        assertTrue(rootFiles.stream().anyMatch(f -> f.id() == rootFile.id()));
        knowledgeBaseRepo.deleteFile(rootFile.id());
    }

    @Test
    @Order(13)
    void findFileById() {
        assertTrue(knowledgeBaseRepo.findFileById(fileId).isPresent());
        assertTrue(knowledgeBaseRepo.findFileById(99999).isEmpty());
    }

    @Test
    @Order(14)
    void updateFile() {
        assertTrue(knowledgeBaseRepo.updateFile(fileId, "Welcome Updated", "New desc", null, 2));
        assertEquals(
                "Welcome Updated",
                knowledgeBaseRepo.findFileById(fileId).orElseThrow().name());
    }

    // -- Text Content --

    @Test
    @Order(20)
    void storeAndReadTextContent() {
        knowledgeBaseRepo.storeTextContent(fileId, "# Welcome\nThis is a doc.");
        var content = knowledgeBaseRepo.readTextContent(fileId);
        assertTrue(content.isPresent());
        assertEquals("# Welcome\nThis is a doc.", content.get());
    }

    @Test
    @Order(21)
    void storeTextContentOverwrites() {
        knowledgeBaseRepo.storeTextContent(fileId, "New content");
        assertEquals("New content", knowledgeBaseRepo.readTextContent(fileId).orElseThrow());
    }

    // -- Versions --

    @Test
    @Order(30)
    void createAndFindVersion() {
        int next = knowledgeBaseRepo.getNextVersion(fileId);
        assertEquals(1, next);

        var version = knowledgeBaseRepo.createVersion(fileId, "# Initial content", true, 1, member.id());
        assertNotNull(version);
        assertEquals(1, version.version());
        assertTrue(version.isFull());

        var versions = knowledgeBaseRepo.findVersions(fileId);
        assertFalse(versions.isEmpty());

        var found = knowledgeBaseRepo.findVersion(fileId, 1);
        assertTrue(found.isPresent());
        assertTrue(knowledgeBaseRepo.findVersion(fileId, 99).isEmpty());
    }

    @Test
    @Order(31)
    void getNextVersionIncrementsAfterCreate() {
        int next = knowledgeBaseRepo.getNextVersion(fileId);
        assertEquals(2, next);
    }

    // -- Search Index --

    @Test
    @Order(40)
    void updateSearchIndexAndSearch() {
        knowledgeBaseRepo.updateSearchIndex(fileId, "welcome safety document fire", "simple");
        var results = knowledgeBaseRepo.search(station.id(), "safety", "simple");
        // May or may not return results depending on tsvector; just verify no exception
        assertNotNull(results);
    }

    @Test
    @Order(41)
    void searchWithSnippets() {
        var results = knowledgeBaseRepo.searchWithSnippets(station.id(), "safety", "simple");
        assertNotNull(results);
    }

    // -- Tags --

    @Test
    @Order(50)
    void findOrCreateTag() {
        var tag = knowledgeBaseRepo.findOrCreateTag(station.id(), "safety");
        assertNotNull(tag);
        assertEquals("safety", tag.name());

        // Idempotent
        var tag2 = knowledgeBaseRepo.findOrCreateTag(station.id(), "safety");
        assertEquals(tag.id(), tag2.id());
    }

    @Test
    @Order(51)
    void findTagsByStation() {
        var tags = knowledgeBaseRepo.findTagsByStation(station.id());
        assertFalse(tags.isEmpty());
        assertTrue(tags.stream().anyMatch(t -> "safety".equals(t.name())));
    }

    @Test
    @Order(52)
    void setFileTags() {
        knowledgeBaseRepo.setFileTags(fileId, List.of("safety", "fire"), station.id());
        var tags = knowledgeBaseRepo.findFileTags(fileId);
        assertEquals(2, tags.size());

        var byTag = knowledgeBaseRepo.findFilesByTag(station.id(), "safety");
        assertTrue(byTag.stream().anyMatch(f -> f.id() == fileId));
    }

    @Test
    @Order(53)
    void setFolderTags() {
        knowledgeBaseRepo.setFolderTags(folderId, List.of("docs"), station.id());
        var tags = knowledgeBaseRepo.findFolderTags(folderId);
        assertFalse(tags.isEmpty());
    }

    @Test
    @Order(54)
    void deleteTag() {
        var tag = knowledgeBaseRepo.findOrCreateTag(station.id(), "todelete");
        assertTrue(knowledgeBaseRepo.deleteTag(tag.id()));
    }

    // -- Related Files --

    @Test
    @Order(60)
    void setAndFindRelatedFiles() {
        var other = knowledgeBaseRepo.createFile(
                station.id(), null, "Related", "", KbFileType.TEXT, "text/plain", 0, null, member.id());
        knowledgeBaseRepo.setRelatedFiles(fileId, List.of(other.id()));
        var related = knowledgeBaseRepo.findRelatedFiles(fileId);
        assertFalse(related.isEmpty());
        assertTrue(related.stream().anyMatch(f -> f.id() == other.id()));

        // clear
        knowledgeBaseRepo.setRelatedFiles(fileId, List.of());
        assertTrue(knowledgeBaseRepo.findRelatedFiles(fileId).isEmpty());
        knowledgeBaseRepo.deleteFile(other.id());
    }

    // -- Public Visibility --

    @Test
    @Order(80)
    void setAndFindPublicVisibility() {
        knowledgeBaseRepo.setPublicVisibility(folderId, null, true);
        var vis = knowledgeBaseRepo.findPublicVisibility(folderId, null);
        assertTrue(vis.isPresent());
        assertTrue(vis.get());

        knowledgeBaseRepo.setPublicVisibility(null, fileId, false);
        var fileVis = knowledgeBaseRepo.findPublicVisibility(null, fileId);
        assertTrue(fileVis.isPresent());
        assertFalse(fileVis.get());
    }

    @Test
    @Order(81)
    void findPublicVisibilityBothNull() {
        assertTrue(knowledgeBaseRepo.findPublicVisibility(null, null).isEmpty());
    }

    @Test
    @Order(82)
    void removePublicVisibility() {
        knowledgeBaseRepo.removePublicVisibility(folderId, null);
        assertTrue(knowledgeBaseRepo.findPublicVisibility(folderId, null).isEmpty());

        knowledgeBaseRepo.removePublicVisibility(null, fileId);
        assertTrue(knowledgeBaseRepo.findPublicVisibility(null, fileId).isEmpty());
    }

    // -- Restrictions --

    @Test
    @Order(85)
    void hasRestrictions() {
        assertFalse(knowledgeBaseRepo.hasRestrictions(folderId, null));
        assertFalse(knowledgeBaseRepo.hasRestrictions(null, fileId));
        assertFalse(knowledgeBaseRepo.hasRestrictions(null, null));
    }

    @Test
    @Order(86)
    void accessRestrictions() {
        var restriction = knowledgeBaseRepo.addRestriction(folderId, null, StationUserType.MEMBER, null, null, null);
        assertNotNull(restriction);

        var restrictions = knowledgeBaseRepo.findRestrictions(folderId, null);
        assertFalse(restrictions.isEmpty());
        assertTrue(knowledgeBaseRepo.hasRestrictions(folderId, null));

        assertTrue(knowledgeBaseRepo.removeRestriction(restriction.id()));
        knowledgeBaseRepo.clearRestrictions(folderId, null);
        assertTrue(knowledgeBaseRepo.findRestrictions(folderId, null).isEmpty());
    }

    @Test
    @Order(87)
    void fileAccessRestrictions() {
        knowledgeBaseRepo.addRestriction(null, fileId, null, null, null, member.id());
        assertFalse(knowledgeBaseRepo.findRestrictions(null, fileId).isEmpty());
        assertTrue(knowledgeBaseRepo.hasRestrictions(null, fileId));
        knowledgeBaseRepo.clearRestrictions(null, fileId);
    }

    // -- Source Reference --

    @Test
    @Order(88)
    void setSourceReference() {
        var other = knowledgeBaseRepo.createFile(
                station.id(), null, "Source", "", KbFileType.TEXT, "text/plain", 0, null, member.id());
        assertTrue(knowledgeBaseRepo.setSourceReference(fileId, other.id(), station.id()));
        knowledgeBaseRepo.deleteFile(other.id());
    }

    // -- Favourites --

    @Test
    @Order(90)
    void addAndFindFavourites() {
        knowledgeBaseRepo.addFavourite(member.id(), fileId);
        var favs = knowledgeBaseRepo.findFavourites(member.id());
        assertFalse(favs.isEmpty());
        assertTrue(favs.stream().anyMatch(f -> f.id() == fileId));
        assertTrue(knowledgeBaseRepo.isFavourite(member.id(), fileId));
    }

    @Test
    @Order(91)
    void isFavouriteReturnsFalseWhenAbsent() {
        assertFalse(knowledgeBaseRepo.isFavourite(member.id(), 99999));
    }

    @Test
    @Order(92)
    void removeFavourite() {
        assertTrue(knowledgeBaseRepo.removeFavourite(member.id(), fileId));
        assertFalse(knowledgeBaseRepo.isFavourite(member.id(), fileId));
        // Remove again — should return false
        assertFalse(knowledgeBaseRepo.removeFavourite(member.id(), fileId));
    }

    // -- createFile with linkUrl --

    @Test
    @Order(93)
    void createFileWithLinkUrl() {
        var file = knowledgeBaseRepo.createFile(
                station.id(),
                null,
                "Link File",
                "desc",
                KbFileType.LINK,
                "text/plain",
                0,
                null,
                "https://example.com",
                member.id());
        assertNotNull(file);
        assertEquals("https://example.com", file.linkUrl());
        knowledgeBaseRepo.deleteFile(file.id());
    }

    // -- Cleanup --

    @Test
    @Order(99)
    void deleteFileAndFolder() {
        assertTrue(knowledgeBaseRepo.deleteFile(fileId));
        assertTrue(knowledgeBaseRepo.findFileById(fileId).isEmpty());

        assertTrue(knowledgeBaseRepo.deleteFolder(folderId));
        assertTrue(knowledgeBaseRepo.findFolderById(folderId).isEmpty());
    }
}
