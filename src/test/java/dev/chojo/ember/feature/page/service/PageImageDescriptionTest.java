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
import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaReferenceRegistry;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.media.service.MediaVariantService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a picture on a page is called.
 *
 * <p>A file is described once, where it lives. A tile showing it should say what the file says
 * unless it has something of its own to say, or a picture reaches a public page with no alt text at
 * all even though somebody had written one.
 */
class PageImageDescriptionTest extends RepositoryTestBase {

    private static PageService service;
    private static MediaLibraryService media;
    private static ContentBlockService blocks;
    private static Station station;
    private static Account account;
    private static StationMember member;

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
                new StorageQuotaService(storageUsageRepo, storageConfig, new DomainEventBus(Set.of())));
        blocks = new ContentBlockService(contentContainerRepo);
        service = new PageService(
                pageRepo,
                blocks,
                media,
                new CellDescriptions(media),
                stationMemberRepo,
                new AvatarService(new ImageVariantService(storageService)));
        station = stationRepo.create("ImageDescriptionStation");
        account = accountRepo.create("image-desc@test.com", "Image", "Author");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aTileSayingNothingShowsWhatTheFileSays() throws Exception {
        var file = describedFile("wappen.png", "Das Wappen der Wache", "Aufgenommen beim Tag der offenen Tür");
        var page = pageShowing(file.contentHash(), imageSaying(null, null));

        var image = (CellConfig.ImageConfig) firstCellConfig(page);

        assertEquals("Das Wappen der Wache", image.altText());
        assertEquals("Aufgenommen beim Tag der offenen Tür", image.description());
    }

    /** The same picture means different things in different places, so the nearer word wins. */
    @Test
    void aTileWithItsOwnWordsKeepsThem() throws Exception {
        var file = describedFile("gruppe.png", "Die Gruppe", "Beim Zeltlager");
        var page = pageShowing(file.contentHash(), imageSaying("Unsere Jugendgruppe", "Sommer 2026"));

        var image = (CellConfig.ImageConfig) firstCellConfig(page);

        assertEquals("Unsere Jugendgruppe", image.altText());
        assertEquals("Sommer 2026", image.description());
    }

    /** A field opened and left empty means nothing to add, not that the picture goes unnamed. */
    @Test
    void anEmptyFieldCountsAsUnsaid() throws Exception {
        var file = describedFile("fahrzeug.png", "Das Fahrzeug", "Vor der Halle");
        var page = pageShowing(file.contentHash(), imageSaying("   ", ""));

        var image = (CellConfig.ImageConfig) firstCellConfig(page);

        assertEquals("Das Fahrzeug", image.altText());
        assertEquals("Vor der Halle", image.description());
    }

    /** A tile holding no picture yet must still render rather than going looking for one. */
    @Test
    void aTileWithNoPictureIsLeftAlone() {
        var page = pageShowing("", imageSaying("nur Text", null));

        var image = (CellConfig.ImageConfig) firstCellConfig(page);

        assertEquals("nur Text", image.altText());
    }

    private static StationFile describedFile(String name, String alt, String description) throws Exception {
        var file = media.upload(station.id(), null, member.id(), name, "image/png", new byte[64]);
        media.updateFileMeta(station.id(), file.id(), alt, description);
        return file;
    }

    private static CellConfig.ImageConfig imageSaying(String alt, String description) {
        return new CellConfig.ImageConfig(
                CellConfig.ImageFit.COVER, alt, null, description, null, null, null, null, null, null, null);
    }

    private static StationPage pageShowing(String imageHash, CellConfig.ImageConfig config) {
        var page = service.create(station.id(), "Bildseite " + pageCounter++, null, member.id());
        var rows = List.of(new ContentBlockService.RowData(
                0, List.of(new ContentBlockService.CellData(0, 100.0, CellContentType.IMAGE, imageHash, config))));
        service.savePage(page.id(), page.title(), page.slug(), null, null, null, rows);
        return service.getPageRendered(page.id()).orElseThrow();
    }

    private static int pageCounter = 1;

    private static CellConfig firstCellConfig(StationPage page) {
        return page.rows().get(0).cells().get(0).config();
    }
}
