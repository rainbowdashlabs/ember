/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.LocalStorageBackend;
import dev.chojo.ember.feature.storage.backend.StoredStream;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * On-disk storage for page-attached files (images, PDFs, downloads). Backed by the unified
 * {@link StorageService}; this class owns only the page-files specific keying convention
 * (content-hash key, {@code orig.<ext>} / {@code w<width>.<ext>} variant slot).
 *
 * <p>Layout in the new storage model (concept §4.3):
 * {@code <scope>/<category>/<contentHash>/<variantFilename>} →
 * {@code station/<uid>/page-files/<contentHash>/orig.<ext>}.
 *
 * <p>A one-shot boot migration relocates uploads from the legacy
 * {@code data/page-files/<stationUid>/<contentHash>/...} layout into the new
 * {@code data/station/<stationUid>/page-files/<contentHash>/...} layout so existing files
 * remain reachable after the cut-over.
 */
@Singleton
public class PageFileStorageService {
    private static final Logger log = LoggerFactory.getLogger(PageFileStorageService.class);
    private static final String ORIG_PREFIX = "orig";

    private final StorageService storage;
    private final StationRepository stationRepository;
    private final LocalStorageBackend localBackend;

    @Inject
    public PageFileStorageService(
            StorageService storage, StationRepository stationRepository, LocalStorageBackend localBackend) {
        this.storage = storage;
        this.stationRepository = stationRepository;
        this.localBackend = localBackend;
        migrateLegacyRoot();
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

    /**
     * Persists the original bytes for a (station, hash) pair. Idempotent — calling twice with
     * the same hash is a semantic no-op because the bytes are identical by definition.
     */
    public void store(int stationId, String contentHash, byte[] data, String contentType) throws IOException {
        StorageScope.Station scope = stationScope(stationId);
        cleanupLegacyOriginals(scope, contentHash, ORIG_PREFIX);
        String filename = ORIG_PREFIX + "." + extensionFor(contentType);
        storage.store(scope, StorageCategory.PAGE_FILES, contentHash, new Variant(filename), data, contentType);
    }

    /**
     * Reads the original bytes + MIME type for a (station, hash). Returns
     * {@link Optional#empty()} when no original is present.
     */
    public Optional<FileData> read(int stationId, String contentHash) {
        return readVariant(stationId, contentHash, ORIG_PREFIX, null);
    }

    /**
     * Reads a single named variant from the (station, hash) directory.
     *
     * <p>{@code baseName} is the file name without extension (e.g. {@code "orig"},
     * {@code "w512"}). {@code extension} pins a specific format ({@code "webp"},
     * {@code "jpg"}, …) — pass {@code null} to accept any extension, which is the right
     * choice when reading the original whose on-disk format is whatever the user uploaded.
     */
    public Optional<FileData> readVariant(int stationId, String contentHash, String baseName, String extension) {
        StorageScope.Station scope = stationScope(stationId);
        String filename = pickVariantFilename(scope, contentHash, baseName, extension);
        if (filename == null) return Optional.empty();
        Optional<StoredStream> opt =
                storage.read(scope, StorageCategory.PAGE_FILES, contentHash, new Variant(filename));
        if (opt.isEmpty()) return Optional.empty();
        try (StoredStream s = opt.get()) {
            byte[] data = s.body().readAllBytes();
            String contentType = contentTypeFor(filename, s.metadata().contentType());
            return Optional.of(new FileData(data, contentType));
        } catch (IOException e) {
            log.error(
                    "Failed to read page image station={} hash={} base={} ext={}",
                    stationId,
                    contentHash,
                    baseName,
                    extension,
                    e);
            return Optional.empty();
        }
    }

    /**
     * Writes a single variant blob into the (station, hash) directory under
     * {@code <variantName>.<extension>}.
     */
    public void storeVariant(int stationId, String contentHash, String variantName, String extension, byte[] data) {
        String filename = variantName + "." + extension;
        String contentType = mimeForExtension(extension);
        storage.store(
                stationScope(stationId),
                StorageCategory.PAGE_FILES,
                contentHash,
                new Variant(filename),
                data,
                contentType);
    }

    /**
     * Deletes everything under the (station, hash) directory — original and all variants.
     * Caller is responsible for ensuring no other DB rows reference the same hash within the
     * same station.
     */
    public void delete(int stationId, String contentHash) {
        storage.deletePrefix(stationScope(stationId), StorageCategory.PAGE_FILES, contentHash);
    }

    /**
     * Visible for the variant service and tests: the absolute on-disk directory that holds
     * the original and every variant for a given (station, hash). Returns a path even when
     * the directory does not yet exist on disk.
     */
    public Path hashDir(int stationId, String contentHash) {
        String full =
                storage.fullKey(stationScope(stationId), StorageCategory.PAGE_FILES, contentHash, Variant.ORIGINAL);
        return localBackend.resolve(full);
    }

    private StorageScope.Station stationScope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }

