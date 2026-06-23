/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

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
 * Per-domain wrapper for lost-and-found item images. Keyed by item id under the owning
 * station's scope: {@code station/<stationUid>/images/lost-and-found/<itemId>/...}.
 */
@Singleton
public class LostAndFoundImageService {
    private final ImageVariantService variants;
    private final StationRepository stationRepository;

    @Inject
    public LostAndFoundImageService(ImageVariantService variants, StationRepository stationRepository) {
        this.variants = variants;
        this.stationRepository = stationRepository;
    }

    /**
     * Persists the image for a (stationId, itemId) pair at all standard size variants.
     */
    public void store(int stationId, int itemId, byte[] data, String declaredMime, int maxBytes) throws IOException {
        variants.store(
                scope(stationId), StorageCategory.IMAGE_LOST_AND_FOUND, key(itemId), data, declaredMime, maxBytes);
    }

    /**
     * Reads the requested image size, falling back to the original when missing.
     */
    public Optional<ImageVariantService.ImageData> read(int stationId, int itemId, int size) {
        return variants.read(scope(stationId), StorageCategory.IMAGE_LOST_AND_FOUND, key(itemId), size);
    }

    /**
     * Whether an image exists for the given item.
     */
    public boolean exists(int stationId, int itemId) {
        return variants.exists(scope(stationId), StorageCategory.IMAGE_LOST_AND_FOUND, key(itemId));
    }

    /**
     * Removes every variant for the given item's image.
     */
    public void delete(int stationId, int itemId) {
        variants.delete(scope(stationId), StorageCategory.IMAGE_LOST_AND_FOUND, key(itemId));
    }

    private StorageScope.Station scope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }

    private String key(int itemId) {
        return String.valueOf(itemId);
    }
}
