/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.concurrent.Callable;

/**
 * Whether a storage backend answers, asked of one built for the question and closed again after it.
 *
 * <p>A backend that cannot even be built answers as one that is not healthy, with the reason it could not
 * be built, so every caller reads a failure the same way whichever step it came from.
 */
@Singleton
public class StorageProbeService {
    private final StorageBackendFactory factory;

    @Inject
    public StorageProbeService(StorageBackendFactory factory) {
        this.factory = factory;
    }

    /**
     * Probes the backend a station, or an association, would stand on.
     *
     * @param config the stored or not yet stored configuration
     * @return whether it answered
     */
    public ProbeResult probe(StationStorageBackendConfig config) {
        return probeBuilt(() -> factory.buildForStation(config));
    }

    /**
     * Probes the backend the instance would stand on with these settings.
     *
     * @param settings the settings, which need not be the saved ones
     * @return whether it answered
     */
    public ProbeResult probe(StorageBackendSettings settings) {
        return probeBuilt(() -> factory.buildForInstance(settings));
    }

    /**
     * What a probe said, in the shape it is answered in.
     *
     * @param status the probe's own answer
     * @return the answer
     */
    public static ProbeResult resultOf(HealthStatus status) {
        return new ProbeResult(
                status.healthy(),
                status.error().orElse(null),
                status.checkedAt().toString());
    }

    private static ProbeResult probeBuilt(Callable<StorageBackend> build) {
        try (StorageBackend backend = build.call()) {
            return resultOf(backend.probe());
        } catch (Exception e) {
            return new ProbeResult(false, e.getMessage(), Instant.now().toString());
        }
    }
}
