/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.LocalStorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
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

class KbFileStorageServiceTest {

    private static final int STATION_ID = 1;
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @TempDir
    Path tempDir;

    private Storage config;
    private KbFileStorageService storage;

    @BeforeEach
    void setup() {
        config = Mockito.mock(Storage.class);
        Mockito.when(config.compressTextFiles()).thenReturn(true);
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(STATION_ID)).thenReturn(STATION_UID);
        var backend = new LocalStorageBackend(tempDir);
        var resolver = new StorageBackendResolver(backend);
        var storageService = new StorageService(resolver, backend);
        var compression = new TextCompressionPolicy(config);
        storage = new KbFileStorageService(storageService, stationRepo, backend, compression);
    }

    private Path stationDir() {
        return tempDir.resolve("station").resolve(STATION_UID.toString()).resolve("kb-files");
    }

    @Test
    void binaryFileRoundTrips() throws IOException {
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        storage.store(STATION_ID, 1, data, "application/pdf");
        var read = storage.read(STATION_ID, 1).orElseThrow();
        assertArrayEquals(data, read.data());
        assertEquals("application/pdf", read.contentType());
        assertTrue(Files.exists(stationDir().resolve("1").resolve("content")));
        assertFalse(Files.exists(stationDir().resolve("1").resolve("content.gz")));
    }

    @Test
    void textFileIsGzippedOnDisk() throws IOException {
        byte[] data = "hello world ".repeat(500).getBytes();
        storage.store(STATION_ID, 2, data, "text/plain");
        assertFalse(Files.exists(stationDir().resolve("2").resolve("content")));
        assertTrue(Files.exists(stationDir().resolve("2").resolve("content.gz")));
        long gzSize = Files.size(stationDir().resolve("2").resolve("content.gz"));
        assertTrue(gzSize < data.length, "Gzipped text should be smaller than original");

        var read = storage.read(STATION_ID, 2).orElseThrow();
        assertArrayEquals(data, read.data());
        assertEquals("text/plain", read.contentType());
    }

    @Test
    void jsonAndXmlAreGzipped() {
        byte[] json = "{\"key\":\"value\"}".repeat(50).getBytes();
        storage.store(STATION_ID, 3, json, "application/json");
        assertTrue(Files.exists(stationDir().resolve("3").resolve("content.gz")));
        assertArrayEquals(json, storage.read(STATION_ID, 3).orElseThrow().data());

        byte[] xml = "<a><b>x</b></a>".repeat(50).getBytes();
        storage.store(STATION_ID, 4, xml, "application/xml");
        assertTrue(Files.exists(stationDir().resolve("4").resolve("content.gz")));
        assertArrayEquals(xml, storage.read(STATION_ID, 4).orElseThrow().data());
    }

    @Test
    void gzipDisabledByConfigStoresPlain() {
        Mockito.when(config.compressTextFiles()).thenReturn(false);
        byte[] data = "plain text".getBytes();
        storage.store(STATION_ID, 5, data, "text/plain");
        assertTrue(Files.exists(stationDir().resolve("5").resolve("content")));
        assertFalse(Files.exists(stationDir().resolve("5").resolve("content.gz")));
        assertArrayEquals(data, storage.read(STATION_ID, 5).orElseThrow().data());
    }

    @Test
    void storeRemovesStaleAlternativeOnReUpload() {
        Mockito.when(config.compressTextFiles()).thenReturn(false);
        storage.store(STATION_ID, 6, "plain".getBytes(), "text/plain");
        assertTrue(Files.exists(stationDir().resolve("6").resolve("content")));

        Mockito.when(config.compressTextFiles()).thenReturn(true);
        storage.store(STATION_ID, 6, "gzipped".getBytes(), "text/plain");
        assertTrue(Files.exists(stationDir().resolve("6").resolve("content.gz")));
        assertFalse(
                Files.exists(stationDir().resolve("6").resolve("content")),
                "Re-storing as gzipped must remove the stale plain copy");

        var read = storage.read(STATION_ID, 6).orElseThrow();
        assertArrayEquals("gzipped".getBytes(), read.data());
    }

    @Test
    void readMissingReturnsEmpty() {
        assertTrue(storage.read(STATION_ID, 999).isEmpty());
    }

    @Test
    void deleteRemovesEverything() {
        storage.store(STATION_ID, 7, "x".getBytes(), "text/plain");
        storage.delete(STATION_ID, 7);
        assertTrue(storage.read(STATION_ID, 7).isEmpty());
        assertFalse(Files.exists(stationDir().resolve("7")));
    }

    @Test
    void presentationPdfRoundTrips() {
        byte[] pdf = new byte[] {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46};
        storage.storePresentationPdf(STATION_ID, 8, pdf);
        var read = storage.readPresentationPdf(STATION_ID, 8).orElseThrow();
        assertArrayEquals(pdf, read.data());
        assertEquals("application/pdf", read.contentType());
    }
}
