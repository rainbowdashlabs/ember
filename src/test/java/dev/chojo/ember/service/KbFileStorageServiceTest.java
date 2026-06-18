/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KbFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private Storage config;
    private KbFileStorageService storage;

    @BeforeEach
    void setup() throws Exception {
        config = Mockito.mock(Storage.class);
        Mockito.when(config.compressTextFiles()).thenReturn(true);
        storage = new KbFileStorageService(config);
        Field baseDir = KbFileStorageService.class.getDeclaredField("baseDir");
        baseDir.setAccessible(true);
        baseDir.set(storage, tempDir);
    }

    @Test
    void binaryFileRoundTrips() throws IOException {
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        storage.store(1, data, "application/pdf");
        var read = storage.read(1).orElseThrow();
        assertArrayEquals(data, read.data());
        assertEquals("application/pdf", read.contentType());
        assertTrue(Files.exists(tempDir.resolve("1").resolve("content")));
        assertFalse(Files.exists(tempDir.resolve("1").resolve("content.gz")));
    }

    @Test
    void textFileIsGzippedOnDisk() throws IOException {
        byte[] data = "hello world ".repeat(500).getBytes();
        storage.store(2, data, "text/plain");
        assertFalse(Files.exists(tempDir.resolve("2").resolve("content")));
        assertTrue(Files.exists(tempDir.resolve("2").resolve("content.gz")));
        long gzSize = Files.size(tempDir.resolve("2").resolve("content.gz"));
        assertTrue(gzSize < data.length, "Gzipped text should be smaller than original");

        var read = storage.read(2).orElseThrow();
        assertArrayEquals(data, read.data());
        assertEquals("text/plain", read.contentType());
    }

    @Test
    void jsonAndXmlAreGzipped() throws IOException {
        byte[] json = "{\"key\":\"value\"}".repeat(50).getBytes();
        storage.store(3, json, "application/json");
        assertTrue(Files.exists(tempDir.resolve("3").resolve("content.gz")));
        assertArrayEquals(json, storage.read(3).orElseThrow().data());

        byte[] xml = "<a><b>x</b></a>".repeat(50).getBytes();
        storage.store(4, xml, "application/xml");
        assertTrue(Files.exists(tempDir.resolve("4").resolve("content.gz")));
        assertArrayEquals(xml, storage.read(4).orElseThrow().data());
    }

    @Test
    void gzipDisabledByConfigStoresPlain() throws IOException {
        Mockito.when(config.compressTextFiles()).thenReturn(false);
        byte[] data = "plain text".getBytes();
        storage.store(5, data, "text/plain");
        assertTrue(Files.exists(tempDir.resolve("5").resolve("content")));
        assertFalse(Files.exists(tempDir.resolve("5").resolve("content.gz")));
        assertArrayEquals(data, storage.read(5).orElseThrow().data());
    }

    @Test
    void storeRemovesStaleAlternativeOnReUpload() throws IOException {
        Mockito.when(config.compressTextFiles()).thenReturn(false);
        storage.store(6, "plain".getBytes(), "text/plain");
        assertTrue(Files.exists(tempDir.resolve("6").resolve("content")));

        Mockito.when(config.compressTextFiles()).thenReturn(true);
        storage.store(6, "gzipped".getBytes(), "text/plain");
        assertTrue(Files.exists(tempDir.resolve("6").resolve("content.gz")));
        assertFalse(
                Files.exists(tempDir.resolve("6").resolve("content")),
                "Re-storing as gzipped must remove the stale plain copy");

        var read = storage.read(6).orElseThrow();
        assertArrayEquals("gzipped".getBytes(), read.data());
    }

    @Test
    void readMissingReturnsEmpty() {
        assertTrue(storage.read(999).isEmpty());
    }

    @Test
    void deleteRemovesEverything() throws IOException {
        storage.store(7, "x".getBytes(), "text/plain");
        storage.delete(7);
        assertTrue(storage.read(7).isEmpty());
        assertFalse(Files.exists(tempDir.resolve("7")));
    }

    @Test
    void presentationPdfRoundTrips() throws IOException {
        byte[] pdf = new byte[] {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46};
        storage.storePresentationPdf(8, pdf);
        var read = storage.readPresentationPdf(8).orElseThrow();
        assertArrayEquals(pdf, read.data());
        assertEquals("application/pdf", read.contentType());
    }
}
