/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

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

class BoardAttachmentServiceTest {

    private static final int STATION_ID = 1;
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @TempDir
    Path tempDir;

    private BoardAttachmentService service;

    @BeforeEach
    void setup() {
        var stationRepo = Mockito.mock(StationRepository.class);
        Mockito.when(stationRepo.resolveUid(STATION_ID)).thenReturn(STATION_UID);
        var backend = new LocalStorageBackend(tempDir);
        var resolver = new StorageBackendResolver(backend);
        var storage = new StorageService(resolver, backend);
        service = new BoardAttachmentService(storage, stationRepo, backend);
    }

    @Test
    void newFilenamePrefixesUuid() {
        String f = service.newFilename("report.pdf");
        assertTrue(f.endsWith("_report.pdf"));
        UUID.fromString(f.substring(0, f.indexOf('_')));
    }

    @Test
    void newFilenameAcceptsNullName() {
        String f = service.newFilename(null);
        assertTrue(f.endsWith("_file"));
    }

    @Test
    void storeReadDelete() throws IOException {
        byte[] data = "ticket-bytes".getBytes();
        String filename = service.newFilename("a.txt");
        service.store(STATION_ID, 7, filename, data, "text/plain");

        var read = service.read(STATION_ID, 7, filename).orElseThrow();
        try (read) {
            assertArrayEquals(data, read.body().readAllBytes());
        }

        service.delete(STATION_ID, 7, filename);
        assertTrue(service.read(STATION_ID, 7, filename).isEmpty());
    }

    @Test
    void resolvePathPointsAtStationScopedFile() {
        String filename = service.newFilename("path.bin");
        service.store(STATION_ID, 9, filename, new byte[] {1, 2}, "application/octet-stream");
        Path p = service.resolvePath(STATION_ID, 9, filename);
        assertTrue(Files.isRegularFile(p));
        assertTrue(p.toString().contains(STATION_UID.toString()));
        assertTrue(p.toString().endsWith(filename));
    }

    @Test
    void deleteAllForTicketRemovesEveryFile() throws IOException {
        String f1 = service.newFilename("a.txt");
        String f2 = service.newFilename("b.txt");
        service.store(STATION_ID, 12, f1, new byte[] {1}, "text/plain");
        service.store(STATION_ID, 12, f2, new byte[] {2}, "text/plain");

        service.deleteAllForTicket(STATION_ID, 12);
        assertTrue(service.read(STATION_ID, 12, f1).isEmpty());
        assertTrue(service.read(STATION_ID, 12, f2).isEmpty());
    }

    @Test
    void storeWithNullContentTypeFallsBackToOctetStream() throws IOException {
        String f = service.newFilename("x.bin");
        service.store(STATION_ID, 3, f, new byte[] {1}, null);
        try (var stream = service.read(STATION_ID, 3, f).orElseThrow()) {
            assertEquals("application/octet-stream", stream.metadata().contentType());
        }
    }
}
