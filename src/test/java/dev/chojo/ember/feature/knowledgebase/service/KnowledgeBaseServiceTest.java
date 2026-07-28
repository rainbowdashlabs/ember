/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeBaseServiceTest extends RepositoryTestBase {
    private static KnowledgeBaseService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int folderId;
    private static int fileId;
    private static KbFileStorageService fileStorage;

    @BeforeAll
    static void setup() {
        fileStorage = mock(KbFileStorageService.class);
        var storageConfig = new Storage();
        service = new KnowledgeBaseService(
                knowledgeBaseRepo,
                stationRepo,
                stationMemberRepo,
                accountRepo,
                memberGroupRepo,
                userTagRepo,
                fileStorage,
                mock(KbCommentRepository.class),
                memberIdentityFactory,
                mock(DomainEventBus.class),
                mock(StationMemberService.class),
                new PresentationCompressor(storageConfig),
                new PdfCompressor(storageConfig));
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
        service.setRestrictions(
                folderId,
                null,
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));
        var restrictions = service.findRestrictions(folderId, null);
        assertFalse(restrictions.isEmpty());
        // Public visibility should now be false because of restrictions
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, folderId, null));
        // Clear
        service.setRestrictions(folderId, null, RestrictionSelection.empty());
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
        assertTrue(service.canAccess(member.id(), null, fileId, null, List.of(), List.of()));
    }

    @Test
    @Order(90)
    void canAccessWithFolderRestriction() {
        // Set a restriction that requires role 1
        service.setRestrictions(
                folderId,
                null,
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));
        // Member without role 1 should be denied
        assertFalse(service.canAccess(member.id(), folderId, null, null, List.of(), List.of()));
        // Member with role 1 should be allowed
        assertTrue(service.canAccess(member.id(), folderId, null, StationUserType.MEMBER, List.of(), List.of()));
        // Clear
        service.setRestrictions(folderId, null, RestrictionSelection.empty());
    }

    @Test
    @Order(91)
    void canAccessFileInheritsFolder() {
        // Set restriction on folder
        service.setRestrictions(
                folderId,
                null,
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));
        // Access to file in that folder checks parent folder restriction
        assertFalse(service.canAccess(member.id(), null, fileId, null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), null, fileId, StationUserType.MEMBER, List.of(), List.of()));
        // Clear
        service.setRestrictions(folderId, null, RestrictionSelection.empty());
    }

    // -- Link file and uploaded file --

    @Test
    @Order(92)
    void createLinkFile() {
        var file = service.createLinkFile(
                station.id(), null, "Google", "Search engine", "https://google.com", member.id());
        assertNotNull(file);
        assertEquals(KbFileType.LINK, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(93)
    void createUploadedTextFile() {
        byte[] data = "plain text content".getBytes(StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "notes.txt", "Some notes", data, "text/plain", member.id());
        assertNotNull(file);
        assertEquals(KbFileType.TEXT, file.fileType());
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
        assertEquals(KbFileType.IMAGE, file.fileType());
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
        byte[] data = "# Markdown Content".getBytes(StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "doc.md", "Markdown upload", data, "text/markdown", member.id());
        assertNotNull(file);
        assertEquals(KbFileType.MARKDOWN, file.fileType());
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
        assertEquals(KbFileType.OTHER, file.fileType());
        service.deleteFile(file.id());
    }

    // -- Restriction modes --

    @Test
    @Order(91)
    void canAccessRootFolderNoRestrictions() {
        assertTrue(service.canAccess(member.id(), null, null, null, List.of(), List.of()));
    }

    @Test
    @Order(93)
    void isPubliclyVisibleDenyAllWithOverride() {
        service.setPublicVisibility(folderId, null, true);
        assertTrue(service.isPubliclyVisible(PublicKbMode.DENY_ALL, folderId, null));
        service.removePublicVisibility(folderId, null);
    }

    @Test
    @Order(94)
    void isPubliclyVisibleAllowAllWithOptOut() {
        service.setPublicVisibility(folderId, null, false);
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, folderId, null));
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
        assertEquals(KbFileType.YOUTUBE, file.fileType());
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
        byte[] pdfHeader = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "document.pdf", "A PDF", pdfHeader, "application/pdf", member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PDF, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(104)
    void createUploadedFileByExtensionPdf() {
        // Detect by extension when mimeType is null
        byte[] data = new byte[] {0x01, 0x02, 0x03};
        var file = service.createUploadedFile(station.id(), null, "report.pdf", "PDF by ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PDF, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(105)
    void createUploadedFileByExtensionMarkdown() {
        byte[] data = "# MD".getBytes(StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "readme.markdown", "Markdown by ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(KbFileType.MARKDOWN, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(106)
    void createUploadedFileByExtensionTxt() {
        byte[] data = "plain".getBytes(StandardCharsets.UTF_8);
        var file = service.createUploadedFile(station.id(), null, "notes.txt", "Text by ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(KbFileType.TEXT, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(107)
    void createUploadedFileByExtensionImage() {
        byte[] data = new byte[] {0x00, 0x01};
        for (String ext : new String[] {"jpg", "jpeg", "gif", "webp", "svg"}) {
            var file = service.createUploadedFile(station.id(), null, "img." + ext, "Img", data, null, member.id());
            assertNotNull(file);
            assertEquals(KbFileType.IMAGE, file.fileType());
            service.deleteFile(file.id());
        }
    }

    // -- Presentation file type detection --

    @Test
    @Order(130)
    void createUploadedFileByMimePptx() {
        byte[] data = new byte[] {0x50, 0x4B};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "slides.pptx",
                "PPTX by mime",
                data,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        // conversionStatus is set after creation via updateConversionStatus, check via findFile
        var reloaded = service.findFile(file.id());
        assertTrue(reloaded.isPresent());
        assertNotNull(reloaded.get().conversionStatus());
        service.deleteFile(file.id());
    }

    @Test
    @Order(131)
    void createUploadedFileByMimePpt() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(), null, "old.ppt", "PPT", data, "application/vnd.ms-powerpoint", member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(132)
    void createUploadedFileByMimeOdp() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "deck.odp",
                "ODP",
                data,
                "application/vnd.oasis.opendocument.presentation",
                member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(133)
    void createUploadedFileByExtensionPptx() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(station.id(), null, "talk.pptx", "By ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(134)
    void createUploadedFileByExtensionOdp() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(station.id(), null, "talk.odp", "By ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(135)
    void presentationUploadSetsPendingThenStoreResultSetsSuccess() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "convert-test.pptx",
                "Convert me",
                data,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                member.id());
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        // Simulate successful conversion by calling storePresentationResult directly
        byte[] fakePdf = "fake-pdf".getBytes(StandardCharsets.UTF_8);
        service.storePresentationResult(station.id(), file.id(), fakePdf);
        var reloaded = service.findFile(file.id());
        assertTrue(reloaded.isPresent());
        assertEquals(ConversionStatus.SUCCESS, reloaded.get().conversionStatus());
        service.deleteFile(file.id());
    }

    @Test
    @Order(136)
    void reuploadPresentationUpdatesStatus() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "reupload-test.pptx",
                "Reupload",
                data,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                member.id());
        // Reupload triggers reconversion
        service.reuploadPresentation(file.id(), new byte[] {0x02}, "application/vnd.ms-powerpoint", "v2.ppt");
        // File should still exist and be retrievable
        var reloaded = service.findFile(file.id());
        assertTrue(reloaded.isPresent());
        service.deleteFile(file.id());
    }

    @Test
    @Order(137)
    void createUploadedFileByExtensionPpt() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(station.id(), null, "legacy.ppt", "PPT ext", data, null, member.id());
        assertNotNull(file);
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(138)
    void createUploadedFileUnknownType() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(), null, "data.bin", "Unknown", data, "application/x-custom", member.id());
        assertNotNull(file);
        assertEquals(KbFileType.OTHER, file.fileType());
        service.deleteFile(file.id());
    }

    @Test
    @Order(139)
    void reuploadPresentationResetsToPending() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "reupload-test2.pptx",
                "Will convert",
                data,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                member.id());
        // Simulate completed conversion
        service.storePresentationResult(station.id(), file.id(), "fake".getBytes(StandardCharsets.UTF_8));
        assertEquals(
                ConversionStatus.SUCCESS,
                service.findFile(file.id()).orElseThrow().conversionStatus());
        // Reupload resets to PENDING and triggers reconversion
        service.reuploadPresentation(file.id(), new byte[] {0x02}, "application/vnd.ms-powerpoint", "v2.ppt");
        var reloaded = service.findFile(file.id());
        assertTrue(reloaded.isPresent());
        assertEquals(ConversionStatus.PENDING, reloaded.get().conversionStatus());
        service.deleteFile(file.id());
    }

    @Test
    @Order(140)
    void storePresentationResultSuccess() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "store-test.pptx",
                "Store result",
                data,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                member.id());
        // Directly call storePresentationResult with fake PDF bytes
        byte[] fakePdf = "fake-pdf-content".getBytes(StandardCharsets.UTF_8);
        // Configure mock to return the stored PDF
        when(fileStorage.readPresentationPdf(station.id(), file.id()))
                .thenReturn(Optional.of(new KbFileStorageService.FileData(fakePdf, "application/pdf")));
        service.storePresentationResult(station.id(), file.id(), fakePdf);
        // Should now be SUCCESS
        var reloaded = service.findFile(file.id());
        assertTrue(reloaded.isPresent());
        assertEquals(ConversionStatus.SUCCESS, reloaded.get().conversionStatus());
        // getPresentationPdf should now return content
        var pdf = service.getPresentationPdf(file.id());
        assertTrue(pdf.isPresent());
        assertArrayEquals(fakePdf, pdf.get());
        service.deleteFile(file.id());
    }

    @Test
    @Order(141)
    void storePresentationResultFailsOnStorageError() {
        byte[] data = new byte[] {0x01};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "store-fail.pptx",
                "Storage error",
                data,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                member.id());
        // Make storage throw to trigger the error path
        try {
            doThrow(new RuntimeException("disk full"))
                    .when(fileStorage)
                    .storePresentationPdf(eq(station.id()), eq(file.id()), any());
        } catch (Exception ignored) {
        }
        service.storePresentationResult(station.id(), file.id(), "pdf".getBytes(StandardCharsets.UTF_8));
        var reloaded = service.findFile(file.id());
        assertTrue(reloaded.isPresent());
        assertEquals(ConversionStatus.FAILED, reloaded.get().conversionStatus());
        service.deleteFile(file.id());
    }

    @Test
    @Order(142)
    void conversionFailedStatusReturnsPdfEmpty() {
        var file = knowledgeBaseRepo.createFile(
                station.id(),
                null,
                "failed-conv.pptx",
                "Simulated failure",
                KbFileType.PRESENTATION,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                1,
                null,
                member.id());
        knowledgeBaseRepo.updateConversionStatus(file.id(), ConversionStatus.FAILED);
        // getPresentationPdf returns empty when no PDF was stored
        when(fileStorage.readPresentationPdf(station.id(), file.id())).thenReturn(Optional.empty());
        var pdf = service.getPresentationPdf(file.id());
        assertTrue(pdf.isEmpty());
        var reloaded = service.findFile(file.id());
        assertTrue(reloaded.isPresent());
        assertEquals(ConversionStatus.FAILED, reloaded.get().conversionStatus());
        service.deleteFile(file.id());
    }

    // -- canAccess with member-based restriction --

    @Test
    @Order(150)
    void canAccessWithMemberRestriction() {
        // Create a file with a member-based restriction
        var restrictedFile = service.createMarkdownFile(
                station.id(), null, "Restricted File", "Only for specific member", "# Secret", member.id());

        service.setRestrictions(
                null,
                restrictedFile.id(),
                new RestrictionSelection(List.of(), List.of(), List.of(), List.of(member.id()), null));

        // member (the restricted one) can access — member ID matches
        assertTrue(service.canAccess(member.id(), null, restrictedFile.id(), null, List.of(), List.of()));
        // another member ID should be denied
        assertFalse(service.canAccess(member.id() + 9999, null, restrictedFile.id(), null, List.of(), List.of()));

        service.setRestrictions(null, restrictedFile.id(), RestrictionSelection.empty());
        service.deleteFile(restrictedFile.id());
    }

    @Test
    @Order(109)
    void canAccessWithTagRestriction() {
        var tag = userTagRepo.create(station.id(), "KbTagRestriction");
        var tagRestrictedFile =
                service.createMarkdownFile(station.id(), null, "Tag File", "Tag restricted", "# Tag", member.id());

        service.setRestrictions(
                null,
                tagRestrictedFile.id(),
                new RestrictionSelection(List.of(), List.of(), List.of(tag.id()), List.of(), null));

        // Without the tag, access denied
        assertFalse(service.canAccess(member.id(), null, tagRestrictedFile.id(), null, List.of(), List.of()));
        // With the tag, access granted
        assertTrue(service.canAccess(member.id(), null, tagRestrictedFile.id(), null, List.of(), List.of(tag.id())));

        service.setRestrictions(null, tagRestrictedFile.id(), RestrictionSelection.empty());
        service.deleteFile(tagRestrictedFile.id());
    }

    @Test
    @Order(110)
    void canAccessWithGroupRestriction() {
        var group = memberGroupRepo.create(station.id(), "KbGroupRestriction");
        var groupRestrictedFile = service.createMarkdownFile(
                station.id(), null, "Group File", "Group restricted", "# Group", member.id());

        service.setRestrictions(
                null,
                groupRestrictedFile.id(),
                new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), null));

        assertFalse(service.canAccess(member.id(), null, groupRestrictedFile.id(), null, List.of(), List.of()));
        assertTrue(
                service.canAccess(member.id(), null, groupRestrictedFile.id(), null, List.of(group.id()), List.of()));

        service.setRestrictions(null, groupRestrictedFile.id(), RestrictionSelection.empty());
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
        service.setRestrictions(
                restrictedParent.id(),
                null,
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));

        var fileInFolder = service.createMarkdownFile(
                station.id(), restrictedParent.id(), "FileInRestricted", "", "# Content", member.id());

        // File in restricted folder should not be visible
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, null, fileInFolder.id()));

        service.setRestrictions(restrictedParent.id(), null, RestrictionSelection.empty());
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

    @Test
    @Order(160)
    void resolveMemberNameFallsBackToAccountName() {
        assertEquals(account.fullName(), service.resolveMemberName(member.id()));
    }

    @Test
    @Order(161)
    void resolveMemberNameForUnknownMember() {
        assertEquals("Unbekannt", service.resolveMemberName(999999));
    }

    @Test
    @Order(162)
    void memberAccessReadsGroupsAndTags() {
        var group = memberGroupRepo.create(station.id(), "KbAccessGroup");
        var tag = userTagRepo.create(station.id(), "KbAccessTag");
        memberGroupRepo.addMember(group.id(), member.id());
        userTagRepo.addMember(tag.id(), member.id());

        var access = service.memberAccess(member.id(), StationUserType.MEMBER);
        assertEquals(member.id(), access.memberId());
        assertEquals(StationUserType.MEMBER, access.userType());
        assertTrue(access.groupIds().contains(group.id()));
        assertTrue(access.tagIds().contains(tag.id()));

        userTagRepo.removeMember(tag.id(), member.id());
        memberGroupRepo.removeMember(group.id(), member.id());
        memberGroupRepo.delete(group.id());
        userTagRepo.delete(tag.id());
    }

    @Test
    @Order(163)
    void canAccessWithAccessContext() {
        var file = service.createMarkdownFile(station.id(), null, "CtxFile", "", "# Ctx", member.id());
        service.setRestrictions(
                null,
                file.id(),
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));

        assertFalse(service.canAccess(service.memberAccess(member.id(), null), null, file.id()));
        assertTrue(service.canAccess(service.memberAccess(member.id(), StationUserType.MEMBER), null, file.id()));

        service.setRestrictions(null, file.id(), RestrictionSelection.empty());
        service.deleteFile(file.id());
    }
}
