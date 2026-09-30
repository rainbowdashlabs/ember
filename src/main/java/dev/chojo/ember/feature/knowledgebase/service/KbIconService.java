/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

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
 * Per-domain wrapper for knowledge-base folder icons. Keyed by
 * {@code folder-<folderId>} under the owning station's scope:
 * {@code station/<stationUid>/images/kb-icons/folder-<folderId>/...}.
 */
@Singleton
public class KbIconService {
    private static final Logger log = LoggerFactory.getLogger(KbIconService.class);
    private static final ImageProfile PROFILE = ImageProfile.ICON_SET;
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_KB_ICON;

    private final ImageVariants images;
    private final StationRepository stationRepository;

    @Inject
    public KbIconService(ImageVariants images, StationRepository stationRepository) {
        this.images = images;
        this.stationRepository = stationRepository;
    }

    /**
     * Persists the icon for a folder at all standard size variants.
     */
    public void store(int stationId, int folderId, byte[] data, String declaredMime, int maxBytes) throws IOException {
        images.store(PROFILE, scope(stationId), CATEGORY, key(folderId), data, maxBytes);
        log.info(
                "Stored KB folder icon for folder {} (station {}, {} bytes, mime={})",
                folderId,
                stationId,
                data.length,
                declaredMime);
    }

    /**
     * Reads the requested icon size, falling back to the original when missing.
     */
    public Optional<MediaContent> read(int stationId, int folderId, int size) {
        return images.read(PROFILE, scope(stationId), CATEGORY, key(folderId), size);
    }

    /**
     * Whether an icon exists for the given folder.
     */
    public boolean exists(int stationId, int folderId) {
        return images.exists(PROFILE, scope(stationId), CATEGORY, key(folderId));
    }

    /**
     * Removes every variant for the given folder's icon.
     */
    public void delete(int stationId, int folderId) {
        images.delete(scope(stationId), CATEGORY, key(folderId));
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
