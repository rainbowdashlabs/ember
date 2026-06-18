/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.repository.PageFileMetaRepository;
import dev.chojo.ember.feature.page.service.PageFileStorageService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PageServiceTest extends RepositoryTestBase {
    private static PageService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int pageId;
    private static int childPageId;
    private static int imageId;

    @BeforeAll
    static void setup() {
        service = new PageService(
                pageRepo,
                new PageFileMetaRepository(),
                new PageFileStorageService(stationRepo),
                new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of())),
                stationMemberRepo,
                new ImageService());
        station = stationRepo.create("PageServiceStation");
        account = accountRepo.create("page-svc@test.com", "Page", "Author");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var page = service.create(station.id(), "Welcome Page", null, member.id());
        assertNotNull(page);
        assertEquals("welcome-page", page.slug());
        pageId = page.id();
    }

    @Test
    @Order(2)
    void slugGeneration() {
        // Test slug generation via create
        var page = service.create(station.id(), "Test Slug!", null, member.id());
        assertEquals("test-slug", page.slug());
        service.deletePage(page.id());

        var page2 = service.create(station.id(), "Ümläüts", null, member.id());
        assertEquals("umlauts", page2.slug());
        service.deletePage(page2.id());
    }

    @Test
    @Order(3)
    void duplicateSlugGetsNumber() {
        var page2 = service.create(station.id(), "Welcome Page", null, member.id());
        assertEquals("welcome-page-2", page2.slug());
        service.deletePage(page2.id());
    }

    @Test
    @Order(4)
    void getPage() {
        var page = service.getPage(pageId);
        assertTrue(page.isPresent());
        assertEquals(0, page.orElseThrow().rows().size());
    }

    @Test
    @Order(5)
    void listPages() {
        var list = service.listPages(station.id());
        assertEquals(1, list.size());
    }

    @Test
    @Order(6)
    void savePageWithContent() {
        var rows = List.of(new PageService.RowData(
                0,
                List.of(
                        new PageService.CellData(0, 60.0, CellContentType.MARKDOWN, "<h1>Hello</h1>", CellConfig.EMPTY),
                        new PageService.CellData(
                                1,
                                40.0,
                                CellContentType.IMAGE,
                                "999",
                                new CellConfig.ImageConfig(
                                        CellConfig.ImageFit.COVER,
                                        "alt",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)))));
        assertTrue(service.savePage(pageId, "Welcome", "welcome-page", null, "Test desc", null, rows));

        var page = service.getPage(pageId).orElseThrow();
        assertEquals(1, page.rows().size());
        assertEquals(2, page.rows().getFirst().cells().size());
        assertEquals("Test desc", page.metaDescription());
    }

    @Test
    @Order(7)
    void publishAndUnpublish() {
        assertTrue(service.setPublished(pageId, true));
        assertTrue(service.getPage(pageId).orElseThrow().published());

        assertTrue(service.setPublished(pageId, false));
        assertFalse(service.getPage(pageId).orElseThrow().published());
    }

    @Test
    @Order(8)
    void listPublishedPagesEmpty() {
        var list = service.listPublishedPages(station.id());
        assertEquals(0, list.size());
    }

    @Test
    @Order(9)
    void publishForLandingPage() {
        service.setPublished(pageId, true);
    }

    @Test
    @Order(10)
    void setLandingPage() {
        service.setLandingPage(station.id(), pageId);
        var landing = service.getLandingPage(station.id());
        assertTrue(landing.isPresent());
        assertEquals(pageId, landing.orElseThrow().id());
    }

    @Test
    @Order(11)
    void getLandingPageSlug() {
        var slug = service.getLandingPageSlug(station.id());
        assertTrue(slug.isPresent());
        assertEquals("welcome-page", slug.orElseThrow());
    }

    @Test
    @Order(12)
    void landingPageValidation() {
        // Page does not exist
        assertThrows(IllegalArgumentException.class, () -> service.setLandingPage(station.id(), 99999));

        // Page not published
        service.setPublished(pageId, false);
        assertThrows(IllegalArgumentException.class, () -> service.setLandingPage(station.id(), pageId));
        service.setPublished(pageId, true);
    }

    @Test
    @Order(13)
    void unpublishAutoUnsetsLandingPage() {
        service.setLandingPage(station.id(), pageId);
        service.setPublished(pageId, false);
        assertTrue(service.getLandingPage(station.id()).isEmpty());
        service.setPublished(pageId, true);
    }

    @Test
    @Order(14)
    void clearLandingPage() {
        service.setLandingPage(station.id(), pageId);
        service.setLandingPage(station.id(), null);
        assertTrue(service.getLandingPage(station.id()).isEmpty());
    }

    @Test
    @Order(15)
    void createChildPage() {
        var child = service.create(station.id(), "Child Page", pageId, member.id());
        assertNotNull(child);
        assertEquals(pageId, child.parentId());
        childPageId = child.id();
    }

    @Test
    @Order(16)
    void depthValidation() {
        service.setPublished(childPageId, true);
        var grandchild = service.create(station.id(), "Grandchild", childPageId, member.id());

        // Depth 3 would be exceeded
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(station.id(), "GreatGrandchild", grandchild.id(), member.id()));

        service.deletePage(grandchild.id());
    }

    @Test
    @Order(17)
    void unpublishedParentHidesChildren() {
        service.setPublished(pageId, false);
        service.setPublished(childPageId, true);
        var published = service.listPublishedPages(station.id());
        // Child is published but parent is not, so child is hidden
        assertTrue(published.stream().noneMatch(p -> p.id() == childPageId));
        service.setPublished(pageId, true);
    }

    @Test
    @Order(18)
    void duplicatePage() {
        var copy = service.duplicatePage(pageId, member.id());
        assertNotNull(copy);
        assertTrue(copy.title().contains("(Copy)"));
        assertNotEquals(pageId, copy.id());
        assertEquals(
                copy.rows().size(), service.getPage(pageId).orElseThrow().rows().size());
        service.deletePage(copy.id());
    }

    @Test
    @Order(19)
    void hasPublishedPages() {
        assertTrue(service.hasPublishedPages(station.id()));
    }

    @Test
    @Order(20)
    void uploadPageFile() throws Exception {
        byte[] data = new byte[1024];
        var image = service.uploadPageFile(pageId, "test.png", "image/png", data);
        assertNotNull(image);
        assertEquals("test.png", image.fileName());
        imageId = image.id();
    }

    @Test
    @Order(21)
    void readFileById() {
        var fileData = service.readFileById(imageId);
        assertTrue(fileData.isPresent());
        assertEquals("image/png", fileData.orElseThrow().contentType());
    }

    @Test
    @Order(22)
    void imageSizeLimit() {
        byte[] tooLarge = new byte[6 * 1024 * 1024];
        assertThrows(
                StorageQuotaService.StorageQuotaExceededException.class,
                () -> service.uploadPageFile(pageId, "big.png", "image/png", tooLarge));
    }

    @Test
    @Order(23)
    void deleteFile() {
        assertTrue(service.deleteFile(imageId));
        assertTrue(service.readFileById(imageId).isEmpty());
    }

    @Test
    @Order(24)
    void orphanedImageSurvivesSaveAndIsPrunedOnRequest() throws Exception {
        byte[] data = new byte[512];
        var image = service.uploadPageFile(pageId, "orphan.png", "image/png", data);
        int orphanId = image.id();

        var rows = List.of(new PageService.RowData(
                0,
                List.of(new PageService.CellData(
                        0, 100.0, CellContentType.MARKDOWN, "<p>No images</p>", CellConfig.EMPTY))));
        service.savePage(pageId, "Welcome", "welcome-page", null, null, null, rows);

        // Save no longer auto-cleans. The file must still be there.
        assertTrue(service.readFileById(orphanId).isPresent());
        assertTrue(service.findUnusedFileIds(station.id()).contains(orphanId));

        // Manual prune removes the orphan.
        int removed = service.pruneUnusedFiles(station.id());
        assertTrue(removed >= 1);
        assertTrue(service.readFileById(orphanId).isEmpty());
    }

    @Test
    @Order(25)
    void savePageNotFound() {
        assertFalse(service.savePage(99999, "X", "x", null, null, null, List.of()));
    }

    @Test
    @Order(26)
    void deletePageNotFound() {
        assertFalse(service.deletePage(99999));
    }

    @Test
    @Order(27)
    void deleteImageNotFound() {
        assertFalse(service.deleteFile(99999));
    }

    @Test
    @Order(28)
    void readImageNotFound() {
        assertTrue(service.readFileById(99999).isEmpty());
    }

    @Test
    @Order(29)
    void getPagePathRootPage() {
        var page = service.getPage(pageId).orElseThrow();
        String path = service.getPagePath(page);
        assertEquals("welcome-page", path);
    }

    @Test
    @Order(30)
    void getPagePathChildPage() {
        var child = service.getPage(childPageId).orElseThrow();
        String path = service.getPagePath(child);
        assertEquals("welcome-page/child-page", path);
    }

    @Test
    @Order(31)
    void getPageByPathRoot() {
        var page = service.getPageByPath(station.id(), "welcome-page");
        assertTrue(page.isPresent());
        assertEquals(pageId, page.orElseThrow().id());
    }

    @Test
    @Order(32)
    void getPageByPathNested() {
        var page = service.getPageByPath(station.id(), "welcome-page/child-page");
        assertTrue(page.isPresent());
        assertEquals(childPageId, page.orElseThrow().id());
    }

    @Test
    @Order(33)
    void getPageByPathNotFound() {
        var page = service.getPageByPath(station.id(), "nonexistent");
        assertTrue(page.isEmpty());
    }

    @Test
    @Order(34)
    void getPageByPathPartialNotFound() {
        var page = service.getPageByPath(station.id(), "welcome-page/nonexistent");
        assertTrue(page.isEmpty());
    }

    @Test
    @Order(40)
    void searchPagePicker() {
        var results = service.searchPagePicker(station.id(), null, 50);
        assertNotNull(results);
        assertTrue(results.stream().anyMatch(p -> "welcome-page".equals(p.slug())));
    }

    @Test
    @Order(41)
    void getLandingPageIdAndSlug() {
        service.setLandingPage(station.id(), pageId);
        try {
            assertEquals(pageId, service.getLandingPageId(station.id()).orElseThrow());
            assertEquals(
                    "welcome-page", service.getLandingPageSlug(station.id()).orElseThrow());
        } finally {
            service.setLandingPage(station.id(), null);
        }
    }

    @Test
    @Order(42)
    void listFilesWithUsageAndPickerAndDedup() throws Exception {
        byte[] data = new byte[16];
        var f1 = service.uploadPageFile(pageId, "list.png", "image/png", data);
        var dup = service.uploadPageFile(pageId, "dup.png", "image/png", data);
        assertEquals(f1.id(), dup.id());

        var listing = service.listFilesWithUsage(station.id());
        assertFalse(listing.isEmpty());
        var first = listing.getFirst();
        assertNotNull(first.file());
        assertNotNull(first.tagIds());

        assertTrue(service.deleteFile(f1.id()));
    }

    @Test
    @Order(43)
    void uploadStationFileAndReadByHash() throws Exception {
        byte[] data = "station-bytes".getBytes();
        var img = service.uploadStationFile(station.id(), "sw.png", "image/png", data);
        assertNotNull(img);

        var byHash = service.readFile(station.id(), img.contentHash());
        assertTrue(byHash.isPresent());

        assertTrue(service.readFile(station.id(), null).isEmpty());
        assertTrue(service.readFile(station.id(), "  ").isEmpty());
        assertTrue(service.readFile(station.id(), "no-such-hash").isEmpty());

        assertTrue(service.deleteFile(img.id()));
    }

    @Test
    @Order(44)
    void folderAndTagOperations() throws Exception {
        var folder = service.createFolder(station.id(), null, "Documents", 0);
        assertNotNull(folder);
        assertTrue(service.listFolders(station.id()).stream().anyMatch(f -> f.id() == folder.id()));
        assertTrue(service.updateFolder(station.id(), folder.id(), null, "Docs", 1));
        assertFalse(service.updateFolder(99999, folder.id(), null, "Nope", 0));
        assertFalse(service.deleteFolder(99999, folder.id()));

        var tag = service.createTag(station.id(), "Hero", "#ff0000");
        assertNotNull(tag);
        assertTrue(service.listTags(station.id()).stream().anyMatch(t -> t.id() == tag.id()));
        assertTrue(service.updateTag(station.id(), tag.id(), "Heroes", "#00ff00"));
        assertFalse(service.updateTag(99999, tag.id(), "X", "Y"));

        byte[] data = new byte[8];
        var file = service.uploadPageFile(pageId, "tagged.png", "image/png", data);

        assertTrue(service.assignTag(station.id(), file.id(), tag.id()));
        assertFalse(service.assignTag(99999, file.id(), tag.id()));
        assertFalse(service.assignTag(station.id(), 99999, tag.id()));
        assertFalse(service.assignTag(station.id(), file.id(), 99999));

        assertTrue(service.moveFileToFolder(station.id(), file.id(), folder.id()));
        assertFalse(service.moveFileToFolder(99999, file.id(), folder.id()));

        assertTrue(service.unassignTag(station.id(), file.id(), tag.id()));
        assertFalse(service.unassignTag(99999, file.id(), tag.id()));

        assertTrue(service.deleteFile(file.id()));
        assertTrue(service.deleteTag(station.id(), tag.id()));
        assertFalse(service.deleteTag(99999, tag.id()));
        assertTrue(service.deleteFolder(station.id(), folder.id()));
    }

    @Test
    @Order(47)
    void getPageRenderedMemberListSpotlightCell() throws Exception {
        var mapper = JsonMapper.builder().build();
        JsonNode src = mapper.readTree("{\"kind\":\"manual\",\"memberUids\":[]}");
        var memberListConfig = new CellConfig.MemberListConfig(
                "Officers", src, CellConfig.MemberListSortBy.NAME, true, true, Map.of(), List.of(), List.of());
        var rows = List.of(new PageService.RowData(
                0,
                List.of(new PageService.CellData(
                        0, 100.0, CellContentType.MEMBER_LIST_SPOTLIGHT, "", memberListConfig))));
        service.savePage(pageId, "Welcome", "welcome-page", null, null, null, rows);
        var rendered = service.getPageRendered(pageId).orElseThrow();
        var cell = rendered.rows().getFirst().cells().getFirst();
        assertEquals(CellContentType.MEMBER_LIST_SPOTLIGHT, cell.contentType());
        assertInstanceOf(CellConfig.MemberListConfig.class, cell.config());
    }

    @Test
    @Order(45)
    void getPageRenderedMarkdownCell() {
        var rows = List.of(new PageService.RowData(
                0,
                List.of(new PageService.CellData(
                        0, 100.0, CellContentType.MARKDOWN, "# Hello\n\nThis is **markdown**.", CellConfig.EMPTY))));
        service.savePage(pageId, "Welcome", "welcome-page", null, null, null, rows);
        var rendered = service.getPageRendered(pageId).orElseThrow();
        String renderedHtml = rendered.rows().getFirst().cells().getFirst().content();
        assertTrue(renderedHtml.contains("<h1") || renderedHtml.contains("<strong"));
    }

    @Test
    @Order(46)
    void updateFileMetaAccessChecks() throws Exception {
        byte[] data = new byte[4];
        var img = service.uploadPageFile(pageId, "meta.png", "image/png", data);
        try {
            assertTrue(service.updateFileMeta(station.id(), img.id(), "alt", "desc"));
            assertFalse(service.updateFileMeta(99999, img.id(), "alt", "desc"));
            assertFalse(service.updateFileMeta(station.id(), 99999, "alt", "desc"));
        } finally {
            service.deleteFile(img.id());
        }
    }

    @Test
    @Order(100)
    void deleteChild() {
        assertTrue(service.deletePage(childPageId));
    }

    @Test
    @Order(101)
    void deletePage() {
        assertTrue(service.deletePage(pageId));
    }
}
