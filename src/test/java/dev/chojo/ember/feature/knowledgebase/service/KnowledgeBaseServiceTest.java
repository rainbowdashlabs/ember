/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.entity.UrlMetadata;
import dev.chojo.ember.feature.members.entity.StationMember;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeBaseServiceTest extends RepositoryTestBase {
    private static KnowledgeBaseService service;
    private static KbAccessService accessService;
    private static KbFileStorageService fileStorage;
    private static KbLinkMetadataService linkMetadataService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int folderId;
    private static int fileId;

    @BeforeAll
    static void setup() {
        fileStorage = mock(KbFileStorageService.class);
        linkMetadataService = mock(KbLinkMetadataService.class);
        when(linkMetadataService.fetchUrlMetadata(anyString())).thenReturn(new UrlMetadata(null, null));
        var storageConfig = new Storage();
        var searchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        var contentService = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                fileStorage,
                searchService);
        accessService = new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo);
        service = new KnowledgeBaseService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                accessService,
                new KbPresentationService(knowledgeBaseRepo, fileStorage, contentService),
                linkMetadataService,
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
        assertFalse(service.updateFolder(99999, "Nope", "", null, 0));
    }

    @Test
    @Order(5)
    void findAllFolders() {
        assertTrue(service.findAllFolders(station.id()).stream().anyMatch(f -> f.id() == folderId));
    }

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
        assertFalse(service.updateFile(99999, "Nope", "", null, 0));
    }

    @Test
    @Order(20)
    void createNestedFolder() {
        var child = service.createFolder(station.id(), folderId, "Subfolder", "Child folder", member.id());
        assertNotNull(child);
        var children = service.findFolders(station.id(), folderId);
        assertTrue(children.stream().anyMatch(f -> f.id() == child.id()));
        assertTrue(service.deleteFolder(child.id()));
        assertFalse(service.deleteFolder(child.id()));
    }

    @Test
    @Order(30)
    void setAndFindRelatedFiles() {
        var other = knowledgeBaseRepo.createFile(
                station.id(), null, "Related", "", KbFileType.TEXT, "text/plain", 0, null, member.id());
        service.setRelatedFiles(fileId, List.of(other.id()));
        var related = service.findRelatedFiles(fileId);
        assertTrue(related.stream().anyMatch(f -> f.id() == other.id()));
        service.setRelatedFiles(fileId, List.of());
        knowledgeBaseRepo.deleteFile(other.id());
    }

    @Test
    @Order(31)
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

    @Test
    @Order(32)
    void setSourceReference() {
        var source = service.createMarkdownFile(station.id(), null, "SrcFile", "", "# Src", member.id());
        var target = service.createMarkdownFile(station.id(), null, "TgtFile", "", "# Tgt", member.id());
        service.setSourceReference(target.id(), source.id(), station.id());
        assertTrue(service.findFile(target.id()).isPresent());
        service.deleteFile(source.id());
        service.deleteFile(target.id());
    }

    /**
     * A text upload keeps its bytes as the searchable body, so the words in the file - not just its
     * name - are what a later search matches.
     */
    @Test
    @Order(40)
    void createUploadedTextFile() {
        byte[] data = "plain text content".getBytes(StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "notes.txt", "Some notes", data, "text/plain", member.id());
        assertEquals(KbFileType.TEXT, file.fileType());
        assertEquals(
                "plain text content",
                knowledgeBaseRepo.readTextContent(file.id()).orElseThrow());
        service.deleteFile(file.id());
    }

    @Test
    @Order(41)
    void createUploadedImageFile() {
        byte[] data = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        var file =
                service.createUploadedFile(station.id(), null, "photo.png", "A photo", data, "image/png", member.id());
        assertEquals(KbFileType.IMAGE, file.fileType());
        verify(fileStorage).store(eq(station.id()), eq(file.id()), any(), eq("image/png"));
        service.deleteFile(file.id());
    }

    @Test
    @Order(42)
    void createUploadedPdfFileExtractsNothingFromGarbage() {
        byte[] pdfHeader = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        var file = service.createUploadedFile(
                station.id(), null, "document.pdf", "A PDF", pdfHeader, "application/pdf", member.id());
        assertEquals(KbFileType.PDF, file.fileType());
        assertTrue(knowledgeBaseRepo.readTextContent(file.id()).isEmpty());
        service.deleteFile(file.id());
    }

    @Test
    @Order(43)
    void createUploadedUnknownFile() {
        byte[] data = new byte[] {0x00, 0x01, 0x02};
        var file = service.createUploadedFile(
                station.id(), null, "file.dat", "Binary data", data, "application/octet-stream", member.id());
        assertEquals(KbFileType.OTHER, file.fileType());
        service.deleteFile(file.id());
    }

    /**
     * A slide deck is stored immediately and left waiting for the background conversion, so the
     * upload never blocks on the converter.
     */
    @Test
    @Order(44)
    void createUploadedPresentationWaitsForConversion() {
        byte[] data = new byte[] {0x50, 0x4B};
        var file = service.createUploadedFile(
                station.id(),
                null,
                "slides.pptx",
                "PPTX by mime",
                data,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                member.id());
        assertEquals(KbFileType.PRESENTATION, file.fileType());
        assertNotNull(service.findFile(file.id()).orElseThrow().conversionStatus());
        service.deleteFile(file.id());
    }

    @Test
    @Order(45)
    void createYoutubeFileIndexesTheVideoMetadata() {
        when(linkMetadataService.fetchYoutubeMetadata(anyString())).thenReturn("Rick Astley Never Gonna Give You Up");
        var file = service.createYoutubeFile(
                station.id(),
                null,
                "Tutorial Video",
                "A tutorial",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                member.id());
        assertEquals(KbFileType.YOUTUBE, file.fileType());
        assertTrue(knowledgeBaseRepo.readTextContent(file.id()).orElseThrow().contains("Rick Astley"));
        service.deleteFile(file.id());
    }

    @Test
    @Order(46)
    void createYoutubeFileSurvivesAnUnreachableVideo() {
        when(linkMetadataService.fetchYoutubeMetadata(anyString())).thenReturn(null);
        var file = service.createYoutubeFile(
                station.id(), null, "Offline Video", "", "https://www.youtube.com/watch?v=gone", member.id());
        assertEquals(KbFileType.YOUTUBE, file.fileType());
        assertTrue(knowledgeBaseRepo.readTextContent(file.id()).isEmpty());
        service.deleteFile(file.id());
    }

    @Test
    @Order(50)
    void createLinkFile() {
        var file = service.createLinkFile(
                station.id(), null, "Google", "Search engine", "https://google.com", member.id());
        assertEquals(KbFileType.LINK, file.fileType());
        assertEquals("Google", file.name());
        verify(linkMetadataService, never()).fetchUrlMetadata("https://google.com");
        service.deleteFile(file.id());
    }

    /**
     * A link entry left unnamed takes its name and description from the page it points at.
     */
    @Test
    @Order(51)
    void createLinkFileTakesItsNameFromThePage() {
        when(linkMetadataService.fetchUrlMetadata("https://example.com"))
                .thenReturn(new UrlMetadata("Example Domain", "An example page"));
        var file = service.createLinkFile(station.id(), null, "", "", "https://example.com", member.id());
        assertEquals("Example Domain", file.name());
        assertEquals("An example page", file.description());
        service.deleteFile(file.id());
    }

    /**
     * A page that says nothing about itself leaves the URL as the name rather than an empty entry.
     */
    @Test
    @Order(52)
    void createLinkFileFallsBackToTheUrl() {
        when(linkMetadataService.fetchUrlMetadata("https://silent.example")).thenReturn(new UrlMetadata(null, null));
        var file = service.createLinkFile(station.id(), null, "", "", "https://silent.example", member.id());
        assertEquals("https://silent.example", file.name());
        assertEquals("", file.description());
        service.deleteFile(file.id());
    }

    @Test
    @Order(53)
    void createLinkFileKeepsAGivenDescription() {
        when(linkMetadataService.fetchUrlMetadata("https://example.com/page"))
                .thenReturn(new UrlMetadata("Page Title", "Ignored"));
        var file = service.createLinkFile(
                station.id(), null, "", "Has a description", "https://example.com/page", member.id());
        assertEquals("Page Title", file.name());
        assertEquals("Has a description", file.description());
        service.deleteFile(file.id());
    }

    /**
     * Only files the public knowledge base actually serves are listed, so an item hidden by a
     * restriction or an opt-out never reaches an anonymous reader.
     */
    @Test
    @Order(60)
    void findAllPublicFilesSkipsHiddenFiles() {
        var visible = service.createMarkdownFile(station.id(), null, "Public File", "", "# Public", member.id());
        var hidden = service.createMarkdownFile(station.id(), null, "Hidden File", "", "# Hidden", member.id());
        accessService.setRestrictions(
                null,
                hidden.id(),
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));

        var publicIds = service.findAllPublicFiles(station.id(), PublicKbMode.ALLOW_ALL).stream()
                .map(KbFile::id)
                .toList();
        assertTrue(publicIds.contains(visible.id()));
        assertFalse(publicIds.contains(hidden.id()));
        assertTrue(service.findAllPublicFiles(station.id(), PublicKbMode.OFF).isEmpty());

        accessService.setRestrictions(null, hidden.id(), RestrictionSelection.empty());
        service.deleteFile(visible.id());
        service.deleteFile(hidden.id());
    }

    /**
     * Deleting a file drops the binary payload behind it, so storage is not left holding orphans.
     */
    @Test
    @Order(70)
    void deleteFileAlsoDropsTheStoredPayload() {
        var file = service.createUploadedFile(
                station.id(), null, "doomed.bin", "", new byte[] {0x01}, "application/octet-stream", member.id());
        assertTrue(service.deleteFile(file.id()));
        verify(fileStorage).delete(station.id(), file.id());
        assertFalse(service.deleteFile(file.id()));
    }

    @Test
    @Order(99)
    void deleteFileAndFolder() {
        assertTrue(service.deleteFile(fileId));
        assertTrue(service.deleteFolder(folderId));
    }
}
