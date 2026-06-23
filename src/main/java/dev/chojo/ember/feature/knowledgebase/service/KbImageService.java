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

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-domain wrapper for knowledge-base inline images (concept §11.3). Producer-supplied
 * keys (e.g. {@code file-<fileId>-<timestamp>}) under the owning station's scope:
 * {@code station/<stationUid>/images/kb-images/<imageId>/...}.
 */
@Singleton
public class KbImageService {
    private final ImageVariantService variants;
    private final StationRepository stationRepository;

    @Inject
    public KbImageService(ImageVariantService variants, StationRepository stationRepository) {
        this.variants = variants;
        this.stationRepository = stationRepository;
    }

    /**
     * Persists a KB inline image at all standard size variants.
     */
    public void store(int stationId, String imageId, byte[] data, String declaredMime, int maxBytes)
            throws IOException {
        variants.store(scope(stationId), StorageCategory.IMAGE_KB_IMAGE, imageId, data, declaredMime, maxBytes);
    }

    /**
     * Reads the requested image size, falling back to the original when missing.
     */
    public Optional<ImageVariantService.ImageData> read(int stationId, String imageId, int size) {
        return variants.read(scope(stationId), StorageCategory.IMAGE_KB_IMAGE, imageId, size);
    }

    /**
     * Whether an image exists for the given (station, imageId).
     */
    public boolean exists(int stationId, String imageId) {
        return variants.exists(scope(stationId), StorageCategory.IMAGE_KB_IMAGE, imageId);
    }

    /**
     * Removes every variant for the given image.
     */
    public void delete(int stationId, String imageId) {
        variants.delete(scope(stationId), StorageCategory.IMAGE_KB_IMAGE, imageId);
    }

    private StorageScope.Station scope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }
}
