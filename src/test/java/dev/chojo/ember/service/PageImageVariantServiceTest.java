/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.page.service.PageFileStorageService;
import dev.chojo.ember.feature.page.service.PageImageVariantService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.WebpEncoder;
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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class PageImageVariantServiceTest {

    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final int STATION_ID = 1;

    @TempDir
    Path tempDir;

    private PageFileStorageService storage;
    private Storage config;
    private PageImageVariantService variants;

    @BeforeEach
    void setup() throws Exception {
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(STATION_ID)).thenReturn(STATION_UID);
        storage = new PageFileStorageService(stationRepo);
        Field baseDir = PageFileStorageService.class.getDeclaredField("baseDir");
        baseDir.setAccessible(true);
        baseDir.set(storage, tempDir.resolve("page-files"));

        config = Mockito.mock(Storage.class);
        Mockito.when(config.imageVariantsEnabled()).thenReturn(true);
        Mockito.when(config.imageVariantsWebp()).thenReturn(true);
        Mockito.when(config.imageVariantsWidthList()).thenReturn(List.of(128, 256, 512));

        variants = new PageImageVariantService(storage, config);
    }

    @Test
    void generatesResizedVariantsForLargePng() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertTrue(Files.exists(dir.resolve("w128.png")));
        assertTrue(Files.exists(dir.resolve("w256.png")));
        assertTrue(Files.exists(dir.resolve("w512.png")));
    }

    @Test
    void generatesWebpVariantsWhenCwebpAvailable() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available — skipping WebP assertions");
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertTrue(Files.exists(dir.resolve("w128.webp")));
        assertTrue(Files.exists(dir.resolve("w256.webp")));
        assertTrue(Files.exists(dir.resolve("w512.webp")));
        assertTrue(Files.exists(dir.resolve("orig.webp")));
    }

    @Test
    void skipsVariantsLargerThanSource() throws IOException {
        byte[] png = pngBytes(200, 100);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertTrue(Files.exists(dir.resolve("w128.png")));
        assertFalse(Files.exists(dir.resolve("w256.png")));
        assertFalse(Files.exists(dir.resolve("w512.png")));
    }

    @Test
    void noOpWhenDisabled() throws IOException {
        Mockito.when(config.imageVariantsEnabled()).thenReturn(false);
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");

        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertFalse(Files.exists(dir.resolve("w128.png")));
        assertFalse(Files.exists(dir.resolve("w128.webp")));
    }

    @Test
    void noOpForNonImageMimeTypes() throws IOException {
        byte[] data = "hello".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(STATION_ID, hash, data, "application/pdf");

        variants.generateVariants(STATION_ID, hash, data, "application/pdf");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertFalse(Files.exists(dir.resolve("w128.webp")));
    }

    @Test
    void noOpForGifAndSvg() throws IOException {
        byte[] gif = new byte[] {'G', 'I', 'F', '8'};
        String hash = PageFileStorageService.hash(gif);
        storage.store(STATION_ID, hash, gif, "image/gif");
        variants.generateVariants(STATION_ID, hash, gif, "image/gif");
        assertFalse(Files.exists(storage.hashDir(STATION_ID, hash).resolve("w128.webp")));

        byte[] svg = "<svg/>".getBytes();
        String svgHash = PageFileStorageService.hash(svg);
        storage.store(STATION_ID, svgHash, svg, "image/svg+xml");
        variants.generateVariants(STATION_ID, svgHash, svg, "image/svg+xml");
        assertFalse(Files.exists(storage.hashDir(STATION_ID, svgHash).resolve("w128.webp")));
    }

    @Test
    void undecodableInputIsSwallowed() throws IOException {
        byte[] junk = new byte[] {(byte) 0xFF, (byte) 0xD8, 0, 0};
        String hash = PageFileStorageService.hash(junk);
        storage.store(STATION_ID, hash, junk, "image/png");
        assertDoesNotThrow(() -> variants.generateVariants(STATION_ID, hash, junk, "image/png"));
    }

    @Test
    void readBestReturnsOrigWhenWidthIsNull() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        var result = variants.readBest(STATION_ID, hash, null, "*/*");
        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void readBestPicksSmallestVariantAboveRequestedWidth() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        var result = variants.readBest(STATION_ID, hash, 200, "*/*");
        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
        long size256 = Files.size(storage.hashDir(STATION_ID, hash).resolve("w256.png"));
        assertEquals(size256, result.orElseThrow().data().length);
    }

    @Test
    void readBestPrefersWebpWhenAcceptIncludesIt() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available — skipping WebP-preferred path");
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        var result = variants.readBest(STATION_ID, hash, 200, "image/avif,image/webp,*/*;q=0.8");
        assertTrue(result.isPresent());
        assertEquals("image/webp", result.orElseThrow().contentType());
    }

    @Test
    void readBestFallsBackToOriginalFormatWhenWebpUnavailable() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
        storage.store(STATION_ID, hash, png, "image/png");
        variants.generateVariants(STATION_ID, hash, png, "image/png");

        Path webpAt256 = storage.hashDir(STATION_ID, hash).resolve("w256.webp");
        Files.deleteIfExists(webpAt256);
        Files.deleteIfExists(storage.hashDir(STATION_ID, hash).resolve("orig.webp"));

        var result = variants.readBest(STATION_ID, hash, 200, "image/webp,*/*");
        assertTrue(result.isPresent());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void readBestSkipsWebpWhenWebpDisabledInConfig() throws IOException {
        byte[] png = pngBytes(800, 600);
        String hash = PageFileStorageService.hash(png);
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
        String hash = PageFileStorageService.hash(png);
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

    @Test
    void generatesResizedJpegVariants() throws IOException {
        byte[] jpg = jpegBytes(800, 600);
        String hash = PageFileStorageService.hash(jpg);
        storage.store(STATION_ID, hash, jpg, "image/jpeg");

        variants.generateVariants(STATION_ID, hash, jpg, "image/jpeg");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertTrue(Files.exists(dir.resolve("w128.jpg")));
        assertTrue(Files.exists(dir.resolve("w256.jpg")));
    }

    @Test
    void webpSourceDoesNotEmitDoubleEncodedWebp() throws IOException {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available — skipping webp-source test");
        byte[] png = pngBytes(800, 600);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        byte[] webpSource;
        try {
            webpSource = WebpEncoder.encode(decoded, 80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        String hash = PageFileStorageService.hash(webpSource);
        storage.store(STATION_ID, hash, webpSource, "image/webp");

        variants.generateVariants(STATION_ID, hash, webpSource, "image/webp");

        Path dir = storage.hashDir(STATION_ID, hash);
        assertFalse(Files.exists(dir.resolve("w128.png")), "Webp source must not produce resized PNG copies");
        assertFalse(Files.exists(dir.resolve("w128.webp")), "Webp source must not produce resized WebP copies");
        assertTrue(Files.exists(dir.resolve("orig.webp")), "Original webp stays in place");
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
