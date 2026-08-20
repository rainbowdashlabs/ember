/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaReferenceRegistry;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.media.service.MediaVariantService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
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
    private static MediaLibraryService media;
    private static ContentBlockService blocks;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int pageId;
    private static int childPageId;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend();
        var resolver = new StorageBackendResolver(backend);
        var storageService = new StorageService(resolver, backend);
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
        blocks = new ContentBlockService(contentContainerRepo);
        service = new PageService(
                pageRepo, blocks, media, stationMemberRepo, new AvatarService(new ImageVariantService(storageService)));
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
        var rows = List.of(new ContentBlockService.RowData(
                0,
                List.of(
                        new ContentBlockService.CellData(
                                0, 60.0, CellContentType.MARKDOWN, "<h1>Hello</h1>", CellConfig.EMPTY),
                        new ContentBlockService.CellData(
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
        // Page does not exist (raw IllegalArgumentException - masked to generic 400 by the route handler)
        assertThrows(IllegalArgumentException.class, () -> service.setLandingPage(station.id(), 99999));

        // Page not published - user-facing BadRequestResponse so the message is preserved
        service.setPublished(pageId, false);
        assertThrows(BadRequestResponse.class, () -> service.setLandingPage(station.id(), pageId));
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

        // Depth 3 would be exceeded - user-facing BadRequestResponse
        assertThrows(
                BadRequestResponse.class,
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
    @Order(22)
    void renderedPageCarriesOgImageHash() throws Exception {
        assertNull(service.getPageRendered(pageId).orElseThrow().ogImageHash());

        var image = media.upload(station.id(), pageId, member.id(), "og.png", "image/png", new byte[64]);
        var page = service.getPage(pageId).orElseThrow();
        assertTrue(service.savePage(pageId, page.title(), page.slug(), page.parentId(), null, image.id(), List.of()));

        var rendered = service.getPageRendered(pageId).orElseThrow();
        assertEquals(image.id(), rendered.ogImageId());
        assertEquals(
                image.contentHash(),
                rendered.ogImageHash(),
                "clients build the image URL from the hash, so an id alone is not enough");

        assertTrue(service.savePage(pageId, page.title(), page.slug(), page.parentId(), null, null, List.of()));
        media.deleteFile(image.id());
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
    @Order(47)
    void getPageRenderedMemberListSpotlightCell() {
        var mapper = JsonMapper.builder().build();
        JsonNode src = mapper.readTree("{\"kind\":\"manual\",\"memberUids\":[]}");
        var memberListConfig = new CellConfig.MemberListConfig(
                "Officers", src, CellConfig.MemberListSortBy.NAME, true, true, Map.of(), List.of(), List.of());
        var rows = List.of(new ContentBlockService.RowData(
                0,
                List.of(new ContentBlockService.CellData(
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
        var rows = List.of(new ContentBlockService.RowData(
                0,
                List.of(new ContentBlockService.CellData(
                        0, 100.0, CellContentType.MARKDOWN, "# Hello\n\nThis is **markdown**.", CellConfig.EMPTY))));
        service.savePage(pageId, "Welcome", "welcome-page", null, null, null, rows);
        var rendered = service.getPageRendered(pageId).orElseThrow();
        String renderedHtml = rendered.rows().getFirst().cells().getFirst().content();
        assertTrue(renderedHtml.contains("<h1") || renderedHtml.contains("<strong"));
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
