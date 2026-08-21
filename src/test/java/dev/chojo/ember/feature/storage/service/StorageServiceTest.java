/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ACCOUNT_UID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @TempDir
    Path tempDir;

    private StorageService service;

    @BeforeEach
    void setup() {
        LocalStorageBackend backend = new LocalStorageBackend(tempDir);
        service = new StorageService(new StorageBackendResolver(backend), backend);
    }

    @Test
    void storeAndReadByteArray() {
        byte[] data = "payload".getBytes();
        var stored =
                service.store(stationScope(), StorageCategory.MEDIA_FILES, "hash1", data, "application/octet-stream");
        assertEquals("hash1", stored.key());
        assertEquals(StorageCategory.MEDIA_FILES, stored.category());
        assertEquals(data.length, stored.contentLength());
        assertFalse(stored.metadata().sha256().isEmpty());

        var read = service.readAllBytes(stationScope(), StorageCategory.MEDIA_FILES, "hash1");
        assertTrue(read.isPresent());
        assertArrayEquals(data, read.get());
    }

    @Test
    void storeAndReadStreaming() throws Exception {
        byte[] data = "streamed".getBytes();
        service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "hash2",
                Variant.ORIGINAL,
                new ByteArrayInputStream(data),
                data.length,
                "application/octet-stream");
        try (StoredStream stream = service.read(stationScope(), StorageCategory.MEDIA_FILES, "hash2")
                .orElseThrow()) {
            assertArrayEquals(data, stream.body().readAllBytes());
            assertEquals(data.length, stream.contentLength());
        }
    }

    @Test
    void readMissingReturnsEmpty() {
        assertTrue(service.read(stationScope(), StorageCategory.MEDIA_FILES, "missing")
                .isEmpty());
        assertTrue(service.readAllBytes(stationScope(), StorageCategory.MEDIA_FILES, "missing")
                .isEmpty());
    }

    @Test
    void existsReflectsStoreState() {
        service.store(
                stationScope(), StorageCategory.MEDIA_FILES, "exists", new byte[] {1}, "application/octet-stream");
        assertTrue(service.exists(stationScope(), StorageCategory.MEDIA_FILES, "exists"));
        service.delete(stationScope(), StorageCategory.MEDIA_FILES, "exists");
        assertFalse(service.exists(stationScope(), StorageCategory.MEDIA_FILES, "exists"));
    }

    @Test
    void deletePrefixRemovesAllVariants() {
        service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "bulk",
                new Variant("orig.png"),
                new byte[] {1},
                "application/octet-stream");
        service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "bulk",
                new Variant("w128.webp"),
                new byte[] {2},
                "application/octet-stream");

        service.deletePrefix(stationScope(), StorageCategory.MEDIA_FILES, "bulk");

        assertFalse(service.exists(stationScope(), StorageCategory.MEDIA_FILES, "bulk", new Variant("orig.png")));
        assertFalse(service.exists(stationScope(), StorageCategory.MEDIA_FILES, "bulk", new Variant("w128.webp")));
    }

    @Test
    void listKeysReturnsRelativePaths() {
        service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "list",
                new Variant("orig.png"),
                new byte[] {1},
                "application/octet-stream");
        service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "list",
                new Variant("w128.webp"),
                new byte[] {2},
                "application/octet-stream");

        List<String> keys = service.listKeys(stationScope(), StorageCategory.MEDIA_FILES, "list");
        assertTrue(keys.contains("list/orig.png"));
        assertTrue(keys.contains("list/w128.webp"));
    }

    @Test
    void listKeysWithEmptyPrefixListsCategory() {
        service.store(stationScope(), StorageCategory.MEDIA_FILES, "k1", new byte[] {1}, "application/octet-stream");
        List<String> keys = service.listKeys(stationScope(), StorageCategory.MEDIA_FILES, "");
        assertFalse(keys.isEmpty());
    }

    @Test
    void rejectMimeOnRestrictedCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.store(
                        instanceScope(), StorageCategory.IMAGE_LOGO_FRAGMENT, "logo", new byte[] {1}, "text/plain"));
    }

    @Test
    void rejectScopeMismatch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.store(
                        instanceScope(), StorageCategory.MEDIA_FILES, "k", new byte[] {1}, "application/octet-stream"));
    }

    @Test
    void accountScopeRoundTrips() {
        var scope = new StorageScope.Account(ACCOUNT_UID);
        service.store(scope, StorageCategory.IMAGE_AVATAR, "me", new byte[] {9}, "image/png");
        assertTrue(service.exists(scope, StorageCategory.IMAGE_AVATAR, "me"));
    }

    @Test
    void sumSizeIncludesStoredBytes() {
        service.store(
                stationScope(), StorageCategory.MEDIA_FILES, "s1", new byte[] {1, 2, 3}, "application/octet-stream");
        service.store(stationScope(), StorageCategory.MEDIA_FILES, "s2", new byte[] {4, 5}, "application/octet-stream");
        long total = service.sumSize(stationScope(), StorageCategory.MEDIA_FILES);
        assertEquals(5, total);
    }

    @Test
    void touchAndLastAccessedForAccessTimeLruCategory() {
        service.store(instanceScope(), StorageCategory.MAP_TILE_CACHE, "tile", new byte[] {7}, "image/png");
        service.read(instanceScope(), StorageCategory.MAP_TILE_CACHE, "tile").ifPresent(s -> {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        });
        assertTrue(service.lastAccessed(instanceScope(), StorageCategory.MAP_TILE_CACHE, "tile")
                .isPresent());
    }

    @Test
    void byteArrayOverloadWithVariantWritesUnderVariantKey() {
        byte[] data = "vp".getBytes();
        service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "vk",
                new Variant("orig.png"),
                data,
                "application/octet-stream");
        assertTrue(service.exists(stationScope(), StorageCategory.MEDIA_FILES, "vk", new Variant("orig.png")));
    }

    @Test
    void singleByteStreamingDrivesDigest() {
        byte[] data = new byte[] {7};
        var stored = service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "single",
                Variant.ORIGINAL,
                new ByteArrayInputStream(data) {
                    @Override
                    public int read(byte[] b, int off, int len) {
                        if (available() == 0) return -1;
                        int v = read();
                        if (v < 0) return -1;
                        b[off] = (byte) v;
                        return 1;
                    }
                },
                data.length,
                "application/octet-stream");
        assertFalse(stored.metadata().sha256().isEmpty());
    }

    @Test
    void sumSizeOnEmptyCategoryIsZero() {
        assertEquals(0L, service.sumSize(stationScope(), StorageCategory.MEDIA_FILES));
    }

    @Test
    void lastAccessedReturnsEmptyForMissingKey() {
        assertTrue(service.lastAccessed(stationScope(), StorageCategory.MEDIA_FILES, "nope")
                .isEmpty());
    }

    @Test
    void readVariantTouchesAccessTimeOnLruCategory() {
        service.store(instanceScope(), StorageCategory.MAP_TILE_CACHE, "lru", new byte[] {1}, "image/png");
        var first = service.read(instanceScope(), StorageCategory.MAP_TILE_CACHE, "lru")
                .orElseThrow();
        try {
            first.close();
        } catch (Exception ignored) {
        }
        assertTrue(service.lastAccessed(instanceScope(), StorageCategory.MAP_TILE_CACHE, "lru")
                .isPresent());
    }

    @Test
    void readWithVariantReturnsBytes() {
        service.store(
                stationScope(),
                StorageCategory.MEDIA_FILES,
                "rv",
                new Variant("v"),
                new byte[] {9},
                "application/octet-stream");
        var bytes = service.readAllBytes(stationScope(), StorageCategory.MEDIA_FILES, "rv", new Variant("v"))
                .orElseThrow();
        assertArrayEquals(new byte[] {9}, bytes);
    }

    @Test
    void fullKeyAssemblesExpectedPath() {
        String full = service.fullKey(stationScope(), StorageCategory.MEDIA_FILES, "abc", new Variant("orig.png"));
        assertEquals("station/" + STATION_UID + "/media/files/abc/orig.png", full);

        String original = service.fullKey(stationScope(), StorageCategory.MEDIA_FILES, "abc", Variant.ORIGINAL);
        assertEquals("station/" + STATION_UID + "/media/files/abc", original);
    }

    private StorageScope.Station stationScope() {
        return new StorageScope.Station(1, STATION_UID);
    }

    private StorageScope.Instance instanceScope() {
        return new StorageScope.Instance();
    }
}
