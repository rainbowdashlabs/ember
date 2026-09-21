/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The picture a wiki tile shows: made of an image and of a PDF, not of anything else, made late
 * for a file stored before pictures existed, and gone with the file.
 */
class KbFilePictureServiceTest {

    private static final int STATION_ID = 1;
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @TempDir
    Path tempDir;

    private KbFileStorageService files;
    private KbFilePictureService pictures;

    @BeforeEach
    void setup() {
        var config = Mockito.mock(Storage.class);
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(STATION_ID)).thenReturn(STATION_UID);
        var backend = new LocalStorageBackend(tempDir);
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        files = Mockito.spy(new KbFileStorageService(storage, stationRepo, backend, new TextCompressionPolicy(config)));
        pictures = new KbFilePictureService(new ImageVariantService(storage), files, stationRepo);
    }

    private static byte[] photo() throws IOException {
        var image = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] onePagePdf() throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            var out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        }
    }

    private static int widthOf(byte[] image) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(image)).getWidth();
    }

    @Test
    void aPhotographIsShownSmallerThanItWasUploaded() throws IOException {
        pictures.make(STATION_ID, 1, "image/png", photo());

        var picture = pictures.read(STATION_ID, 1, "image/png", 256).orElseThrow();

        assertTrue(widthOf(picture.data()) <= 256);
    }

    @Test
    void aPdfIsShownByItsFirstPage() throws IOException {
        pictures.make(STATION_ID, 2, "application/pdf", onePagePdf());

        assertTrue(pictures.read(STATION_ID, 2, "application/pdf", 512).isPresent());
    }

    @Test
    void aSpreadsheetHasNoPicture() {
        pictures.make(STATION_ID, 3, "text/csv", "a;b".getBytes(StandardCharsets.UTF_8));

        assertTrue(pictures.read(STATION_ID, 3, "text/csv", 256).isEmpty());
    }

    /** A PDF swapped for a spreadsheet must not go on showing the page it no longer has. */
    @Test
    void aFileReplacedByOneWithoutAPictureLosesTheOldOne() throws IOException {
        pictures.make(STATION_ID, 4, "application/pdf", onePagePdf());

        pictures.make(STATION_ID, 4, "text/csv", "a;b".getBytes(StandardCharsets.UTF_8));

        assertTrue(pictures.read(STATION_ID, 4, "application/pdf", 256).isEmpty());
    }

    /** A wiki full of files from before this existed has to light up without being uploaded again. */
    @Test
    void aFileStoredBeforePicturesExistedGetsOneWhenFirstAskedFor() throws IOException {
        files.store(STATION_ID, 5, photo(), "image/png");

        assertTrue(pictures.read(STATION_ID, 5, "image/png", 256).isPresent());
    }

    /** A grid showing a broken file every visit must not render and fail and warn every visit. */
    @Test
    void aFileNoPictureCanBeMadeOfIsNotTriedAgain() {
        files.store(STATION_ID, 6, "not a png".getBytes(StandardCharsets.UTF_8), "image/png");

        assertTrue(pictures.read(STATION_ID, 6, "image/png", 256).isEmpty());
        assertTrue(pictures.read(STATION_ID, 6, "image/png", 256).isEmpty());

        Mockito.verify(files, Mockito.times(1)).read(STATION_ID, 6);
    }

    @Test
    void deletingTheFileTakesItsPictureWithIt() throws IOException {
        byte[] photo = photo();
        files.store(STATION_ID, 7, photo, "image/png");
        pictures.make(STATION_ID, 7, "image/png", photo);

        files.delete(STATION_ID, 7);

        assertTrue(pictures.read(STATION_ID, 7, "image/png", 256).isEmpty());
    }
}
