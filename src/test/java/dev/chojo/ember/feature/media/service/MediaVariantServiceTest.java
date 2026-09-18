/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.WebpEncoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class MediaVariantServiceTest {

    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final int STATION_ID = 1;

    @TempDir
    Path tempDir;

    private MediaStorageService storage;
    private Storage config;
    private MediaVariantService variants;

    @BeforeEach
    void setup() {
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(STATION_ID)).thenReturn(STATION_UID);
        var backend = new LocalStorageBackend(tempDir);
        var resolver = new StorageBackendResolver(backend);
        var storageService = new StorageService(resolver, backend);
        storage = new MediaStorageService(storageService, stationRepo, backend);

        config = Mockito.mock(Storage.class);
        Mockito.when(config.imageVariantsEnabled()).thenReturn(true);
        Mockito.when(config.imageVariantsWebp()).thenReturn(true);
        Mockito.when(config.imageVariantsWidthList()).thenReturn(List.of(128, 256, 512));

        variants = new MediaVariantService(storage, config);
    }

    @Test
    void generatesWebpVariantsForLargePng() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping WebP assertions");
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertTrue(Files.exists(dir.resolve("w128.webp")));
        assertTrue(Files.exists(dir.resolve("w256.webp")));
        assertTrue(Files.exists(dir.resolve("w512.webp")));
    }

    @Test
    void doesNotEmitOriginalFormatResizedVariants() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping check");
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertFalse(Files.exists(dir.resolve("w128.png")));
        assertFalse(Files.exists(dir.resolve("w256.png")));
        assertFalse(Files.exists(dir.resolve("w512.png")));
        assertFalse(Files.exists(dir.resolve("orig.webp")));
    }

    @Test
    void skipsWidthsLargerOrEqualToSource() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping width-cap check");
        byte[] png = pngBytes(200, 100);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertTrue(Files.exists(dir.resolve("w128.webp")));
        assertFalse(Files.exists(dir.resolve("w256.webp")));
        assertFalse(Files.exists(dir.resolve("w512.webp")));
    }

    @Test
    void noOpWhenDisabled() throws IOException {
        Mockito.when(config.imageVariantsEnabled()).thenReturn(false);
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertFalse(Files.exists(dir.resolve("w128.png")));
        assertFalse(Files.exists(dir.resolve("w128.webp")));
    }

    @Test
    void noOpForNonImageMimeTypes() throws IOException {
        byte[] data = "hello".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(STATION_ID, hash, data, "application/pdf");

        variants.generateVariants(STATION_ID, hash, data, "application/pdf");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertFalse(Files.exists(dir.resolve("w128.webp")));
    }

    @Test
    void noOpForGifAndSvg() throws IOException {
        byte[] gif = new byte[] {'G', 'I', 'F', '8'};
        String hash = MediaStorageService.hash(gif);
        storage.store(STATION_ID, hash, gif, "image/gif");
        variants.generateVariants(STATION_ID, hash, gif, "image/gif");
        assertFalse(Files.exists(storage.hashDir(STATION_ID, hash).resolve("w128.webp")));

        byte[] svg = "<svg/>".getBytes();
        String svgHash = MediaStorageService.hash(svg);
        storage.store(STATION_ID, svgHash, svg, "image/svg+xml");
        variants.generateVariants(STATION_ID, svgHash, svg, "image/svg+xml");
        assertFalse(Files.exists(storage.hashDir(STATION_ID, svgHash).resolve("w128.webp")));
    }

    @Test
    void undecodableInputIsSwallowed() throws IOException {
        byte[] junk = new byte[] {(byte) 0xFF, (byte) 0xD8, 0, 0};
        String hash = MediaStorageService.hash(junk);
        storage.store(STATION_ID, hash, junk, "image/png");
        assertDoesNotThrow(() -> variants.generateVariants(STATION_ID, hash, junk, "image/png"));
    }

    @Test
    void readBestReturnsOrigWhenWidthIsNull() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        var result = variants.readBest(STATION_ID, hash, null, "*/*");
        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void readBestPrefersWebpWhenAcceptIncludesIt() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping WebP-preferred path");
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        var result = variants.readBest(STATION_ID, hash, 200, "image/avif,image/webp,*/*;q=0.8");
        assertTrue(result.isPresent());
        assertEquals("image/webp", result.orElseThrow().contentType());
    }

    @Test
    void readBestFallsBackToOriginalWhenClientRejectsWebp() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping fallback check");
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        var result = variants.readBest(STATION_ID, hash, 200, "image/png,*/*;q=0.8");
        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
        assertEquals(png.length, result.orElseThrow().data().length);
    }

    @Test
    void readBestSkipsWebpWhenWebpDisabledInConfig() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping config check");
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Mockito.when(config.imageVariantsWebp()).thenReturn(false);
        var result = variants.readBest(STATION_ID, hash, 200, "image/webp");
        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void readBestFallsBackToOriginalWhenNoVariantFits() throws IOException {
        byte[] png = pngBytes(200, 100);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        var result = variants.readBest(STATION_ID, hash, 4096, "*/*");
        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
        assertEquals(png.length, result.orElseThrow().data().length);
    }

    @Test
    void readBestReturnsEmptyForMissingHash() {
        assertTrue(variants.readBest(STATION_ID, "deadbeef", 256, "*/*").isEmpty());
    }

    /**
     * Stations uploaded under the old layout still hold an {@code orig.webp} beside the original.
     * Written directly rather than through the encoder, because the encoder no longer produces one.
     */
    @Test
    void readBestServesALegacyOrigWebpWhenNoWidthIsRequested() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        storage.storeVariant(STATION_ID, hash, "orig", "webp", "legacy-webp-bytes".getBytes());

        var result = variants.readBest(STATION_ID, hash, null, "image/webp,*/*;q=0.8");

        assertTrue(result.isPresent());
        assertEquals("image/webp", result.orElseThrow().contentType());
        assertArrayEquals("legacy-webp-bytes".getBytes(), result.orElseThrow().data());
    }

    @Test
    void readBestIgnoresALegacyOrigWebpWhenTheClientRejectsWebp() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        storage.storeVariant(STATION_ID, hash, "orig", "webp", "legacy-webp-bytes".getBytes());

        var result = variants.readBest(STATION_ID, hash, null, "image/png");

        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void generatesWebpVariantsForJpegSource() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping JPEG-source check");
        byte[] jpg = jpegBytes(800, 600);
        String hash = MediaStorageService.hash(jpg);
        storage.store(STATION_ID, hash, jpg, "image/jpeg");

        variants.generateVariants(STATION_ID, hash, jpg, "image/jpeg");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertTrue(Files.exists(dir.resolve("w128.webp")));
        assertTrue(Files.exists(dir.resolve("w256.webp")));
        assertFalse(Files.exists(dir.resolve("w128.jpg")));
        assertFalse(Files.exists(dir.resolve("w256.jpg")));
    }

    @Test
    void webpSourceLeavesOriginalAloneAndProducesNoCopies() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping webp-source test");
        byte[] png = pngBytes(800, 600);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        byte[] webpSource;
        try {
            webpSource = WebpEncoder.encode(decoded, 80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        String hash = MediaStorageService.hash(webpSource);
        storage.store(STATION_ID, hash, webpSource, "image/webp");

        variants.generateVariants(STATION_ID, hash, webpSource, "image/webp");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertFalse(Files.exists(dir.resolve("w128.png")), "Webp source must not produce resized PNG copies");
        assertFalse(Files.exists(dir.resolve("w128.webp")), "Webp source must not produce resized WebP copies");
        assertTrue(Files.exists(dir.resolve("orig.webp")), "Original webp stays in place");
    }

    /**
     * A sheet is recognised by its first page, so a document with pages is given one to show.
     */
    @Test
    void drawsTheFirstPageOfADocument() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping WebP assertions");
        byte[] pdf = pdfBytes();
        String hash = MediaStorageService.hash(pdf);
        storage.store(STATION_ID, hash, pdf, "application/pdf");

        variants.generateVariants(STATION_ID, hash, pdf, "application/pdf");

        assertTrue(
                variants.readPicture(STATION_ID, hash, "application/pdf", null).isPresent());
        assertTrue(
                variants.readPicture(STATION_ID, hash, "application/pdf", 128).isPresent());
    }

    /**
     * The drawn page is filed apart from the file's own variants.
     *
     * <p>Left under the name the file falls back to, every browser saying it takes WebP was handed the
     * picture in place of the document it was drawn from, which is to say downloading a sheet gave back
     * an image of its first page.
     */
    @Test
    void theDrawnPageIsNeverHandedOutInPlaceOfTheDocument() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping WebP assertions");
        byte[] pdf = pdfBytes();
        String hash = MediaStorageService.hash(pdf);
        storage.store(STATION_ID, hash, pdf, "application/pdf");

        variants.generateVariants(STATION_ID, hash, pdf, "application/pdf");

        var served = variants.readBest(STATION_ID, hash, null, "image/webp,image/*");
        assertTrue(served.isPresent());
        assertArrayEquals(pdf, served.get().data());
    }

    /**
     * A drawing is an image a browser draws and a document that can carry script, and everything else
     * here refuses to hand one back inline. It therefore has no picture, rather than one that would be
     * served as itself for want of a smaller copy.
     */
    @Test
    void aDrawingHasNoPicture() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        String hash = MediaStorageService.hash(svg);
        storage.store(STATION_ID, hash, svg, "image/svg+xml");

        assertTrue(variants.readPicture(STATION_ID, hash, "image/svg+xml", null).isEmpty());
    }

    /** A file nobody can draw says so, rather than answering with itself. */
    @Test
    void afileWithNoPictureAnswersNothing() throws IOException {
        byte[] bytes = "nothing anybody draws".getBytes();
        String hash = MediaStorageService.hash(bytes);
        storage.store(STATION_ID, hash, bytes, "application/octet-stream");

        assertTrue(variants.readPicture(STATION_ID, hash, "application/octet-stream", null)
                .isEmpty());
    }

    /** An image that never got a smaller copy is still a picture, and answers as itself. */
    @Test
    void anImageWithoutVariantsIsItsOwnPicture() throws IOException {
        byte[] png = pngBytes(64, 64);
        String hash = MediaStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        var picture = variants.readPicture(STATION_ID, hash, "image/png", null);
        assertTrue(picture.isPresent());
        assertArrayEquals(png, picture.get().data());
    }

    private static byte[] pdfBytes() throws IOException {
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] jpegBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(80, 180, 30));
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(220, 70, 30));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(50, 80, 200));
            g.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
