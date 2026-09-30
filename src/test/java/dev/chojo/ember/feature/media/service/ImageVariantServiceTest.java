/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.media.image.ImageEncoder;
import dev.chojo.ember.feature.media.image.ImageFormat;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.WebpEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageVariantServiceTest {
    private static final StorageScope.Account AVATAR_SCOPE = new StorageScope.Account(MediaLayoutFixtures.ACCOUNT_UID);
    private static final String AVATAR_KEY = MediaLayoutFixtures.ACCOUNT_UID.toString();
    private static final StorageScope.Station STATION_SCOPE =
            new StorageScope.Station(1, MediaLayoutFixtures.STATION_UID);
    private static final String AVATAR_DIR =
            "account/%s/images/avatars/%s/".formatted(MediaLayoutFixtures.ACCOUNT_UID, MediaLayoutFixtures.ACCOUNT_UID);
    private static final String LOST_ITEM_DIR =
            "station/%s/images/lost-and-found/7/".formatted(MediaLayoutFixtures.STATION_UID);

    @TempDir
    Path root;

    private StorageService storage;
    private ImageVariantService variants;

    @BeforeEach
    void setUp() {
        var backend = new LocalStorageBackend(root);
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        variants = new ImageVariantService(storage);
    }

    @Test
    void aPngUploadWritesItsOriginalAndEverySize() throws IOException {
        variants.store(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, png(300, 200), "image/png");

        assertEquals(
                List.of("1024.png", "128.png", "256.png", "512.png", "64.png", "original.png"),
                storedNames(AVATAR_DIR));
    }

    @Test
    void theStoredSizedLayoutIsReadByItsNames() {
        MediaLayoutFixtures.copyInto(root);

        assertServed(AVATAR_DIR + "64.png", read(64));
        assertServed(AVATAR_DIR + "128.png", read(100));
        assertServed(AVATAR_DIR + "original.png", read(0));
        assertServed(AVATAR_DIR + "original.png", read(2000));

        var jpeg = variants.read(
                        STATION_SCOPE, StorageCategory.IMAGE_LOST_AND_FOUND, MediaLayoutFixtures.LOST_ITEM_KEY, 200)
                .orElseThrow();
        assertArrayEquals(MediaLayoutFixtures.stored(LOST_ITEM_DIR + "256.jpg"), jpeg.data());
        assertEquals("image/jpeg", jpeg.contentType());
    }

    @Test
    void aWebpUploadReplacesThePreviousPicture() throws IOException {
        variants.store(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, png(300, 200), "image/png");
        byte[] webp = MediaLayoutFixtures.picture("picture.webp");

        variants.store(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, webp, "image/webp");

        var original = read(0).orElseThrow();
        assertArrayEquals(webp, original.data());
        assertEquals("image/webp", original.contentType());
        assertFalse(storedNames(AVATAR_DIR).contains("original.png"));
        if (WebpEncoder.isAvailable()) {
            assertTrue(storedNames(AVATAR_DIR).contains("128.webp"));
        }
    }

    @Test
    void anAnimatedGifIsKeptAsItCame() {
        byte[] gif = MediaLayoutFixtures.picture("animated.gif");

        assertStores(gif, "image/gif");

        assertArrayEquals(gif, read(0).orElseThrow().data());
        assertArrayEquals(gif, read(128).orElseThrow().data());
        assertEquals(List.of("original.gif"), storedNames(AVATAR_DIR));
    }

    @Test
    void anUploadThatCannotBeEncodedKeepsThePreviousPicture() throws IOException {
        var failing = Mockito.spy(new ImageEncoder());
        Mockito.doThrow(new IOException("no encoder")).when(failing).encode(Mockito.any(), Mockito.eq(ImageFormat.PNG));
        variants = new ImageVariantService(storage, failing);
        byte[] gif = MediaLayoutFixtures.picture("animated.gif");
        assertStores(gif, "image/gif");

        assertThrows(
                IOException.class,
                () -> variants.store(
                        AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, png(300, 200), "image/png"));

        assertArrayEquals(gif, read(0).orElseThrow().data());
    }

    @Test
    void readsNothingForAMissingKeyOrAScopeTheCategoryDoesNotTake() {
        assertTrue(read(64).isEmpty());
        assertTrue(variants.read(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, "", 64)
                .isEmpty());
        assertFalse(variants.exists(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY));
        assertTrue(variants.read(STATION_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, 64)
                .isEmpty());
        assertFalse(variants.exists(STATION_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY));
    }

    private void assertStores(byte[] data, String mime) {
        try {
            variants.store(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, data, mime);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private Optional<ImageVariantService.ImageData> read(int size) {
        return variants.read(AVATAR_SCOPE, StorageCategory.IMAGE_AVATAR, AVATAR_KEY, size);
    }

    private static void assertServed(String relative, Optional<ImageVariantService.ImageData> served) {
        assertArrayEquals(
                MediaLayoutFixtures.stored(relative), served.orElseThrow().data(), relative);
    }

    private List<String> storedNames(String relativeDir) {
        try (Stream<Path> files = Files.list(root.resolve(relativeDir))) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> !name.endsWith(".meta.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] png(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
