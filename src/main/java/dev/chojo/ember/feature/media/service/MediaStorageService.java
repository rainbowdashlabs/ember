/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.media.image.MediaTypes;
import dev.chojo.ember.feature.media.image.VariantFile;
import dev.chojo.ember.feature.media.image.VariantLayout;
import dev.chojo.ember.feature.media.image.VariantSet;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * On-disk storage for the station media library (images, PDFs, downloads). Backed by the
 * unified {@link StorageService}; this class owns only the library's keying convention: the content
 * hash is the key, and the files of one hash are named in {@link VariantLayout#LIBRARY}.
 *
 * <p>Layout in the storage model:
 * {@code <scope>/<category>/<contentHash>/<variantFilename>} →
 * {@code station/<uid>/media/files/<contentHash>/orig.<ext>}.
 */
@Singleton
public class MediaStorageService {
    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private final StorageService storage;
    private final StationRepository stationRepository;
    private final LocalStorageBackend localBackend;

    @Inject
    public MediaStorageService(
            StorageService storage, StationRepository stationRepository, LocalStorageBackend localBackend) {
        this.storage = storage;
        this.stationRepository = stationRepository;
        this.localBackend = localBackend;
    }

    /**
     * Hex SHA-256 of the given bytes (lowercase).
     */
    public static String hash(byte[] data) {
        return Sha256.hex(data);
    }

    /**
     * Persists the original bytes for a (station, hash) pair. Idempotent - calling twice with
     * the same hash is a semantic no-op because the bytes are identical by definition.
     */
    public void store(Integer stationId, String contentHash, byte[] data, String contentType) throws IOException {
        StorageScope scope = scopeOf(stationId);
        StorageCategory category = categoryFor(stationId);
        for (String key : storage.listKeys(scope, category, contentHash)) {
            VariantFile file = VariantFile.of(key);
            if (VariantLayout.LIBRARY.isOriginal(file)) {
                storage.delete(scope, category, contentHash, new Variant(file.fileName()));
            }
        }
        String filename = VariantLayout.LIBRARY.originalName(MediaTypes.extensionFor(contentType));
        storage.store(scope, category, contentHash, new Variant(filename), data, contentType);
    }

    /**
     * Reads the original bytes + MIME type for a (station, hash). Returns
     * {@link Optional#empty()} when no original is present.
     */
    public Optional<FileData> read(Integer stationId, String contentHash) {
        return set(stationId, contentHash, VariantLayout.LIBRARY)
                .original()
                .flatMap(file -> readFile(stationId, contentHash, file));
    }

    /**
     * The files stored under one hash, read in a layout.
     *
     * @param stationId   the station, or {@code null} for the instance's own library
     * @param contentHash the hash the files are stored under
     * @param layout      the layout the names are read in
     * @return the set, empty when nothing is stored
     */
    public VariantSet set(Integer stationId, String contentHash, VariantLayout layout) {
        return VariantSet.of(layout, storage.listKeys(scopeOf(stationId), categoryFor(stationId), contentHash));
    }

    /**
     * Reads one stored file of a hash.
     *
     * @param stationId   the station, or {@code null} for the instance's own library
     * @param contentHash the hash the file is stored under
     * @param file        the file, as a {@link VariantSet} named it
     * @return the bytes and the type to serve them as, or empty when the file is gone
     */
    public Optional<FileData> readFile(Integer stationId, String contentHash, VariantFile file) {
        Optional<StoredStream> opt =
                storage.read(scopeOf(stationId), categoryFor(stationId), contentHash, new Variant(file.fileName()));
        if (opt.isEmpty()) return Optional.empty();
        try (StoredStream s = opt.get()) {
            byte[] data = s.body().readAllBytes();
            return Optional.of(new FileData(
                    data, MediaTypes.contentTypeOf(file, s.metadata().contentType())));
        } catch (IOException e) {
            log.error("Failed to read media file station={} hash={} file={}", stationId, contentHash, file, e);
            return Optional.empty();
        }
    }

    /**
     * Reads a single named variant from the (station, hash) directory.
     *
     * <p>{@code baseName} is the file name without extension (e.g. {@code "orig"},
     * {@code "w512"}). {@code extension} pins a specific format ({@code "webp"},
     * {@code "jpg"}, …) - pass {@code null} to accept any extension, preferring one that is not WebP,
     * which is the right choice when reading the original whose on-disk format is whatever the user
     * uploaded.
     */
    public Optional<FileData> readVariant(Integer stationId, String contentHash, String baseName, String extension) {
        List<VariantFile> named = storage.listKeys(scopeOf(stationId), categoryFor(stationId), contentHash).stream()
                .map(VariantFile::of)
                .filter(file -> file.base().equals(baseName))
                .filter(file -> extension == null
                        || extension.isEmpty()
                        || file.extension().equals(extension))
                .toList();
        return named.stream()
                .filter(file -> !file.extension().equals("webp"))
                .findFirst()
                .or(() -> named.stream().findFirst())
                .flatMap(file -> readFile(stationId, contentHash, file));
    }

    /**
     * Writes a single variant blob into the (station, hash) directory under
     * {@code <variantName>.<extension>}.
     */
    public void storeVariant(Integer stationId, String contentHash, String variantName, String extension, byte[] data) {
        storeFile(stationId, contentHash, variantName + "." + extension, data);
    }

    /**
     * Writes one file into the (station, hash) directory under a name a layout gave it.
     *
     * @param stationId   the station, or {@code null} for the instance's own library
     * @param contentHash the hash the file belongs to
     * @param fileName    the file name, extension included
     * @param data        the bytes
     */
    public void storeFile(Integer stationId, String contentHash, String fileName, byte[] data) {
        String contentType = MediaTypes.mimeTypeFor(VariantFile.of(fileName).extension());
        storage.store(
                scopeOf(stationId), categoryFor(stationId), contentHash, new Variant(fileName), data, contentType);
    }

    /**
     * Deletes everything under the (station, hash) directory - original and all variants.
     * Caller is responsible for ensuring no other DB rows reference the same hash within the
     * same station.
     */
    public void delete(Integer stationId, String contentHash) {
        storage.deletePrefix(scopeOf(stationId), categoryFor(stationId), contentHash);
    }

    /**
     * Visible for the variant service and tests: the absolute on-disk directory that holds
     * the original and every variant for a given (station, hash). Returns a path even when
     * the directory does not yet exist on disk.
     */
    public Path hashDir(Integer stationId, String contentHash) {
        String full = storage.fullKey(scopeOf(stationId), categoryFor(stationId), contentHash, Variant.ORIGINAL);
        return localBackend.resolve(full);
    }

    /**
     * The category a file's bytes live under. It follows the scope: the same folder name, declared
     * once for a station and once for the instance, because the storage layer checks that a
     * category is used at the scope it was declared for. Null means the instance, the same way it
     * does in the column the file is read from.
     */
    private StorageCategory categoryFor(Integer stationId) {
        return stationId == null ? StorageCategory.INSTANCE_MEDIA_FILES : StorageCategory.MEDIA_FILES;
    }

    private StorageScope scopeOf(Integer stationId) {
        if (stationId == null) return new StorageScope.Instance();
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }

    /**
     * The bytes + MIME type returned by {@link #read}. {@code contentType} is derived from
     * the variant's extension (WebP always reports {@code image/webp}; the original falls back
     * to the stored sidecar) so callers can set the {@code Content-Type} header verbatim.
     */
    public record FileData(byte[] data, String contentType) {}
}
