/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.EditorFontService;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.feature.generator.service.font.WebFontService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The font files the template editor loads: only of what the owner reaches, as a document prints the
 * family, nothing for the families Typst carries itself, and the default font from its web files alone.
 */
class EditorFontServiceTest extends RepositoryTestBase {
    private static final byte[] WEB_REGULAR = TestFonts.webFont("wOF2", "regular");
    private static final byte[] WEB_BOLD = TestFonts.webFont("wOF2", "bold");
    private static final byte[] WEB_WOFF = TestFonts.webFont("wOFF", "older");

    private static StorageService storage;
    private static FontLibrary library;
    private static DocumentFontService fonts;
    private static WebFontService webFonts;
    private static EditorFontService files;
    private static Owner.Station station;
    private static Owner.Station outsider;
    private static Owner.Association association;
    private static int accountId;

    @BeforeAll
    static void setup() {
        var backend = localStorage();
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        var quota = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));
        library = newFontLibrary(storage);
        fonts = new DocumentFontService(new DocumentFontRepository(), library, storage, quota);
        webFonts = new WebFontService(new DocumentFontRepository(), library, storage, quota, fonts);
        files = new EditorFontService(library);

        var cluster = clusterService.create("Editor Verband", null);
        association = new Owner.Association(cluster.id());
        var member = stationRepo.create("Editor Wache");
        stationRepo.setCluster(member.id(), cluster.id());
        station = new Owner.Station(member.id());
        outsider = new Owner.Station(stationRepo.create("Editor Einzelwache").id());
        accountId = accountRepo.create("editor-fonts@test.com", "Edi", "Tor").id();
        stationMemberRepo.create(member.id(), accountId);

        fonts.upload(
                association,
                TestUploads.of("lisu.ttf", "font/ttf", TestFonts.lisu()),
                "Verbandsschrift",
                FontStyle.REGULAR,
                true,
                accountId);
        fonts.upload(
                station,
                TestUploads.of("lycian.otf", "font/otf", TestFonts.lycian()),
                "Wachenschrift",
                FontStyle.REGULAR,
                true,
                accountId);
    }

    private static void refused(Executable action) {
        assertEquals(
                DocumentRefusal.DOCUMENT_FONT_FILE_UNKNOWN,
                assertThrows(RefusalResponse.class, action).refusal());
    }

    private static EditorFontService withDefaultFontIn(Path directory) {
        return new EditorFontService(newFontLibrary(storage, DefaultFont.readFrom(directory)));
    }

    /** An uploaded family is served from its own file, a missing style from its regular one, as it prints. */
    @Test
    void anUploadedFamilyIsServedWhereItIsReached() {
        var lisu = files.file(station, "verbandsschrift", FontStyle.BOLD);
        assertArrayEquals(TestFonts.lisu(), lisu.data());
        assertEquals("font/ttf", lisu.mediaType());

        var lycian = files.file(station, "Wachenschrift", FontStyle.REGULAR);
        assertArrayEquals(TestFonts.lycian(), lycian.data());
        assertEquals("font/otf", lycian.mediaType());

        assertArrayEquals(
                TestFonts.lisu(),
                files.file(association, "Verbandsschrift", FontStyle.REGULAR).data());
    }

    /** Nothing goes out of a family the owner does not reach. */
    @Test
    void aFamilyOutOfReachIsRefused() {
        refused(() -> files.file(outsider, "Verbandsschrift", FontStyle.REGULAR));
        refused(() -> files.file(association, "Wachenschrift", FontStyle.REGULAR));
        refused(() -> files.file(new Owner.Instance(), "Verbandsschrift", FontStyle.REGULAR));
        refused(() -> files.file(station, "Gibt es nicht", FontStyle.REGULAR));
    }

    /** A built-in family that ships as a file is served; one Typst carries inside itself has none. */
    @Test
    void builtInFamiliesAreServedWhereTheyHaveAFile() {
        var serif = files.file(outsider, "Liberation Serif", FontStyle.BOLD);
        assertEquals("font/ttf", serif.mediaType());
        assertTrue(serif.data().length > 0);

        refused(() -> files.file(outsider, "New Computer Modern", FontStyle.REGULAR));
        refused(() -> files.file(outsider, "DejaVu Sans Mono", FontStyle.REGULAR));
    }

    /** Without a default font, a text naming no family shows in Liberation Sans, in every style. */
    @Test
    void withoutADefaultFontLiberationSansStandsIn() {
        var regular = files.file(station, null, FontStyle.REGULAR);
        var italic = files.file(station, " ", FontStyle.ITALIC);

        assertEquals("font/ttf", regular.mediaType());
        assertFalse(Arrays.equals(regular.data(), italic.data()));
        assertEquals(
                List.of(FontStyle.REGULAR, FontStyle.BOLD, FontStyle.ITALIC, FontStyle.BOLD_ITALIC),
                fonts.list(station).defaultStyles());
    }

    /** The default font goes out as its web files only, never the file a document embeds. */
    @Test
    void theDefaultFontIsServedFromItsWebFiles(@TempDir Path directory) throws IOException {
        TestFonts.defaultFontIn(directory);
        Files.write(directory.resolve("NotoSansLisuWeb-Regular.woff2"), WEB_REGULAR);
        Files.write(directory.resolve("NotoSansLisuWeb-Bold.woff2"), WEB_BOLD);
        Files.write(directory.resolve("NotoSansLisuWeb-Italic.woff2"), TestFonts.lisu());
        Files.write(
                directory.resolve("NotoSansLisuWeb-BoldItalic.woff2"),
                "wOF2 cut short".getBytes(StandardCharsets.US_ASCII));
        var served = withDefaultFontIn(directory);

        var regular = served.file(station, null, FontStyle.REGULAR);
        assertArrayEquals(WEB_REGULAR, regular.data());
        assertEquals("font/woff2", regular.mediaType());
        assertArrayEquals(WEB_BOLD, served.file(station, null, FontStyle.BOLD).data());
        refused(() -> served.file(station, null, FontStyle.ITALIC));
        assertEquals(
                List.of(FontStyle.REGULAR, FontStyle.BOLD),
                DefaultFont.readFrom(directory).webStyles());
    }

    /** A default font without web files, as an older download left it, is named rather than shown. */
    @Test
    void aDefaultFontWithoutWebFilesIsNotServed(@TempDir Path directory) throws IOException {
        TestFonts.defaultFontIn(directory);
        Files.write(directory.resolve("NotoSansLisuWeb-Bold.woff2"), WEB_BOLD);
        var served = withDefaultFontIn(directory);

        refused(() -> served.file(station, null, FontStyle.REGULAR));
        refused(() -> served.file(station, null, FontStyle.BOLD));
        assertEquals(List.of(), DefaultFont.readFrom(directory).webStyles());
    }

    /**
     * The list tells the editor which families it can load, and under a version that follows the files
     * it loads, the web version among them.
     */
    @Test
    void theListNamesWhatTheEditorCanLoad() {
        assertNotNull(editorVersion("Verbandsschrift"));
        assertNotNull(editorVersion("Liberation Sans"));
        assertNull(editorVersion("Libertinus Serif"));

        String before = editorVersion("Wachenschrift");
        var font = new DocumentFontRepository().findOwned(station).getFirst();
        webFonts.upload(station, font.id(), TestUploads.of("w.woff2", "font/woff2", WEB_REGULAR), true, accountId);
        assertNotEquals(before, editorVersion("Wachenschrift"));
        webFonts.remove(station, font.id(), accountId);
        assertEquals(before, editorVersion("Wachenschrift"));
    }

    /** An uploaded style with a web version is served as that, in its own media type, and documents keep the file. */
    @Test
    void anUploadedStyleIsServedAsItsWebVersion() {
        var font = new DocumentFontRepository().findOwned(association).getFirst();
        webFonts.upload(association, font.id(), TestUploads.of("w.woff", "font/woff", WEB_WOFF), true, accountId);
        try {
            var served = files.file(station, "Verbandsschrift", FontStyle.REGULAR);
            assertArrayEquals(WEB_WOFF, served.data());
            assertEquals("font/woff", served.mediaType());
            assertArrayEquals(
                    TestFonts.lisu(),
                    library.read(library.familyAt(station, "Verbandsschrift")
                                    .orElseThrow()
                                    .file(FontStyle.REGULAR))
                            .orElseThrow());
        } finally {
            webFonts.remove(association, font.id(), accountId);
        }
        assertArrayEquals(
                TestFonts.lisu(),
                files.file(station, "Verbandsschrift", FontStyle.REGULAR).data());
    }

    private static @Nullable String editorVersion(String family) {
        return fonts.list(station).reachable().stream()
                .filter(option -> option.family().equals(family))
                .findFirst()
                .orElseThrow()
                .editorVersion();
    }
}
