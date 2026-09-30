/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.media.image.MediaTypes;
import dev.chojo.ember.feature.media.image.VariantFile;
import dev.chojo.ember.feature.media.image.VariantLayout;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The media library's keying: a file is stored under its content hash, in the library of its station
 * or in the instance's own. Everything about the files of one hash beyond the original, their sizes
 * and the drawn page of a document, belongs to {@link ImageVariants}.
 *
 * <p>Layout in the storage model:
 * {@code <scope>/<category>/<contentHash>/<variantFilename>} →
 * {@code station/<uid>/media/files/<contentHash>/orig.<ext>}.
 */
@Singleton
public class MediaStorageService {
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
     * Where the files of one hash are stored.
     *
     * @param stationId   the station, or {@code null} for the instance's own library
     * @param contentHash the hash
     * @return the scope, category and key the files live under
     */
    public Location locate(Integer stationId, String contentHash) {
        if (stationId == null) {
            return new Location(new StorageScope.Instance(), StorageCategory.INSTANCE_MEDIA_FILES, contentHash);
        }
        UUID uid = stationRepository.resolveUid(stationId);
        return new Location(new StorageScope.Station(stationId, uid), StorageCategory.MEDIA_FILES, contentHash);
    }

    /**
     * Persists the original bytes for a (station, hash) pair, replacing an original stored under
     * another extension. Idempotent: the bytes of one hash are identical by definition.
     */
    public void store(Integer stationId, String contentHash, byte[] data, String contentType) {
        Location at = locate(stationId, contentHash);
        for (String key : storage.listKeys(at.scope(), at.category(), contentHash)) {
            VariantFile file = VariantFile.of(key);
            if (VariantLayout.LIBRARY.isOriginal(file)) {
                storage.delete(at.scope(), at.category(), contentHash, new Variant(file.fileName()));
            }
        }
        String filename = VariantLayout.LIBRARY.originalName(MediaTypes.extensionFor(contentType));
        storage.store(at.scope(), at.category(), contentHash, new Variant(filename), data, contentType);
    }

    /**
     * Deletes everything under the (station, hash) directory - original and all variants.
     * Caller is responsible for ensuring no other DB rows reference the same hash within the
     * same station.
     */
    public void delete(Integer stationId, String contentHash) {
        Location at = locate(stationId, contentHash);
        storage.deletePrefix(at.scope(), at.category(), contentHash);
    }

    /**
     * Visible for tests: the absolute on-disk directory that holds the original and every variant
     * for a given (station, hash). Returns a path even when the directory does not yet exist on disk.
     */
    public Path hashDir(Integer stationId, String contentHash) {
        Location at = locate(stationId, contentHash);
        return localBackend.resolve(storage.fullKey(at.scope(), at.category(), contentHash, Variant.ORIGINAL));
    }

    /**
     * Where the files of one hash live. The category follows the scope: the same folder name, declared
     * once for a station and once for the instance, because the storage layer checks that a category
     * is used at the scope it was declared for.
     *
     * @param scope    the station's scope, or the instance's
     * @param category the library category of that scope
     * @param key      the content hash
     */
    public record Location(StorageScope scope, StorageCategory category, String key) {}
}
