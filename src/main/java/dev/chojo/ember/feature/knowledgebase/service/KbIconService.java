/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-domain wrapper for knowledge-base folder icons. Keyed by
 * {@code folder-<folderId>} under the owning station's scope:
 * {@code station/<stationUid>/images/kb-icons/folder-<folderId>/...}.
 */
@Singleton
public class KbIconService {
    private static final Logger log = LoggerFactory.getLogger(KbIconService.class);
    private final ImageVariantService variants;
    private final StationRepository stationRepository;

    @Inject
    public KbIconService(ImageVariantService variants, StationRepository stationRepository) {
        this.variants = variants;
        this.stationRepository = stationRepository;
    }

    /**
     * Persists the icon for a folder at all standard size variants.
     */
    public void store(int stationId, int folderId, byte[] data, String declaredMime, int maxBytes) throws IOException {
        variants.store(scope(stationId), StorageCategory.IMAGE_KB_ICON, key(folderId), data, declaredMime, maxBytes);
        log.info("Stored KB folder icon for folder {} (station {}, {} bytes)", folderId, stationId, data.length);
    }

    /**
     * Reads the requested icon size, falling back to the original when missing.
     */
    public Optional<ImageVariantService.ImageData> read(int stationId, int folderId, int size) {
        return variants.read(scope(stationId), StorageCategory.IMAGE_KB_ICON, key(folderId), size);
    }

    /**
     * Whether an icon exists for the given folder.
     */
    public boolean exists(int stationId, int folderId) {
        return variants.exists(scope(stationId), StorageCategory.IMAGE_KB_ICON, key(folderId));
    }

    /**
     * Removes every variant for the given folder's icon.
     */
    public void delete(int stationId, int folderId) {
        variants.delete(scope(stationId), StorageCategory.IMAGE_KB_ICON, key(folderId));
        log.info("Deleted KB folder icon for folder {} (station {})", folderId, stationId);
    }

    /**
     * The key string used for a folder's icon. Exposed so callers can persist the same name in the folder row.
     */
    public String key(int folderId) {
        return "folder-" + folderId;
    }

    private StorageScope.Station scope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }
}
