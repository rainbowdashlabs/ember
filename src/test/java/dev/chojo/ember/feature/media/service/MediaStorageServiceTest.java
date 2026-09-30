/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaStorageServiceTest {
    private final UUID stationOneUid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID stationTwoUid = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    Path tempDir;

    private MediaStorageService storage;
    private ImageVariants images;

    @BeforeEach
    void setup() {
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(1)).thenReturn(stationOneUid);
        Mockito.when(stationRepo.resolveUid(2)).thenReturn(stationTwoUid);
        var backend = new LocalStorageBackend(tempDir);
        var storageService = new StorageService(new StorageBackendResolver(backend), backend);
        storage = new MediaStorageService(storageService, stationRepo, backend);
        images = new ImageVariants(storageService);
    }

    @Test
    void hashIsDeterministicHexSha256() {
        assertEquals(MediaStorageService.hash("hello".getBytes()), MediaStorageService.hash("hello".getBytes()));
        assertNotEquals(MediaStorageService.hash("a".getBytes()), MediaStorageService.hash("b".getBytes()));
    }

    @Test
    void aStationFileLivesInTheStationLibraryAndAnInstanceFileInTheInstances() {
        var station = storage.locate(1, "abc");
        assertEquals(new StorageScope.Station(1, stationOneUid), station.scope());
        assertEquals(StorageCategory.MEDIA_FILES, station.category());
        assertEquals("abc", station.key());

        var instance = storage.locate(null, "abc");
        assertEquals(new StorageScope.Instance(), instance.scope());
        assertEquals(StorageCategory.INSTANCE_MEDIA_FILES, instance.category());
    }

    @Test
    void storeAndRead() {
        byte[] data = "hello".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        var result = original(1, hash).orElseThrow();
        assertArrayEquals(data, result.data());
        assertEquals("image/png", result.contentType());
        assertTrue(Files.exists(storage.hashDir(1, hash).resolve("orig.png")));
    }

    @Test
    void storeWithoutContentType() {
        byte[] data = "hello-no-ct".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, null);

        assertEquals("application/octet-stream", original(1, hash).orElseThrow().contentType());
        assertTrue(Files.exists(storage.hashDir(1, hash).resolve("orig.bin")));
    }

    @Test
    void anInstanceFileIsKeptApartFromEveryStation() {
        byte[] data = "instance".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(null, hash, data, "application/pdf");

        assertArrayEquals(data, original(null, hash).orElseThrow().data());
        assertTrue(original(1, hash).isEmpty());
    }

    @Test
    void deleteRemovesEntireHashDirectory() {
        byte[] data = "swallow".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        storage.delete(1, hash);

        assertFalse(Files.exists(storage.hashDir(1, hash)));
        assertTrue(original(1, hash).isEmpty());
        storage.delete(1, "0000");
    }

    @Test
    void filesForDifferentStationsAreIsolated() {
        byte[] data = "same-bytes".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.store(2, hash, data, "image/png");

        storage.delete(1, hash);
        assertTrue(original(1, hash).isEmpty());
        assertTrue(original(2, hash).isPresent());
    }

    @Test
    void storeOverwritesPreviousOriginalExtension() {
        byte[] data = "first".getBytes();
        String hash = MediaStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.store(1, hash, data, "image/jpeg");

        Path dir = storage.hashDir(1, hash);
        assertFalse(Files.exists(dir.resolve("orig.png")), "Stale orig.png should be removed");
        assertTrue(Files.exists(dir.resolve("orig.jpg")));
    }

    private Optional<MediaContent> original(Integer stationId, String hash) {
        var at = storage.locate(stationId, hash);
        return images.read(ImageProfile.LIBRARY, at.scope(), at.category(), at.key(), 0);
    }
}