    private String pickVariantFilename(
            StorageScope.Station scope, String contentHash, String baseName, String extension) {
        if (extension != null && !extension.isEmpty()) {
            String candidate = baseName + "." + extension;
            if (storage.exists(scope, StorageCategory.PAGE_FILES, contentHash, new Variant(candidate))) {
                return candidate;
            }
            return null;
        }
        List<String> keys = storage.listKeys(scope, StorageCategory.PAGE_FILES, contentHash);
        String fallback = null;
        for (String key : keys) {
            int slash = key.lastIndexOf('/');
            String filename = slash < 0 ? key : key.substring(slash + 1);
            int dot = filename.lastIndexOf('.');
            String base = dot < 0 ? filename : filename.substring(0, dot);
            if (!base.equals(baseName)) continue;
            if (!filename.endsWith(".webp")) return filename;
            if (fallback == null) fallback = filename;
        }
        return fallback;
    }

    private void cleanupLegacyOriginals(StorageScope.Station scope, String contentHash, String keepBase) {
        for (String key : storage.listKeys(scope, StorageCategory.PAGE_FILES, contentHash)) {
            int slash = key.lastIndexOf('/');
            String filename = slash < 0 ? key : key.substring(slash + 1);
            int dot = filename.lastIndexOf('.');
            String base = dot < 0 ? filename : filename.substring(0, dot);
            if (base.equals(keepBase)) {
                storage.delete(scope, StorageCategory.PAGE_FILES, contentHash, new Variant(filename));
            }
        }
    }

    private void migrateLegacyRoot() {
        Path legacyRoot = localBackend.root().resolve("page-files");
        if (!Files.isDirectory(legacyRoot)) return;
        log.info("Migrating legacy page-files layout from {}", legacyRoot);
        try (Stream<Path> stationDirs = Files.list(legacyRoot)) {
            for (Path stationDir : stationDirs.filter(Files::isDirectory).toList()) {
                String uid = stationDir.getFileName().toString();
                Path target =
                        localBackend.root().resolve("station").resolve(uid).resolve("page-files");
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) continue;
                Files.move(stationDir, target, StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.deleteIfExists(legacyRoot);
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            log.warn("Page-files legacy migration failed; legacy uploads may not be reachable", e);
        }
    }

    private String contentTypeFor(String filename, String stored) {
        if (filename.endsWith(".webp")) return "image/webp";
        if (stored != null && !stored.isBlank() && !"application/octet-stream".equals(stored)) {
            return stored;
        }
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return mimeForExtension(ext);
    }

    private static String mimeForExtension(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private static String extensionFor(String mimeType) {
        if (mimeType == null) return "bin";
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            case "application/pdf" -> "pdf";
            default -> "bin";
        };
    }

    /**
     * The bytes + MIME type returned by {@link #read}. {@code contentType} is derived from
     * the variant's extension (WebP always reports {@code image/webp}; the original falls back
     * to the stored sidecar) so callers can set the {@code Content-Type} header verbatim.
     */
    public record FileData(byte[] data, String contentType) {}
}
