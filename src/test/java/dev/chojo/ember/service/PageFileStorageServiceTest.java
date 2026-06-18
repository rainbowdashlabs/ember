/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.page.service.PageFileStorageService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PageFileStorageServiceTest {
    private PageFileStorageService storage;
    private final UUID stationOneUid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID stationTwoUid = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(1)).thenReturn(stationOneUid);
        Mockito.when(stationRepo.resolveUid(2)).thenReturn(stationTwoUid);
        storage = new PageFileStorageService(stationRepo);
        // Override baseDir to use temp directory
        Field baseDirField = PageFileStorageService.class.getDeclaredField("baseDir");
        baseDirField.setAccessible(true);
        baseDirField.set(storage, tempDir.resolve("page-files"));
    }

    @Test
    void hashIsDeterministicHexSha256() {
        assertEquals(PageFileStorageService.hash("hello".getBytes()), PageFileStorageService.hash("hello".getBytes()));
        assertNotEquals(PageFileStorageService.hash("a".getBytes()), PageFileStorageService.hash("b".getBytes()));
    }

    @Test
    void storeAndRead() throws IOException {
        byte[] data = "hello".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        var result = storage.read(1, hash);
        assertTrue(result.isPresent());
        assertArrayEquals(data, result.orElseThrow().data());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void storeWithoutContentType() throws IOException {
        byte[] data = "hello-no-ct".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, null);

        var result = storage.read(1, hash);
        assertTrue(result.isPresent());
        assertEquals("application/octet-stream", result.orElseThrow().contentType());
    }

    @Test
    void readNonExistent() {
        assertTrue(storage.read(1, "deadbeef").isEmpty());
    }

    @Test
    void deleteFile() throws IOException {
        byte[] data = "to-delete".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(2, hash, data, "image/jpeg");

        storage.delete(2, hash);
        assertTrue(storage.read(2, hash).isEmpty());
    }

    @Test
    void deleteNonExistent() {
        // Should not throw
        storage.delete(1, "0000");
    }

    @Test
    void deleteRemovesEntireHashDirectory() throws IOException {
        byte[] data = "swallow".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        storage.storeVariant(1, hash, "w128", "webp", new byte[] {1, 2, 3});
        storage.delete(1, hash);

        assertFalse(Files.exists(storage.hashDir(1, hash)));
    }

    @Test
    void readReturnsEmptyWhenHashDirIsMissing() {
        assertTrue(storage.read(1, "deadbeef").isEmpty());
    }

    @Test
    void filesForDifferentStationsAreIsolated() throws IOException {
        byte[] data = "same-bytes".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.store(2, hash, data, "image/png");

        storage.delete(1, hash);
        assertTrue(storage.read(1, hash).isEmpty());
        assertTrue(storage.read(2, hash).isPresent());
    }

    @Test
    void legacyFlatFileMigratesOnRead() throws IOException {
        byte[] data = "legacy-bytes".getBytes();
        String hash = PageFileStorageService.hash(data);
        Path stationDir = tempDir.resolve("page-files").resolve(stationOneUid.toString());
        Files.createDirectories(stationDir);
        Path legacyFile = stationDir.resolve(hash);
        Path legacyType = stationDir.resolve(hash + ".content-type");
        Files.write(legacyFile, data);
        Files.writeString(legacyType, "image/png");

        var result = storage.read(1, hash);
        assertTrue(result.isPresent());
        assertArrayEquals(data, result.orElseThrow().data());
        assertEquals("image/png", result.orElseThrow().contentType());

        assertFalse(Files.isRegularFile(legacyFile), "Legacy flat file should have been promoted to a directory");
        assertFalse(Files.exists(legacyType), "Legacy content-type sidecar should have been deleted");
        assertTrue(Files.isDirectory(stationDir.resolve(hash)), "Hash directory should exist after migration");
    }

    @Test
    void legacyFileWithoutContentTypeFallsBackToOctetStream() throws IOException {
        byte[] data = "no-ct".getBytes();
        String hash = PageFileStorageService.hash(data);
        Path stationDir = tempDir.resolve("page-files").resolve(stationOneUid.toString());
        Files.createDirectories(stationDir);
        Files.write(stationDir.resolve(hash), data);

        var result = storage.read(1, hash);
        assertTrue(result.isPresent());
        assertEquals("application/octet-stream", result.orElseThrow().contentType());
    }

    @Test
    void readVariantByExtensionReturnsExactMatch() throws IOException {
        byte[] data = "orig-bytes".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        byte[] webp = new byte[] {0x52, 0x49, 0x46, 0x46};
        storage.storeVariant(1, hash, "w128", "webp", webp);
        byte[] resized = new byte[] {1, 2, 3};
        storage.storeVariant(1, hash, "w128", "png", resized);

        var asWebp = storage.readVariant(1, hash, "w128", "webp");
        assertTrue(asWebp.isPresent());
        assertArrayEquals(webp, asWebp.orElseThrow().data());
        assertEquals("image/webp", asWebp.orElseThrow().contentType());

        var asPng = storage.readVariant(1, hash, "w128", "png");
        assertTrue(asPng.isPresent());
        assertArrayEquals(resized, asPng.orElseThrow().data());
        assertEquals("image/png", asPng.orElseThrow().contentType());
    }

    @Test
    void readVariantWithoutExtensionMatchesByBaseName() throws IOException {
        byte[] data = "orig-bytes".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/jpeg");

        var any = storage.readVariant(1, hash, "orig", null);
        assertTrue(any.isPresent());
        assertEquals("image/jpeg", any.orElseThrow().contentType());
    }

    @Test
    void readVariantReturnsEmptyForUnknownBase() throws IOException {
        byte[] data = "x".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        assertTrue(storage.readVariant(1, hash, "w9999", null).isEmpty());
        assertTrue(storage.readVariant(1, hash, "w9999", "webp").isEmpty());
    }

    @Test
    void storeOverwritesPreviousOriginalExtension() throws IOException {
        byte[] data = "first".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.store(1, hash, data, "image/jpeg");

        Path dir = storage.hashDir(1, hash);
        assertFalse(Files.exists(dir.resolve("orig.png")), "Stale orig.png should be removed");
        assertTrue(Files.exists(dir.resolve("orig.jpg")));
    }
}
