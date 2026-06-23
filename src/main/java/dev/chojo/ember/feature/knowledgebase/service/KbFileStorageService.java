/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Per-station storage for knowledge-base binary files (uploaded documents, PDFs, presentation
 * artefacts). All I/O is delegated to {@link StorageService} under the {@code KB_FILES}
 * category. The producer side owns the keying convention: {@code <fileId>/content} for the
 * uncompressed original or {@code <fileId>/content.gz} for the gzipped variant, and
 * {@code <fileId>/presentation.pdf} for converted slide decks.
 *
 * <p>Gzip selection moves out of this service into {@link TextCompressionPolicy}; the storage
 * layer just receives whatever bytes it is given and the metadata sidecar reports the
 * {@code Content-Encoding}.
 */
@Singleton
public class KbFileStorageService {
    private static final Logger log = LoggerFactory.getLogger(KbFileStorageService.class);
    private static final String CONTENT = "content";
    private static final String CONTENT_GZ = "content.gz";
    private static final String PRESENTATION_PDF = "presentation.pdf";

    private final StorageService storage;
    private final StationRepository stationRepository;
    private final LocalStorageBackend localBackend;
    private final TextCompressionPolicy compression;

    @Inject
    public KbFileStorageService(
            StorageService storage,
            StationRepository stationRepository,
            LocalStorageBackend localBackend,
            TextCompressionPolicy compression) {
        this.storage = storage;
        this.stationRepository = stationRepository;
        this.localBackend = localBackend;
        this.compression = compression;
        migrateLegacyRoot();
    }

    /**
     * Persists the binary content for a (stationId, fileId) pair. Picks gzip transparently
     * when {@link TextCompressionPolicy#shouldGzip(String)} returns true.
     */
    public void store(int stationId, int fileId, byte[] data, String contentType) {
        StorageScope.Station scope = stationScope(stationId);
        if (compression.shouldGzip(contentType)) {
            byte[] gzipped = compression.gzip(data);
            storage.store(
                    scope, StorageCategory.KB_FILES, fileKey(fileId), new Variant(CONTENT_GZ), gzipped, contentType);
            storage.delete(scope, StorageCategory.KB_FILES, fileKey(fileId), new Variant(CONTENT));
        } else {
            storage.store(scope, StorageCategory.KB_FILES, fileKey(fileId), new Variant(CONTENT), data, contentType);
            storage.delete(scope, StorageCategory.KB_FILES, fileKey(fileId), new Variant(CONTENT_GZ));
        }
    }

    /**
     * Reads the binary content for a (stationId, fileId). Transparently decompresses gzip.
     */
    public Optional<FileData> read(int stationId, int fileId) {
        StorageScope.Station scope = stationScope(stationId);
        Optional<byte[]> gz =
                storage.readAllBytes(scope, StorageCategory.KB_FILES, fileKey(fileId), new Variant(CONTENT_GZ));
        if (gz.isPresent()) {
            byte[] decoded = compression.gunzip(gz.get());
            String contentType = readContentType(scope, fileId, CONTENT_GZ);
            return Optional.of(new FileData(decoded, contentType));
        }
        Optional<byte[]> plain =
                storage.readAllBytes(scope, StorageCategory.KB_FILES, fileKey(fileId), new Variant(CONTENT));
        if (plain.isPresent()) {
            String contentType = readContentType(scope, fileId, CONTENT);
            return Optional.of(new FileData(plain.get(), contentType));
        }
        return Optional.empty();
    }

    /**
     * Removes the file and any sidecar variants (raw / gzipped / converted PDF).
     */
    public void delete(int stationId, int fileId) {
        storage.deletePrefix(stationScope(stationId), StorageCategory.KB_FILES, fileKey(fileId));
    }

    /**
     * Persists the converted PDF artefact for a presentation file.
     */
    public void storePresentationPdf(int stationId, int fileId, byte[] pdfData) {
        storage.store(
                stationScope(stationId),
                StorageCategory.KB_FILES,
                fileKey(fileId),
                new Variant(PRESENTATION_PDF),
                pdfData,
                "application/pdf");
    }

    /**
     * Reads the converted PDF artefact for a presentation file.
     */
    public Optional<FileData> readPresentationPdf(int stationId, int fileId) {
        return storage.readAllBytes(
                        stationScope(stationId),
                        StorageCategory.KB_FILES,
                        fileKey(fileId),
                        new Variant(PRESENTATION_PDF))
                .map(bytes -> new FileData(bytes, "application/pdf"));
    }

    private String fileKey(int fileId) {
        return String.valueOf(fileId);
    }

    private String readContentType(StorageScope.Station scope, int fileId, String variant) {
        return storage.read(scope, StorageCategory.KB_FILES, fileKey(fileId), new Variant(variant))
                .map(stream -> {
                    try (stream) {
                        return stream.metadata().contentType();
                    } catch (IOException ignored) {
                        return "application/octet-stream";
                    }
                })
                .orElse("application/octet-stream");
    }

    private StorageScope.Station stationScope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }

    private void migrateLegacyRoot() {
        Path legacyRoot = localBackend.root().resolve("kb-files");
        if (!Files.isDirectory(legacyRoot)) return;
        log.info("Migrating legacy kb-files layout from {}", legacyRoot);
        try (Stream<Path> fileDirs = Files.list(legacyRoot)) {
            for (Path fileDir : fileDirs.filter(Files::isDirectory).toList()) {
                String name = fileDir.getFileName().toString();
                int fileId;
                try {
                    fileId = Integer.parseInt(name);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                Optional<Integer> stationId = lookupStationId(fileId);
                if (stationId.isEmpty()) {
                    log.warn("Could not resolve station for legacy kb file {}; leaving in place", fileId);
                    continue;
                }
                UUID uid = stationRepository.resolveUid(stationId.get());
                Path target = localBackend
                        .root()
                        .resolve("station")
                        .resolve(uid.toString())
                        .resolve("kb-files")
                        .resolve(name);
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) continue;
                Files.move(fileDir, target, StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.deleteIfExists(legacyRoot);
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            log.warn("KB-files legacy migration failed; some files may not be reachable", e);
        }
    }

    private Optional<Integer> lookupStationId(int fileId) {
        try {
            return query("SELECT station_id FROM kb_file WHERE id = :id;")
                    .single(call().bind("id", fileId))
                    .map(row -> row.getInt("station_id"))
                    .first();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Decoded payload + the MIME type the upload was stored under.
     */
    public record FileData(byte[] data, String contentType) {}
}
