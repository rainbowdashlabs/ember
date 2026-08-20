/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaStorageServiceTest {
    private MediaStorageService storage;
    private final UUID stationOneUid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID stationTwoUid = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(1)).thenReturn(stationOneUid);
        Mockito.when(stationRepo.resolveUid(2)).thenReturn(stationTwoUid);
        var backend = new LocalStorageBackend(tempDir);
        var resolver = new StorageBackendResolver(backend);
        var storageService = new StorageService(resolver, backend);
        storage = new MediaStorageService(storageService, stationRepo, backend);
    }

    @Test
    void hashIsDeterministicHexSha256() {
        assertEquals(MediaStorageService.hash("hello".getBytes()), MediaStorageService.hash("hello".getBytes()));
        assertNotEquals(MediaStorageService.hash("a".getBytes()), MediaStorageService.hash("b".getBytes()));
    }

    @Test
    void storeAndRead() throws IOException {
        byte[] data = "hello".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        var result = storage.read(1, hash);
        assertTrue(result.isPresent());
        assertArrayEquals(data, result.orElseThrow().data());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void storeWithoutContentType() throws IOException {
        byte[] data = "hello-no-ct".getBytes();
        String hash = MediaStorageService.hash(data);
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
        String hash = MediaStorageService.hash(data);
        storage.store(2, hash, data, "image/jpeg");

        storage.delete(2, hash);
        assertTrue(storage.read(2, hash).isEmpty());
    }

    @Test
    void deleteNonExistent() {
        storage.delete(1, "0000");
    }

    @Test
    void deleteRemovesEntireHashDirectory() throws IOException {
        byte[] data = "swallow".getBytes();
        String hash = MediaStorageService.hash(data);
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
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.store(2, hash, data, "image/png");

        storage.delete(1, hash);
        assertTrue(storage.read(1, hash).isEmpty());
        assertTrue(storage.read(2, hash).isPresent());
    }

    @Test
    void readVariantByExtensionReturnsExactMatch() throws IOException {
        byte[] data = "orig-bytes".getBytes();
        String hash = MediaStorageService.hash(data);
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
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/jpeg");

        var any = storage.readVariant(1, hash, "orig", null);
        assertTrue(any.isPresent());
        assertEquals("image/jpeg", any.orElseThrow().contentType());
    }

    @Test
    void readVariantReturnsEmptyForUnknownBase() throws IOException {
        byte[] data = "x".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        assertTrue(storage.readVariant(1, hash, "w9999", null).isEmpty());
        assertTrue(storage.readVariant(1, hash, "w9999", "webp").isEmpty());
    }

    @Test
    void storeOverwritesPreviousOriginalExtension() throws IOException {
        byte[] data = "first".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.store(1, hash, data, "image/jpeg");

        Path dir = storage.hashDir(1, hash);
        assertFalse(Files.exists(dir.resolve("orig.png")), "Stale orig.png should be removed");
        assertTrue(Files.exists(dir.resolve("orig.jpg")));
    }

    @Test
    void variantContentTypeFollowsTheFileExtension() throws IOException {
        byte[] data = "typed".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "application/pdf");

        storage.storeVariant(1, hash, "anim", "gif", new byte[] {1});
        storage.storeVariant(1, hash, "vector", "svg", new byte[] {2});
        storage.storeVariant(1, hash, "doc", "pdf", new byte[] {3});
        storage.storeVariant(1, hash, "odd", "bin", new byte[] {4});

        assertEquals(
                "image/gif",
                storage.readVariant(1, hash, "anim", "gif").orElseThrow().contentType());
        assertEquals(
                "image/svg+xml",
                storage.readVariant(1, hash, "vector", "svg").orElseThrow().contentType());
        assertEquals(
                "application/pdf",
                storage.readVariant(1, hash, "doc", "pdf").orElseThrow().contentType());
        assertEquals(
                "application/octet-stream",
                storage.readVariant(1, hash, "odd", "bin").orElseThrow().contentType());
    }

    @Test
    void aWebpOnlyVariantIsServedWhenNothingElseCarriesThatBaseName() throws IOException {
        byte[] data = "webp-only".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.storeVariant(1, hash, "w256", "webp", new byte[] {9});

        var served = storage.readVariant(1, hash, "w256", null);
        assertTrue(served.isPresent());
        assertEquals("image/webp", served.orElseThrow().contentType());
    }

    @Test
    void legacyPageFilesDirectoryIsRelocated() throws IOException {
        byte[] data = "legacy-tree".getBytes();
        String hash = MediaStorageService.hash(data);
        Path legacyDir =
                tempDir.resolve("page-files").resolve(stationOneUid.toString()).resolve(hash);
        Files.createDirectories(legacyDir);
        Files.write(legacyDir.resolve("orig.png"), data);

        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(1)).thenReturn(stationOneUid);
        var backend = new LocalStorageBackend(tempDir);
        var resolver = new StorageBackendResolver(backend);
        var storageService = new StorageService(resolver, backend);
        var migrated = new MediaStorageService(storageService, stationRepo, backend);

        var result = migrated.read(1, hash);
        assertTrue(result.isPresent());
        assertArrayEquals(data, result.orElseThrow().data());
        assertFalse(Files.exists(tempDir.resolve("page-files").resolve(stationOneUid.toString())));
    }

    @Test
    void legacyRelocationLeavesAStationThatIsAlreadyThereAlone() throws IOException {
        byte[] current = "already-moved".getBytes();
        String hash = MediaStorageService.hash(current);
        storage.store(1, hash, current, "image/png");

        Path legacyDir =
                tempDir.resolve("page-files").resolve(stationOneUid.toString()).resolve(hash);
        Files.createDirectories(legacyDir);
        Files.write(legacyDir.resolve("orig.png"), "stale".getBytes());

        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(1)).thenReturn(stationOneUid);
        var backend = new LocalStorageBackend(tempDir);
        var storageService = new StorageService(new StorageBackendResolver(backend), backend);
        var migrated = new MediaStorageService(storageService, stationRepo, backend);

        assertArrayEquals(
                current,
                migrated.read(1, hash).orElseThrow().data(),
                "what the station already holds is never overwritten by the legacy tree");
    }
}
