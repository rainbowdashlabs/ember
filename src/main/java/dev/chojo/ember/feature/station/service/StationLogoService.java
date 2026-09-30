/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageFormat;
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
 * Stores and serves a station's logo through the storage backend as a multi-size variant set,
 * exactly like avatars and other images. Replaces the historical single-blob-in-the-database
 * storage, which never produced size variants.
 *
 * <p>Logos predating the backend migration still live in the {@code station.logo} column. Those
 * are migrated lazily: the first read of a raster logo copies it into the backend (generating the
 * variant set) and drops the database blob. A legacy SVG logo cannot go through the raster variant
 * pipeline, so it is served as-is from the database until the station uploads a raster replacement.
 */
@Singleton
public class StationLogoService {
    private static final Logger log = LoggerFactory.getLogger(StationLogoService.class);
    private static final int MAX_LOGO_SIZE = 2 * 1024 * 1024;
    private static final String KEY = "logo";
    private static final ImageProfile PROFILE = ImageProfile.ICON_SET;
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_STATION_LOGO;

    private final ImageVariants images;
    private final StationRepository stationRepository;

    @Inject
    public StationLogoService(ImageVariants images, StationRepository stationRepository) {
        this.images = images;
        this.stationRepository = stationRepository;
    }

    /**
     * Persists a new raster logo as the full variant set in the storage backend and drops any
     * legacy database blob. Rejects non-raster images (the variant pipeline works on pixels,
     * which is why SVG is no longer accepted).
     */
    public void store(int stationId, byte[] data, String declaredMime) throws IOException {
        images.store(PROFILE, scope(stationId), CATEGORY, KEY, data, MAX_LOGO_SIZE);
        stationRepository.deleteLogo(stationId);
        log.info("Station logo stored station={} mime={}", stationId, declaredMime);
    }

    /**
     * Reads the best-fit logo variant for the requested size, migrating a legacy database blob on
     * first access. Returns empty when the station has no logo.
     */
    public Optional<MediaContent> read(int stationId, int size) {
        var scope = scope(stationId);
        if (images.exists(PROFILE, scope, CATEGORY, KEY)) {
            return images.read(PROFILE, scope, CATEGORY, KEY, size);
        }
        return migrateOrServeLegacy(stationId, size);
    }

    /**
     * Reads the full-resolution logo, for consumers that embed it (PDF exports, e-mail).
     */
    public Optional<MediaContent> original(int stationId) {
        return read(stationId, 0);
    }

    /**
     * Whether the station has a logo, in the backend or as a not-yet-migrated database blob.
     */
    public boolean exists(int stationId) {
        return images.exists(PROFILE, scope(stationId), CATEGORY, KEY)
                || stationRepository.findLogo(stationId).isPresent();
    }

    /**
     * Removes the logo from both the backend and any legacy database blob.
     */
    public void delete(int stationId) {
        images.delete(scope(stationId), CATEGORY, KEY);
        stationRepository.deleteLogo(stationId);
        log.info("Station {} dropped its logo", stationId);
    }

    private Optional<MediaContent> migrateOrServeLegacy(int stationId, int size) {
        var legacy = stationRepository.findLogo(stationId);
        if (legacy.isEmpty()) {
            return Optional.empty();
        }
        var blob = legacy.get();
        if (ImageFormat.sniff(blob.data()).isPresent()) {
            try {
                var scope = scope(stationId);
                images.store(PROFILE, scope, CATEGORY, KEY, blob.data(), 0);
                stationRepository.deleteLogo(stationId);
                log.info("Migrated legacy station logo to storage backend station={}", stationId);
                return images.read(PROFILE, scope, CATEGORY, KEY, size);
            } catch (IOException e) {
                log.warn("Failed to migrate legacy station logo station={}", stationId, e);
            }
        }
        return Optional.of(new MediaContent(blob.data(), blob.contentType()));
    }

    private StorageScope.Station scope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        if (uid == null) {
            throw new IllegalArgumentException("Unknown station " + stationId);
        }
        return new StorageScope.Station(stationId, uid);
    }
}
