/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.content.repository.ContentContainerRepository;
import dev.chojo.ember.feature.media.repository.MediaFileRepository;
import dev.chojo.ember.feature.media.repository.MediaMetaRepository;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaReferenceRegistry;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.media.service.MediaVariantService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;

import java.util.Set;

/**
 * Builds a media library over the local storage backend for the tests that need one only to
 * satisfy a constructor.
 */
public final class MediaTestSupport {

    private MediaTestSupport() {}

    public static MediaLibraryService library(
            StationRepository stationRepository,
            ContentContainerRepository containers,
            MediaFileRepository fileRepository,
            MediaMetaRepository metaRepository,
            StorageUsageRepository usageRepository) {
        var backend = new LocalStorageBackend();
        var storageService = new StorageService(new StorageBackendResolver(backend), backend);
        var storageConfig = new Storage();
        var storage = new MediaStorageService(storageService, stationRepository, backend);
        return new MediaLibraryService(
                fileRepository,
                metaRepository,
                storage,
                new MediaVariantService(storage, storageConfig),
                new MediaReferenceRegistry(containers),
                new StorageQuotaService(usageRepository, storageConfig, new DomainEventBus(Set.of())));
    }
}
