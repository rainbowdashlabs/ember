/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.WebFontFormat;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService.DocumentFontView;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.feature.generator.service.font.WebFontService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.UploadedFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The web versions of uploaded font styles: taken only as a font, replaced and removed, counted against
 * the owner's room like the file itself, and gone with the style.
 */
class WebFontServiceTest extends RepositoryTestBase {
    private static final byte[] WOFF2 = TestFonts.webFont("wOF2", "first");
    private static final byte[] LONGER_WOFF2 = TestFonts.webFont("wOF2", "second, a little longer");

    private static StorageService storage;
    private static StorageQuotaService quota;
    private static FontLibrary library;
    private static DocumentFontService fonts;
    private static WebFontService webFonts;
    private static int accountId;

    @BeforeAll
    static void setup() {
        var backend = localStorage();
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        quota = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));
        library = newFontLibrary(storage);
        fonts = new DocumentFontService(new DocumentFontRepository(), library, storage, quota);
        webFonts = new WebFontService(new DocumentFontRepository(), library, storage, quota, fonts);
        accountId = accountRepo.create("web-fonts@test.com", "Web", "Font").id();
    }

    private static Owner.Station newStation(String name) {
        return new Owner.Station(stationRepo.create(name).id());
    }

    private static DocumentFontView uploadFont(Owner owner) {
        return fonts.upload(
                        owner,
                        TestUploads.of("lisu.ttf", "font/ttf", TestFonts.lisu()),
                        "Hausschrift",
                        FontStyle.REGULAR,
                        true,
                        accountId)
                .own()
                .getFirst();
    }

    private static UploadedFile file(String name, byte[] data) {
        return TestUploads.of(name, "application/octet-stream", data);
    }

    private static DocumentFontView webOf(Owner owner, int fontId, String name, byte[] data) {
        return webFonts.upload(owner, fontId, file(name, data), true, accountId).own().stream()
                .filter(font -> font.id() == fontId)
                .findFirst()
                .orElseThrow();
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    private static byte[] stored(Owner owner, int fontId) {
        return storage.readAllBytes(library.scopeOf(owner), FontLibrary.categoryOf(owner), fontId + "-web")
                .orElseThrow();
    }

    /** A web version is listed with the style, replaced by the next one, and the room follows both. */
    @Test
    void aWebVersionIsUploadedAndReplaced() {
        var owner = newStation("Webschrift Wache");
        var font = uploadFont(owner);
        long before = quota.getTotalUsedBytes(owner.stationId());

        var first = webOf(owner, font.id(), "haus.woff2", WOFF2);
        var web = first.web();
        assertNotNull(web);
        assertEquals("haus.woff2", web.fileName());
        assertEquals(WebFontFormat.WOFF2, web.format());
        assertEquals(WOFF2.length, web.sizeBytes());
        assertArrayEquals(WOFF2, stored(owner, font.id()));
        assertEquals(before + WOFF2.length, quota.getTotalUsedBytes(owner.stationId()));

        var second = webOf(owner, font.id(), "haus-neu.woff2", LONGER_WOFF2);
        assertEquals("haus-neu.woff2", second.web().fileName());
        assertArrayEquals(LONGER_WOFF2, stored(owner, font.id()));
        assertEquals(before + LONGER_WOFF2.length, quota.getTotalUsedBytes(owner.stationId()));
        assertArrayEquals(
                TestFonts.lisu(),
                library.read(library.familyAt(owner, "Hausschrift")
                                .orElseThrow()
                                .file(FontStyle.REGULAR))
                        .orElseThrow());
    }

    /** WOFF and the fonts an upload takes are web versions too, each with its own format. */
    @Test
    void everyWebFormatIsTaken() {
        var owner = newStation("Formate Wache");
        var font = uploadFont(owner);

        assertEquals(
                WebFontFormat.WOFF,
                webOf(owner, font.id(), "a.woff", TestFonts.webFont("wOFF", "x"))
                        .web()
                        .format());
        assertEquals(
                WebFontFormat.TRUETYPE,
                webOf(owner, font.id(), "a.ttf", TestFonts.lisu()).web().format());
        assertEquals(
                WebFontFormat.CFF,
                webOf(owner, font.id(), "a.otf", TestFonts.lycian()).web().format());
    }

    /** Removing the web version gives its room back and leaves the style itself. */
    @Test
    void aWebVersionIsRemoved() {
        var owner = newStation("Entfernen Wache");
        var font = uploadFont(owner);
        long before = quota.getTotalUsedBytes(owner.stationId());
        webOf(owner, font.id(), "haus.woff2", WOFF2);

        var removed = webFonts.remove(owner, font.id(), accountId).own().getFirst();
        assertNull(removed.web());
        assertEquals(before, quota.getTotalUsedBytes(owner.stationId()));
        assertTrue(storage.readAllBytes(library.scopeOf(owner), FontLibrary.categoryOf(owner), font.id() + "-web")
                .isEmpty());

        assertNull(webFonts.remove(owner, font.id(), accountId).own().getFirst().web());
    }

    /** Deleting the style takes its web version and the room of both with it. */
    @Test
    void aWebVersionGoesWithItsStyle() {
        var owner = newStation("Löschen Wache");
        long before = quota.getTotalUsedBytes(owner.stationId());
        var font = uploadFont(owner);
        webOf(owner, font.id(), "haus.woff2", WOFF2);

        fonts.delete(owner, font.id(), accountId);
        assertEquals(before, quota.getTotalUsedBytes(owner.stationId()));
        assertTrue(storage.readAllBytes(library.scopeOf(owner), FontLibrary.categoryOf(owner), font.id() + "-web")
                .isEmpty());
    }

    /** What is no web font, unconfirmed, too much for the room or another owner's is refused. */
    @Test
    void aWebVersionIsRefusedWithAPlainReason() {
        var owner = newStation("Ablehnen Wache");
        var font = uploadFont(owner);
        int id = font.id();

        refused(
                DocumentRefusal.DOCUMENT_WEB_FONT_NOT_A_FONT,
                () -> webOf(owner, id, "a.txt", "no font at all".getBytes(StandardCharsets.US_ASCII)));
        refused(
                DocumentRefusal.DOCUMENT_WEB_FONT_NOT_A_FONT,
                () -> webOf(owner, id, "a.woff2", "wOF2 cut short".getBytes(StandardCharsets.US_ASCII)));
        refused(
                DocumentRefusal.DOCUMENT_FONT_LICENCE_NOT_CONFIRMED,
                () -> webFonts.upload(owner, id, file("a.woff2", WOFF2), false, accountId));
        refused(DocumentRefusal.DOCUMENT_FONT_MISSING_FILE, () -> webFonts.upload(owner, id, null, true, accountId));
        refused(DocumentRefusal.DOCUMENT_FONT_NOT_HERE, () -> webOf(newStation("Fremde Wache"), id, "a.woff2", WOFF2));
        refused(DocumentRefusal.DOCUMENT_FONT_NOT_HERE, () -> webFonts.remove(new Owner.Instance(), id, accountId));

        quota.trackDelta(
                owner.stationId(),
                StorageCategory.MEMBER_DOCUMENTS,
                quota.getEffectiveTotalQuota(owner.stationId()),
                1);
        refused(DocumentRefusal.DOCUMENT_FONT_NO_ROOM, () -> webOf(owner, id, "a.woff2", WOFF2));
        assertNull(fonts.list(owner).own().getFirst().web());
    }
}
