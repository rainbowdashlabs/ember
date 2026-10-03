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
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.font.FontSampleRenderer;
import dev.chojo.ember.feature.generator.service.font.FontSampleService;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pictures of sample text in the fonts a template can print in: drawn for built-in and uploaded
 * families and the default font alike, only of what the owner reaches, and drawn once for the same
 * files and style.
 */
class FontSampleServiceTest extends RepositoryTestBase {
    private static DocumentFontService fonts;
    private static FontLibrary library;
    private static Owner.Station station;
    private static Owner.Station outsider;
    private static Owner.Association association;
    private static int accountId;

    private FontSampleRenderer renderer;
    private FontSampleService samples;

    @BeforeAll
    static void setup() {
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var quota = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));
        library = newFontLibrary(storage);
        fonts = new DocumentFontService(new DocumentFontRepository(), library, storage, quota);

        var cluster = clusterService.create("Beispiel Verband", null);
        association = new Owner.Association(cluster.id());
        var member = stationRepo.create("Beispiel Wache");
        stationRepo.setCluster(member.id(), cluster.id());
        station = new Owner.Station(member.id());
        outsider = new Owner.Station(stationRepo.create("Beispiel Einzelwache").id());
        accountId = accountRepo.create("samples@test.com", "Sam", "Ple").id();
        stationMemberRepo.create(member.id(), accountId);
    }

    @BeforeEach
    void freshCache() {
        renderer = spy(new FontSampleRenderer());
        samples = new FontSampleService(library, renderer);
    }

    private static void upload(Owner owner, String family, byte[] font) {
        fonts.upload(
                owner, TestUploads.of("schrift.ttf", "font/ttf", font), family, FontStyle.REGULAR, true, accountId);
    }

    private static BufferedImage picture(byte[] png) throws IOException {
        assertEquals(
                0x89504E47,
                ((png[0] & 0xFF) << 24) | ((png[1] & 0xFF) << 16) | ((png[2] & 0xFF) << 8) | (png[3] & 0xFF),
                "a PNG");
        var image = Objects.requireNonNull(ImageIO.read(new ByteArrayInputStream(png)));
        assertTrue(image.getWidth() > image.getHeight() * 5, "one line, wider than high");
        assertTrue(image.getColorModel().hasAlpha(), "a transparent background");
        return image;
    }

    private static void refusedUnknown(Executable action) {
        assertEquals(
                DocumentRefusal.DOCUMENT_FONT_SAMPLE_UNKNOWN,
                assertThrows(RefusalResponse.class, action).refusal());
    }

    /** Each built-in family, the default font and every style draw a line of their own. */
    @Test
    void builtInFamiliesAndTheDefaultFontDrawTheirOwnLine() throws IOException {
        byte[] serif = samples.sample(outsider, "Liberation Serif", FontStyle.REGULAR);
        byte[] mono = samples.sample(outsider, "liberation mono", FontStyle.REGULAR);
        byte[] typst = samples.sample(outsider, "New Computer Modern", FontStyle.REGULAR);
        byte[] bold = samples.sample(outsider, "Liberation Serif", FontStyle.BOLD);
        byte[] standard = samples.sample(outsider, null, FontStyle.REGULAR);

        for (byte[] png : new byte[][] {serif, mono, typst, bold, standard}) picture(png);
        assertFalse(Arrays.equals(serif, mono));
        assertFalse(Arrays.equals(serif, typst));
        assertFalse(Arrays.equals(serif, bold));
        assertFalse(Arrays.equals(standard, mono));
    }

    /** An uploaded family draws in its own file, and only for an owner whose templates reach it. */
    @Test
    void anUploadedFamilyIsDrawnOnlyWhereItIsReached() throws IOException {
        upload(association, "Beispiel Lisu", TestFonts.lisu());

        byte[] lisu = samples.sample(station, "beispiel lisu", FontStyle.REGULAR);
        picture(lisu);
        assertFalse(Arrays.equals(lisu, samples.sample(station, " ", FontStyle.REGULAR)));
        samples.sample(association, "Beispiel Lisu", FontStyle.REGULAR);

        refusedUnknown(() -> samples.sample(outsider, "Beispiel Lisu", FontStyle.REGULAR));
        refusedUnknown(() -> samples.sample(new Owner.Instance(), "Beispiel Lisu", FontStyle.REGULAR));
        refusedUnknown(() -> samples.sample(station, "Gibt es nicht", FontStyle.REGULAR));
    }

    /**
     * A sample is drawn once for the same files and style, whoever asks for it, and anew for another
     * style or other files; the version the list names follows the files.
     */
    @Test
    void aSampleIsDrawnOncePerFilesAndStyle() {
        upload(new Owner.Instance(), "Geteilte Probe", TestFonts.lisu());
        upload(station, "Eigene Probe", TestFonts.lisu());
        upload(outsider, "Eigene Probe", TestFonts.lycian());

        samples.sample(station, "Geteilte Probe", FontStyle.REGULAR);
        samples.sample(outsider, "geteilte probe", FontStyle.REGULAR);
        samples.sample(association, "Geteilte Probe", FontStyle.REGULAR);
        verify(renderer, times(1)).render(any(), any());

        samples.sample(station, "Eigene Probe", FontStyle.REGULAR);
        verify(renderer, times(1)).render(any(), any());

        samples.sample(station, "Geteilte Probe", FontStyle.ITALIC);
        samples.sample(outsider, "Eigene Probe", FontStyle.REGULAR);
        verify(renderer, times(3)).render(any(), any());

        assertNotEquals(
                FontSampleService.versionOf(
                        library.familyAt(station, "Eigene Probe").orElseThrow()),
                FontSampleService.versionOf(
                        library.familyAt(outsider, "Eigene Probe").orElseThrow()));
        assertEquals(
                FontSampleService.versionOf(
                        library.familyAt(station, "Eigene Probe").orElseThrow()),
                FontSampleService.versionOf(
                        library.familyAt(station, "Geteilte Probe").orElseThrow()));
    }
}
