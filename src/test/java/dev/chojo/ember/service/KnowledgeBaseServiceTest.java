/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
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
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeBaseServiceTest extends RepositoryTestBase {
    private static KnowledgeBaseService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int folderId;
    private static int fileId;

    @BeforeAll
    static void setup() {
        var fileStorage = mock(KbFileStorageService.class);
        service = new KnowledgeBaseService(knowledgeBaseRepo, stationRepo, fileStorage);
        station = stationRepo.create("KbSvcStation");
        account = accountRepo.create("kb-svc@test.com", "Kb", "SvcTester");
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
        var folder = service.createFolder(station.id(), null, "Safety Docs", "Safety related", member.id());
        assertNotNull(folder);
        assertEquals("Safety Docs", folder.name());
        folderId = folder.id();
    }

    @Test
    @Order(2)
    void findFolders() {
        var folders = service.findFolders(station.id(), null);
        assertTrue(folders.stream().anyMatch(f -> f.id() == folderId));
    }

    @Test
    @Order(3)
    void findFolder() {
        assertTrue(service.findFolder(folderId).isPresent());
        assertTrue(service.findFolder(99999).isEmpty());
    }

    @Test
    @Order(4)
    void updateFolder() {
        assertTrue(service.updateFolder(folderId, "Updated Safety", "Updated", null, 1));
    }

    // -- Markdown Files --

    @Test
    @Order(10)
    void createMarkdownFile() {
        var file = service.createMarkdownFile(
                station.id(), folderId, "Guide", "Setup guide", "# Guide\nThis is a guide.", member.id());
        assertNotNull(file);
        assertEquals("Guide", file.name());
        assertEquals(KbFileType.MARKDOWN, file.fileType());
        fileId = file.id();
    }

    @Test
    @Order(11)
    void findFiles() {
        var files = service.findFiles(station.id(), folderId);
        assertTrue(files.stream().anyMatch(f -> f.id() == fileId));
    }

    @Test
    @Order(12)
    void findFile() {
        assertTrue(service.findFile(fileId).isPresent());
        assertTrue(service.findFile(99999).isEmpty());
    }

    @Test
    @Order(13)
    void updateFile() {
        assertTrue(service.updateFile(fileId, "Updated Guide", "Updated desc", null, 2));
    }

    // -- Markdown Content --

    @Test
    @Order(20)
    void getMarkdownContent() {
        var content = service.getMarkdownContent(fileId);
        assertTrue(content.isPresent());
        assertTrue(content.get().contains("Guide"));
    }

    @Test
    @Order(21)
    void updateMarkdownContent() {
        service.updateMarkdownContent(fileId, "# Updated\nNew content here.", member.id());
        var content = service.getMarkdownContent(fileId);
        assertTrue(content.isPresent());
        assertTrue(content.get().contains("Updated"));
    }

    @Test
    @Order(22)
    void renderMarkdown() {
        String html = service.renderMarkdown("# Hello\nWorld");
        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("Hello"));
    }

    // -- Versions --

    @Test
    @Order(30)
    void findVersions() {
        var versions = service.findVersions(fileId);
        assertFalse(versions.isEmpty());
    }

    @Test
    @Order(31)
    void findVersion() {
        assertTrue(service.findVersion(fileId, 1).isPresent());
        assertTrue(service.findVersion(fileId, 99).isEmpty());
    }

    @Test
    @Order(32)
    void reconstructVersion() {
        var content = service.reconstructVersion(fileId, 1);
        assertTrue(content.isPresent());
    }

    @Test
    @Order(33)
    void revertToVersion() {
        service.revertToVersion(fileId, 1, member.id());
        // No exception = success
    }

    // -- Search --

    @Test
    @Order(40)
    void searchEmptyQuery() {
        assertTrue(service.search(station.id(), "").isEmpty());
        assertTrue(service.search(station.id(), null).isEmpty());
    }

    @Test
    @Order(41)
    void searchNonEmpty() {
        var results = service.search(station.id(), "guide");
        assertNotNull(results);
    }

    @Test
    @Order(42)
    void searchWithSnippetsEmpty() {
        assertTrue(service.searchWithSnippets(station.id(), "").isEmpty());
    }

    @Test
    @Order(43)
    void searchWithSnippetsNonEmpty() {
        var results = service.searchWithSnippets(station.id(), "guide");
        assertNotNull(results);
    }

    // -- Tags --

    @Test
    @Order(50)
    void setFileTags() {
        var tags = service.setFileTags(fileId, List.of("safety", "guide"), station.id());
        assertEquals(2, tags.size());
    }

    @Test
    @Order(51)
    void findFileTags() {
        var tags = service.findFileTags(fileId);
        assertFalse(tags.isEmpty());
    }

    @Test
    @Order(52)
    void findFilesByTag() {
        var files = service.findFilesByTag(station.id(), "safety");
        assertTrue(files.stream().anyMatch(f -> f.id() == fileId));
    }

    @Test
    @Order(53)
    void findTagsByStation() {
        var tags = service.findTagsByStation(station.id());
        assertFalse(tags.isEmpty());
    }

    @Test
    @Order(54)
    void setFolderTags() {
        var tags = service.setFolderTags(folderId, List.of("docs"), station.id());
        assertFalse(tags.isEmpty());
    }

    @Test
    @Order(55)
    void findFolderTags() {
        var tags = service.findFolderTags(folderId);
        assertFalse(tags.isEmpty());
    }

    // -- Related Files --

    @Test
    @Order(60)
    void setAndFindRelatedFiles() {
        var other = knowledgeBaseRepo.createFile(
                station.id(), null, "Related", "", KbFileType.TEXT, "text/plain", 0, null, member.id());
        service.setRelatedFiles(fileId, List.of(other.id()));
        var related = service.findRelatedFiles(fileId);
        assertTrue(related.stream().anyMatch(f -> f.id() == other.id()));
        service.setRelatedFiles(fileId, List.of());
        knowledgeBaseRepo.deleteFile(other.id());
    }

    // -- Public Visibility --

    @Test
    @Order(80)
    void setAndGetPublicVisibility() {
        service.setPublicVisibility(folderId, null, true);
        var vis = service.findPublicVisibility(folderId, null);
        assertTrue(vis.isPresent());
        assertTrue(vis.get());

        // isPubliclyVisible with ALLOW_ALL mode and no restrictions
        assertTrue(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, folderId, null));
        assertFalse(service.isPubliclyVisible(PublicKbMode.OFF, folderId, null));

        service.removePublicVisibility(folderId, null);
    }

    @Test
    @Order(81)
    void isPubliclyVisibleDenyAll() {
        // In DENY_ALL mode without override, should not be visible
        assertFalse(service.isPubliclyVisible(PublicKbMode.DENY_ALL, folderId, null));
    }

    @Test
    @Order(82)
    void isPubliclyVisibleFileInFolder() {
        // File in folder - folder visibility is checked
        assertTrue(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, null, fileId));
    }

    // -- Access Restrictions --

    @Test
    @Order(85)
    void setRestrictions() {
        service.setRestrictions(folderId, null, List.of(1), List.of(), List.of(), List.of());
        var restrictions = service.findRestrictions(folderId, null);
        assertFalse(restrictions.isEmpty());
        // Public visibility should now be false because of restrictions
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, folderId, null));
        // Clear
        service.setRestrictions(folderId, null, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    @Order(86)
    void setSourceReference() {
        var source = knowledgeBaseRepo.createFile(
                station.id(), null, "Source", "", KbFileType.TEXT, "text/plain", 0, null, member.id());
        service.setSourceReference(fileId, source.id(), station.id());
        knowledgeBaseRepo.deleteFile(source.id());
    }

    // -- Cleanup --

    // -- canAccess --

    @Test
    @Order(89)
    void canAccessNoRestrictions() {
        // No restrictions on file or folder — anyone can access
        assertTrue(service.canAccess(member.id(), null, fileId, List.of(), List.of(), List.of()));
    }

    @Test
    @Order(90)
    void canAccessWithFolderRestriction() {
        // Set a restriction that requires role 1
        service.setRestrictions(folderId, null, List.of(1), List.of(), List.of(), List.of());
        // Member without role 1 should be denied
        assertFalse(service.canAccess(member.id(), folderId, null, List.of(), List.of(), List.of()));
        // Member with role 1 should be allowed
        assertTrue(service.canAccess(member.id(), folderId, null, List.of(1), List.of(), List.of()));
        // Clear
        service.setRestrictions(folderId, null, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    @Order(91)
    void canAccessFileInheritsFolder() {
        // Set restriction on folder
        service.setRestrictions(folderId, null, List.of(1), List.of(), List.of(), List.of());
        // Access to file in that folder checks parent folder restriction
        assertFalse(service.canAccess(member.id(), null, fileId, List.of(), List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), null, fileId, List.of(1), List.of(), List.of()));
        // Clear
        service.setRestrictions(folderId, null, List.of(), List.of(), List.of(), List.of());
    }

    // -- Link file and uploaded file --

    @Test
    @Order(92)
    void createLinkFile() {
        var file = service.createLinkFile(
                station.id(), null, "Google", "Search engine", "https://google.com", member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.LINK, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(93)
    void createUploadedTextFile() {
        byte[] data = "plain text content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "notes.txt", "Some notes", data, "text/plain", member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.TEXT, file.fileType());
        var content = service.getMarkdownContent(file.id());
        assertTrue(content.isPresent());
        assertEquals("plain text content", content.get());
        service.deleteFile(file.id());
    }

    @Test
    @Order(94)
    void createUploadedImageFile() {
        byte[] data = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG header
        var file =
                service.createUploadedFile(station.id(), null, "photo.png", "A photo", data, "image/png", member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.IMAGE, file.fileType());
        service.deleteFile(file.id());
    }

    // -- Nested folder --

    @Test
    @Order(95)
    void createNestedFolder() {
        var child = service.createFolder(station.id(), folderId, "Subfolder", "Child folder", member.id());
        assertNotNull(child);
        var children = service.findFolders(station.id(), folderId);
        assertTrue(children.stream().anyMatch(f -> f.id() == child.id()));
        service.deleteFolder(child.id());
    }

    // -- Version reconstruction --

    @Test
    @Order(96)
    void reconstructVersionAfterMultipleEdits() {
        // Version 1 already exists from creation
        service.updateMarkdownContent(fileId, "# Version 2\nEdited content.", member.id());
        service.updateMarkdownContent(fileId, "# Version 3\nFinal content.", member.id());

        var v1 = service.reconstructVersion(fileId, 1);
        assertTrue(v1.isPresent());

        var latest = service.getMarkdownContent(fileId);
        assertTrue(latest.isPresent());
        assertTrue(latest.get().contains("Version 3"));
    }

    @Test
    @Order(97)
    void reconstructVersionNonExistent() {
        var result = service.reconstructVersion(99999, 1);
        // No versions for non-existent file — should be empty
        assertTrue(result.isEmpty());
    }

    // -- Link file with blank name --

    @Test
    @Order(87)
    void createLinkFileBlankNameAutoPopulates() {
        // Providing blank name and description should trigger auto-populate from URL
        // (the HTTP call may fail, but code still runs the logic)
        var file = service.createLinkFile(station.id(), null, "", "", "https://example.com", member.id());
        assertNotNull(file);
        assertNotNull(file.name());
        service.deleteFile(file.id());
    }

    // -- getFileContent and getFileContentType --

    @Test
    @Order(88)
    void getFileContentMissing() {
        // File was stored as markdown, not binary; binary storage returns empty
        assertTrue(service.getFileContent(fileId).isEmpty());
        assertTrue(service.getFileContentType(fileId).isEmpty());
    }

    // -- Uploaded file with markdown extension --

    @Test
    @Order(89)
    void createUploadedMarkdownFile() {
        byte[] data = "# Markdown Content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "doc.md", "Markdown upload", data, "text/markdown", member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.MARKDOWN, file.fileType());
        service.deleteFile(file.id());
    }

    // -- Uploaded file with unknown type --

    @Test
    @Order(90)
    void createUploadedUnknownFile() {
        byte[] data = new byte[] {0x00, 0x01, 0x02};
        var file = service.createUploadedFile(
                station.id(), null, "file.dat", "Binary data", data, "application/octet-stream", member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.OTHER, file.fileType());
        service.deleteFile(file.id());
    }

    // -- Restriction modes --

    @Test
    @Order(91)
    void canAccessRootFolderNoRestrictions() {
        assertTrue(service.canAccess(member.id(), null, null, List.of(), List.of(), List.of()));
    }

    @Test
    @Order(93)
    void isPubliclyVisibleDenyAllWithOverride() {
        service.setPublicVisibility(folderId, null, true);
        assertTrue(service.isPubliclyVisible(
                dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode.DENY_ALL, folderId, null));
        service.removePublicVisibility(folderId, null);
    }

    @Test
    @Order(94)
    void isPubliclyVisibleAllowAllWithOptOut() {
        service.setPublicVisibility(folderId, null, false);
        assertFalse(service.isPubliclyVisible(
                dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode.ALLOW_ALL, folderId, null));
        service.removePublicVisibility(folderId, null);
    }

    // -- YouTube file --

    @Test
    @Order(100)
    void createYoutubeFile() {
        // This will attempt to fetch YouTube metadata — may fail silently (network) but must not throw
        var file = service.createYoutubeFile(
                station.id(),
                null,
                "Tutorial Video",
                "A tutorial",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.YOUTUBE, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(101)
    void fetchUrlMetadataValidUrl() {
        // May succeed or fail depending on network — should never throw
        var metadata = service.fetchUrlMetadata("https://example.com");
        assertNotNull(metadata);
    }

    @Test
    @Order(102)
    void fetchUrlMetadataInvalidUrl() {
        // Invalid URL — should return empty metadata without throwing
        var metadata = service.fetchUrlMetadata("not-a-valid-url");
        assertNotNull(metadata);
    }

    // -- detectFileType via uploaded file --

    @Test
    @Order(103)
    void createUploadedPdfFile() {
        // Minimal valid-looking PDF (won't actually parse) — should create PDF type entry
        byte[] pdfHeader = "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "document.pdf", "A PDF", pdfHeader, "application/pdf", member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.PDF, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(104)
    void createUploadedFileByExtensionPdf() {
        // Detect by extension when mimeType is null
        byte[] data = new byte[] {0x01, 0x02, 0x03};
        var file = service.createUploadedFile(station.id(), null, "report.pdf", "PDF by ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.PDF, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(105)
    void createUploadedFileByExtensionMarkdown() {
        byte[] data = "# MD".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "readme.markdown", "Markdown by ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.MARKDOWN, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(106)
    void createUploadedFileByExtensionTxt() {
        byte[] data = "plain".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = service.createUploadedFile(station.id(), null, "notes.txt", "Text by ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.TEXT, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(107)
    void createUploadedFileByExtensionImage() {
        byte[] data = new byte[] {0x00, 0x01};
        for (String ext : new String[] {"jpg", "jpeg", "gif", "webp", "svg"}) {
            var file = service.createUploadedFile(station.id(), null, "img." + ext, "Img", data, null, member.id());
            assertNotNull(file);
            assertEquals(dev.chojo.ember.feature.knowledgebase.entity.KbFileType.IMAGE, file.fileType());
            service.deleteFile(file.id());
        }
    }

    // -- canAccess with member-based restriction --

    @Test
    @Order(108)
    void canAccessWithMemberRestriction() {
        // Create a file with a member-based restriction
        var restrictedFile = service.createMarkdownFile(
                station.id(), null, "Restricted File", "Only for specific member", "# Secret", member.id());

        service.setRestrictions(null, restrictedFile.id(), List.of(), List.of(), List.of(), List.of(member.id()));

        // member (the restricted one) can access — member ID matches
        assertTrue(service.canAccess(member.id(), null, restrictedFile.id(), List.of(), List.of(), List.of()));
        // another member ID should be denied
        assertFalse(service.canAccess(member.id() + 9999, null, restrictedFile.id(), List.of(), List.of(), List.of()));

        service.setRestrictions(null, restrictedFile.id(), List.of(), List.of(), List.of(), List.of());
        service.deleteFile(restrictedFile.id());
    }

    @Test
    @Order(109)
    void canAccessWithTagRestriction() {
        var tag = userTagRepo.create(station.id(), "KbTagRestriction");
        var tagRestrictedFile =
                service.createMarkdownFile(station.id(), null, "Tag File", "Tag restricted", "# Tag", member.id());

        service.setRestrictions(null, tagRestrictedFile.id(), List.of(), List.of(), List.of(tag.id()), List.of());

        // Without the tag, access denied
        assertFalse(service.canAccess(member.id(), null, tagRestrictedFile.id(), List.of(), List.of(), List.of()));
        // With the tag, access granted
        assertTrue(
                service.canAccess(member.id(), null, tagRestrictedFile.id(), List.of(), List.of(), List.of(tag.id())));

        service.setRestrictions(null, tagRestrictedFile.id(), List.of(), List.of(), List.of(), List.of());
        service.deleteFile(tagRestrictedFile.id());
    }

    @Test
    @Order(110)
    void canAccessWithGroupRestriction() {
        var group = memberGroupRepo.create(station.id(), "KbGroupRestriction");
        var groupRestrictedFile = service.createMarkdownFile(
                station.id(), null, "Group File", "Group restricted", "# Group", member.id());

        service.setRestrictions(null, groupRestrictedFile.id(), List.of(), List.of(group.id()), List.of(), List.of());

        assertFalse(service.canAccess(member.id(), null, groupRestrictedFile.id(), List.of(), List.of(), List.of()));
        assertTrue(service.canAccess(
                member.id(), null, groupRestrictedFile.id(), List.of(), List.of(group.id()), List.of()));

        service.setRestrictions(null, groupRestrictedFile.id(), List.of(), List.of(), List.of(), List.of());
        service.deleteFile(groupRestrictedFile.id());
        memberGroupRepo.delete(group.id());
    }

    // -- isPubliclyVisible with nested folders --

    @Test
    @Order(111)
    void isPubliclyVisibleNestedFolderParentOptOut() {
        // Parent folder opts out — child should also be not visible
        var parentFolder = service.createFolder(station.id(), null, "Parent", "Parent folder", member.id());
        var childFolder = service.createFolder(station.id(), parentFolder.id(), "Child", "Child folder", member.id());

        service.setPublicVisibility(parentFolder.id(), null, false);
        // Child inherits parent's opt-out
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, childFolder.id(), null));

        service.removePublicVisibility(parentFolder.id(), null);
        service.deleteFolder(childFolder.id());
        service.deleteFolder(parentFolder.id());
    }

    @Test
    @Order(112)
    void isPubliclyVisibleFileRestrictedParentFolder() {
        // Parent folder has restrictions — file in it should not be publicly visible
        var restrictedParent = service.createFolder(station.id(), null, "RestParent", "Restricted parent", member.id());
        service.setRestrictions(restrictedParent.id(), null, List.of(1), List.of(), List.of(), List.of());

        var fileInFolder = service.createMarkdownFile(
                station.id(), restrictedParent.id(), "FileInRestricted", "", "# Content", member.id());

        // File in restricted folder should not be visible
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, null, fileInFolder.id()));

        service.setRestrictions(restrictedParent.id(), null, List.of(), List.of(), List.of(), List.of());
        service.deleteFile(fileInFolder.id());
        service.deleteFolder(restrictedParent.id());
    }

    // -- Favourite operations --

    @Test
    @Order(113)
    void addAndRemoveFavourite() {
        var favFile =
                service.createMarkdownFile(station.id(), null, "Fav File", "For favourite test", "# Fav", member.id());

        service.addFavourite(member.id(), favFile.id());
        assertTrue(service.isFavourite(member.id(), favFile.id()));

        var favs = service.findFavourites(member.id());
        assertTrue(favs.stream().anyMatch(f -> f.id() == favFile.id()));

        assertTrue(service.removeFavourite(member.id(), favFile.id()));
        assertFalse(service.isFavourite(member.id(), favFile.id()));

        service.deleteFile(favFile.id());
    }

    // -- setSourceReference --

    @Test
    @Order(114)
    void setSourceReferenceOnMarkdownFile() {
        var source = service.createMarkdownFile(station.id(), null, "SrcFile", "", "# Src", member.id());
        var target = service.createMarkdownFile(station.id(), null, "TgtFile", "", "# Tgt", member.id());
        service.setSourceReference(target.id(), source.id(), station.id());
        var found = service.findFile(target.id());
        assertTrue(found.isPresent());
        service.deleteFile(source.id());
        service.deleteFile(target.id());
    }

    // -- createLinkFile with only blank name (desc provided) --

    @Test
    @Order(115)
    void createLinkFileBlankNameOnlyAutoPopulatesName() {
        var file = service.createLinkFile(
                station.id(), null, "", "Has a description", "https://example.com/page", member.id());
        assertNotNull(file);
        assertFalse(file.name().isBlank());
        service.deleteFile(file.id());
    }

    // -- Cleanup --

    @Test
    @Order(99)
    void deleteFileAndFolder() {
        assertTrue(service.deleteFile(fileId));
        assertTrue(service.deleteFolder(folderId));
    }
}
