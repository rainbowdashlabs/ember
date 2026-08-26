/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StoredStream;
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
 * Per-station storage for board-ticket attachments. The producer side owns the keying
 * convention ({@code <ticketId>/<random>_<originalName>}); every I/O call is delegated to
 * {@link StorageService} which picks the right backend for the {@code (Station,
 * BOARD_ATTACHMENTS)} pair.
 *
 * <p>A one-shot boot migration relocates uploads from the legacy
 * {@code data/attachments/board/<ticketId>/<file>} layout into
 * {@code data/station/<uid>/attachments/board/<ticketId>/<file>} so existing attachments
 * remain reachable after the cut-over. The migration resolves a ticket's station via
 * {@code board_ticket → board → station}, leaving any directory whose station cannot be
 * looked up untouched (the row is gone but the bytes are preserved).
 */
@Singleton
public class BoardAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(BoardAttachmentService.class);

    private final StorageService storage;
    private final StationRepository stationRepository;
    private final LocalStorageBackend localBackend;

    @Inject
    public BoardAttachmentService(
            StorageService storage, StationRepository stationRepository, LocalStorageBackend localBackend) {
        this.storage = storage;
        this.stationRepository = stationRepository;
        this.localBackend = localBackend;
        migrateLegacyRoot();
    }

    /**
     * Generates an upload filename for an original name. Random-prefixed so two clients
     * uploading {@code report.pdf} for the same ticket end up at distinct keys.
     */
    public String newFilename(String originalName) {
        String safeName = originalName == null ? "file" : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return UUID.randomUUID() + "_" + safeName;
    }

    /**
     * Stores the attachment bytes for {@code (stationId, ticketId, filename)}.
     */
    public void store(int stationId, int ticketId, String filename, byte[] data, String contentType) {
        storage.store(
                stationScope(stationId),
                StorageCategory.BOARD_ATTACHMENTS,
                ticketKey(ticketId, filename),
                data,
                contentType == null ? "application/octet-stream" : contentType);
    }

    /**
     * Streams the attachment back; the caller is responsible for closing the {@link StoredStream}.
     */
    public Optional<StoredStream> read(int stationId, int ticketId, String filename) {
        return storage.read(stationScope(stationId), StorageCategory.BOARD_ATTACHMENTS, ticketKey(ticketId, filename));
    }

    /**
     * Deletes a single attachment file. No-op when it does not exist.
     */
    public void delete(int stationId, int ticketId, String filename) {
        storage.delete(stationScope(stationId), StorageCategory.BOARD_ATTACHMENTS, ticketKey(ticketId, filename));
    }

    /**
     * Deletes every attachment that belongs to {@code (stationId, ticketId)}.
     */
    public void deleteAllForTicket(int stationId, int ticketId) {
        storage.deletePrefix(stationScope(stationId), StorageCategory.BOARD_ATTACHMENTS, String.valueOf(ticketId));
    }

    /**
     * Returns the local filesystem path that backs an attachment. The download route streams
     * directly from {@link #read(int, int, String)} in V1; this method survives only for the
     * handful of callers (download route, GDPR export) that still need a {@link Path}.
     */
    public Path resolvePath(int stationId, int ticketId, String filename) {
        String full = storage.fullKey(
                stationScope(stationId),
                StorageCategory.BOARD_ATTACHMENTS,
                ticketKey(ticketId, filename),
                Variant.ORIGINAL);
        return localBackend.resolve(full);
    }

    private String ticketKey(int ticketId, String filename) {
        return ticketId + "/" + filename;
    }

    private StorageScope.Station stationScope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }

    private void migrateLegacyRoot() {
        Path legacyRoot = localBackend.root().resolve("attachments").resolve("board");
        if (!Files.isDirectory(legacyRoot)) return;
        log.info("Migrating legacy board-attachment layout from {}", legacyRoot);
        try (Stream<Path> ticketDirs = Files.list(legacyRoot)) {
            for (Path ticketDir : ticketDirs.filter(Files::isDirectory).toList()) {
                String name = ticketDir.getFileName().toString();
                int ticketId;
                try {
                    ticketId = Integer.parseInt(name);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                Optional<Integer> stationId = lookupStationId(ticketId);
                if (stationId.isEmpty()) {
                    log.warn("Could not resolve station for legacy board ticket {}; leaving in place", ticketId);
                    continue;
                }
                UUID uid = stationRepository.resolveUid(stationId.get());
                Path target = localBackend
                        .root()
                        .resolve("station")
                        .resolve(uid.toString())
                        .resolve("attachments")
                        .resolve("board")
                        .resolve(name);
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) continue;
                Files.move(ticketDir, target, StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.deleteIfExists(legacyRoot);
                Files.deleteIfExists(legacyRoot.getParent());
            } catch (IOException e) {
                log.debug("Legacy board-attachment root {} stays behind, empty", legacyRoot, e);
            }
        } catch (IOException e) {
            log.warn("Board-attachment legacy migration failed; some uploads may not be reachable", e);
        }
    }

    private Optional<Integer> lookupStationId(int ticketId) {
        try {
            return query("""
                    SELECT b.station_id
                    FROM board_ticket bt
                    JOIN board b ON b.id = bt.board_id
                    WHERE bt.id = :id;
                    """)
                    .single(call().bind("id", ticketId))
                    .map(row -> row.getInt("station_id"))
                    .first();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
