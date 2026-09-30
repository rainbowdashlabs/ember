/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
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
 * Per-domain wrapper for lost-and-found item images. Keyed by item id under the owning
 * station's scope: {@code station/<stationUid>/images/lost-and-found/<itemId>/...}.
 */
@Singleton
public class LostAndFoundImageService {
    private static final Logger log = LoggerFactory.getLogger(LostAndFoundImageService.class);
    private static final ImageProfile PROFILE = ImageProfile.CONTENT;
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_LOST_AND_FOUND;

    private final ImageVariants images;
    private final StationRepository stationRepository;

    @Inject
    public LostAndFoundImageService(ImageVariants images, StationRepository stationRepository) {
        this.images = images;
        this.stationRepository = stationRepository;
    }

    /**
     * Persists the image for a (stationId, itemId) pair at all standard size variants.
     */
    public void store(int stationId, int itemId, byte[] data, String declaredMime, int maxBytes) throws IOException {
        images.store(PROFILE, scope(stationId), CATEGORY, key(itemId), data, maxBytes);
        log.info(
                "Stored lost-and-found image: station {}, item {} ({} bytes, mime={})",
                stationId,
                itemId,
                data.length,
                declaredMime);
    }

    /**
     * Reads the requested image size, falling back to the original when missing.
     */
    public Optional<MediaContent> read(int stationId, int itemId, int size) {
        return images.read(PROFILE, scope(stationId), CATEGORY, key(itemId), size);
    }

    /**
     * Whether an image exists for the given item.
     */
    public boolean exists(int stationId, int itemId) {
        return images.exists(PROFILE, scope(stationId), CATEGORY, key(itemId));
    }

    /**
     * Removes every variant for the given item's image.
     */
    public void delete(int stationId, int itemId) {
        images.delete(scope(stationId), CATEGORY, key(itemId));
        log.info("Deleted lost-and-found image: station {}, item {}", stationId, itemId);
    }

    private StorageScope.Station scope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }

    private String key(int itemId) {
        return String.valueOf(itemId);
    }
}
