/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * On-disk storage for files uploaded against pages (images today, PDFs/audio/downloads in the
 * future), partitioned per station and keyed by the file's SHA-256 hash.
 *
 * <p>Layout: {@code data/page-files/<stationUuid>/<contentHash>} for the bytes and a sibling
 * {@code .content-type} file. The station UUID prefix isolates files between stations (so we
 * never share bytes across stations) while the hash key inside enables dedup within a station —
 * uploading the same file twice for the same station reuses the existing on-disk copy.
 */
@Singleton
public class PageFileStorageService {
    private static final Logger log = LoggerFactory.getLogger(PageFileStorageService.class);
    private final Path baseDir = Path.of("data", "page-files");
    private final StationRepository stationRepository;

    @Inject
    public PageFileStorageService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    /** Hex SHA-256 of the given bytes (lowercase). */
    public static String hash(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public void store(int stationId, String contentHash, byte[] data, String contentType) throws IOException {
        Path file = pathFor(stationId, contentHash);
        Files.createDirectories(file.getParent());
        // If another upload for this station-hash combo already exists, the bytes are identical by
        // definition — overwriting is a no-op semantically.
        Files.write(file, data);
        if (contentType != null) {
            Files.writeString(file.resolveSibling(contentHash + ".content-type"), contentType);
        }
    }

    public Optional<FileData> read(int stationId, String contentHash) {
        Path file = pathFor(stationId, contentHash);
        if (!Files.exists(file)) return Optional.empty();
        try {
            byte[] data = Files.readAllBytes(file);
            Path ctFile = file.resolveSibling(contentHash + ".content-type");
            String contentType = Files.exists(ctFile) ? Files.readString(ctFile).trim() : "application/octet-stream";
            return Optional.of(new FileData(data, contentType));
        } catch (IOException e) {
            log.error("Failed to read page image station={} hash={}", stationId, contentHash, e);
            return Optional.empty();
        }
    }

    /**
     * Deletes the on-disk file for a (station, hash) pair. Caller is responsible for ensuring no
     * other DB rows reference the same hash within the same station before calling this — the
     * dedup index in {@code page_image} guarantees that at most one row per (station, hash)
     * exists at any time, so deletion is safe whenever the corresponding row is removed.
     */
    public void delete(int stationId, String contentHash) {
        Path file = pathFor(stationId, contentHash);
        deleteQuietly(file);
        deleteQuietly(file.resolveSibling(contentHash + ".content-type"));
    }

    private Path pathFor(int stationId, String contentHash) {
        UUID uid = stationRepository.resolveUid(stationId);
        return baseDir.resolve(uid.toString()).resolve(contentHash);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
        }
    }

    public record FileData(byte[] data, String contentType) {}
}
