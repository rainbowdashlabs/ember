/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.page.service.PageImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PageImageStorageServiceTest {
    private PageImageStorageService storage;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        storage = new PageImageStorageService();
        // Override baseDir to use temp directory
        Field baseDirField = PageImageStorageService.class.getDeclaredField("baseDir");
        baseDirField.setAccessible(true);
        baseDirField.set(storage, tempDir.resolve("page-images"));
    }

    @Test
    void storeAndRead() throws IOException {
        byte[] data = "hello".getBytes();
        storage.store(1, 10, data, "image/png");

        var result = storage.read(1, 10);
        assertTrue(result.isPresent());
        assertArrayEquals(data, result.orElseThrow().data());
        assertEquals("image/png", result.orElseThrow().contentType());
    }

    @Test
    void storeWithoutContentType() throws IOException {
        byte[] data = "hello".getBytes();
        storage.store(1, 11, data, null);

        var result = storage.read(1, 11);
        assertTrue(result.isPresent());
        assertEquals("application/octet-stream", result.orElseThrow().contentType());
    }

    @Test
    void readNonExistent() {
        assertTrue(storage.read(99, 99).isEmpty());
    }

    @Test
    void deleteFile() throws IOException {
        byte[] data = "hello".getBytes();
        storage.store(2, 20, data, "image/jpeg");

        storage.delete(2, 20);
        assertTrue(storage.read(2, 20).isEmpty());
    }

    @Test
    void deleteNonExistent() {
        // Should not throw
        storage.delete(99, 99);
    }

    @Test
    void deleteAllForPage() throws IOException {
        byte[] data = "img".getBytes();
        storage.store(3, 30, data, "image/png");
        storage.store(3, 31, data, "image/png");

        storage.deleteAllForPage(3);
        assertTrue(storage.read(3, 30).isEmpty());
        assertTrue(storage.read(3, 31).isEmpty());
    }

    @Test
    void deleteAllForNonExistentPage() {
        // Should not throw
        storage.deleteAllForPage(999);
    }
}
