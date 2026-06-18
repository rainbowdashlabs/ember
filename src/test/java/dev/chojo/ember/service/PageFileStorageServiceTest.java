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
    void deleteQuietlySwallowsIoException() throws IOException {
        byte[] data = "swallow".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        Path target =
                tempDir.resolve("page-files").resolve(stationOneUid.toString()).resolve(hash);
        Files.delete(target);
        Files.createDirectory(target);
        Files.write(target.resolve("sentinel"), new byte[] {1});

        storage.delete(1, hash);

        Files.delete(target.resolve("sentinel"));
        Files.delete(target);
    }

    @Test
    void readReturnsEmptyOnIoFailure() throws IOException {
        byte[] data = "ioerr".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");

        Path target =
                tempDir.resolve("page-files").resolve(stationOneUid.toString()).resolve(hash);
        Files.delete(target);
        Files.createDirectory(target);

        assertTrue(storage.read(1, hash).isEmpty());

        Files.delete(target);
    }

    @Test
    void filesForDifferentStationsAreIsolated() throws IOException {
        byte[] data = "same-bytes".getBytes();
        String hash = PageFileStorageService.hash(data);
        storage.store(1, hash, data, "image/png");
        storage.store(2, hash, data, "image/png");

        // Deleting in station 1 must not affect station 2's copy.
        storage.delete(1, hash);
        assertTrue(storage.read(1, hash).isEmpty());
        assertTrue(storage.read(2, hash).isPresent());
    }
}
