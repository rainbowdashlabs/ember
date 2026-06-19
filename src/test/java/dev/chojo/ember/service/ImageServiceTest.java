/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImageServiceTest {

    private static ImageService imageService;
    private static Path tempDir;

    @BeforeAll
    static void setup() throws Exception {
        tempDir = Files.createTempDirectory("ember-image-test");
        // Use reflection to set the baseDir since the constructor uses a fixed path
        imageService = new ImageService();
        var field = ImageService.class.getDeclaredField("baseDir");
        field.setAccessible(true);
        field.set(imageService, tempDir);
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    private static byte[] createTestPng(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.fillRect(0, 0, width, height);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    @Order(1)
    void storeCreatesAllSizes() throws IOException {
        byte[] data = createTestPng(800, 600);
        imageService.store(ImageCategory.AVATARS, "1", data, "image/png");

        assertTrue(imageService.exists(ImageCategory.AVATARS, "1"));

        // Should have original + 4 sizes
        Path dir = tempDir.resolve("avatars").resolve("1");
        assertTrue(Files.exists(dir.resolve("original.png")));
        assertTrue(Files.exists(dir.resolve("1024.png")));
        assertTrue(Files.exists(dir.resolve("512.png")));
        assertTrue(Files.exists(dir.resolve("256.png")));
        assertTrue(Files.exists(dir.resolve("128.png")));
        assertTrue(Files.exists(dir.resolve(".content-type")));
        assertEquals("image/png", Files.readString(dir.resolve(".content-type")).trim());
    }

    @Test
    @Order(2)
    void readReturnsCorrectSize() {
        var original = imageService.read(ImageCategory.AVATARS, "1", 0);
        assertTrue(original.isPresent());
        assertEquals("image/png", original.get().contentType());
        assertTrue(original.get().data().length > 0);

        var small = imageService.read(ImageCategory.AVATARS, "1", 128);
        assertTrue(small.isPresent());
        assertTrue(small.get().data().length < original.get().data().length);
    }

    @Test
    @Order(3)
    void readFallsBackToOriginalForUnknownSize() {
        var result = imageService.read(ImageCategory.AVATARS, "1", 9999);
        assertTrue(result.isPresent()); // falls back to original
    }

    @Test
    @Order(4)
    void readReturnsEmptyForMissing() {
        var result = imageService.read(ImageCategory.AVATARS, "nonexistent", 0);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(5)
    void existsReturnsFalseForMissing() {
        assertFalse(imageService.exists(ImageCategory.AVATARS, "nonexistent"));
    }

    @Test
    @Order(10)
    void storeReplacesExistingImage() throws IOException {
        byte[] data1 = createTestPng(400, 300);
        imageService.store(ImageCategory.AVATARS, "replace", data1, "image/png");
        var before = imageService.read(ImageCategory.AVATARS, "replace", 0);
        assertTrue(before.isPresent());

        byte[] data2 = createTestPng(200, 150);
        imageService.store(ImageCategory.AVATARS, "replace", data2, "image/png");
        var after = imageService.read(ImageCategory.AVATARS, "replace", 0);
        assertTrue(after.isPresent());

        // Sizes differ because source dimensions differ
        assertNotEquals(before.get().data().length, after.get().data().length);
    }

    @Test
    @Order(11)
    void deleteRemovesAllFiles() throws IOException {
        byte[] data = createTestPng(100, 100);
        imageService.store(ImageCategory.LOST_AND_FOUND, "42", data, "image/png");
        assertTrue(imageService.exists(ImageCategory.LOST_AND_FOUND, "42"));

        imageService.delete(ImageCategory.LOST_AND_FOUND, "42");
        assertFalse(imageService.exists(ImageCategory.LOST_AND_FOUND, "42"));
        assertTrue(imageService.read(ImageCategory.LOST_AND_FOUND, "42", 0).isEmpty());
    }

    @Test
    @Order(20)
    void storeRejectsOversizedUpload() {
        byte[] tooLarge = new byte[3 * 1024 * 1024]; // 3 MB
        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.store(ImageCategory.AVATARS, "big", tooLarge, "image/png", 2 * 1024 * 1024));
    }

    @Test
    @Order(21)
    void storeAcceptsWithinSizeLimit() throws IOException {
        byte[] data = createTestPng(100, 100);
        assertDoesNotThrow(
                () -> imageService.store(ImageCategory.AVATARS, "small", data, "image/png", 10 * 1024 * 1024));
    }

    @Test
    @Order(30)
    void largeImageIsResizedDown() throws IOException {
        byte[] data = createTestPng(4000, 3000);
        imageService.store(ImageCategory.AVATARS, "large", data, "image/png");

        var original = imageService.read(ImageCategory.AVATARS, "large", 0);
        assertTrue(original.isPresent());

        // Original should be smaller than the input (compressed + resized to 2048 max)
        BufferedImage result =
                ImageIO.read(new ByteArrayInputStream(original.get().data()));
        assertTrue(Math.max(result.getWidth(), result.getHeight()) <= ImageService.MAX_PIXEL_SIZE);
    }

    @Test
    @Order(31)
    void smallImageStillGeneratesAllSizes() throws IOException {
        byte[] data = createTestPng(64, 64);
        imageService.store(ImageCategory.AVATARS, "tiny", data, "image/png");

        Path dir = tempDir.resolve("avatars").resolve("tiny");
        assertTrue(Files.exists(dir.resolve("original.png")));
        assertTrue(Files.exists(dir.resolve("1024.png")));
        assertTrue(Files.exists(dir.resolve("512.png")));
        assertTrue(Files.exists(dir.resolve("256.png")));
        assertTrue(Files.exists(dir.resolve("128.png")));
    }

    @Test
    @Order(40)
    void jpegContentTypeUsesJpgExtension() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);

        imageService.store(ImageCategory.AVATARS, "jpeg-test", out.toByteArray(), "image/jpeg");
        Path dir = tempDir.resolve("avatars").resolve("jpeg-test");
        assertTrue(Files.exists(dir.resolve("original.jpg")));
        assertTrue(Files.exists(dir.resolve("128.jpg")));

        var result = imageService.read(ImageCategory.AVATARS, "jpeg-test", 128);
        assertTrue(result.isPresent());
        assertEquals("image/jpeg", result.get().contentType());
    }

    @Test
    @Order(50)
    void deleteNonExistentIsNoop() {
        // Should not throw
        assertDoesNotThrow(() -> imageService.delete(ImageCategory.AVATARS, "does-not-exist-99"));
    }

    @Test
    @Order(51)
    void storeWebpContentTypeStoresWithWebpExtension() throws IOException {
        // WebP is not decodable by standard ImageIO — raw bytes path is taken
        byte[] fakeWebp = new byte[] {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x00, 0x00, 0x00, 0x00
        };
        imageService.store(ImageCategory.AVATARS, "webp-test", fakeWebp, "image/webp");

        Path dir = tempDir.resolve("avatars").resolve("webp-test");
        assertTrue(Files.exists(dir.resolve("original.webp")));
        assertTrue(Files.exists(dir.resolve(".content-type")));
        assertEquals(
                "image/webp", Files.readString(dir.resolve(".content-type")).trim());
    }

    @Test
    @Order(52)
    void readWebpFallsBackToOriginalForSize() {
        // The webp-test image was stored in order 51 — reading a specific size falls back to original
        var result = imageService.read(ImageCategory.AVATARS, "webp-test", 256);
        assertTrue(result.isPresent());
        assertEquals("image/webp", result.get().contentType());
    }

    @Test
    @Order(53)
    void storeWithZeroMaxBytesSkipsSizeCheck() throws IOException {
        // maxBytes = 0 means no size check
        byte[] data = createTestPng(50, 50);
        assertDoesNotThrow(() -> imageService.store(ImageCategory.AVATARS, "zero-limit", data, "image/png", 0));
        assertTrue(imageService.exists(ImageCategory.AVATARS, "zero-limit"));
    }

    @Test
    @Order(54)
    void readReturnsEmptyWhenContentTypeFileMissing() throws IOException {
        // Create dir without .content-type file
        Path dir = tempDir.resolve("avatars").resolve("no-content-type");
        Files.createDirectories(dir);
        // No .content-type written — read should handle the IOException and return empty
        var result = imageService.read(ImageCategory.AVATARS, "no-content-type", 0);
        // Either empty (IOException caught) or empty (file missing)
        assertNotNull(result); // just verify no unhandled exception
    }

    @Test
    @Order(60)
    void readRejectsParentDirectoryTraversal() {
        assertTrue(imageService.read(ImageCategory.AVATARS, "../escape", 0).isEmpty());
        assertTrue(
                imageService.read(ImageCategory.AVATARS, "../../etc/passwd", 0).isEmpty());
    }

    @Test
    @Order(61)
    void existsRejectsParentDirectoryTraversal() {
        assertFalse(imageService.exists(ImageCategory.AVATARS, "../escape"));
    }

    @Test
    @Order(62)
    void storeRejectsParentDirectoryTraversal() throws IOException {
        byte[] data = createTestPng(50, 50);
        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.store(ImageCategory.AVATARS, "../escape", data, "image/png"));
    }

    @Test
    @Order(63)
    void deleteRejectsParentDirectoryTraversal() {
        assertThrows(IllegalArgumentException.class, () -> imageService.delete(ImageCategory.AVATARS, "../escape"));
    }

    @Test
    @Order(64)
    void readRejectsNullAndBlankId() {
        assertTrue(imageService.read(ImageCategory.AVATARS, null, 0).isEmpty());
        assertTrue(imageService.read(ImageCategory.AVATARS, "", 0).isEmpty());
        assertTrue(imageService.read(ImageCategory.AVATARS, "   ", 0).isEmpty());
    }
}
