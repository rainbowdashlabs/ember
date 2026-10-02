/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Deletes the versions of an association's storage that nobody needs any more, with their credentials.
 *
 * <p>A version is retired when the association points somewhere new or gives its storage up, and it stays
 * for as long as a station's files are on it. Whatever leaves a version behind asks here afterwards: a new
 * version, a dropped storage, and every station carried off one. The backend built for a deleted version is
 * closed with it.
 */
@Singleton
public class RetiredVersions {
    private final ClusterStorageConfigRepository repository;
    private final StorageBackendResolver resolver;

    @Inject
    public RetiredVersions(ClusterStorageConfigRepository repository, StorageBackendResolver resolver) {
        this.repository = repository;
        this.resolver = resolver;
    }

    /**
     * Deletes the retired versions of one association that nobody stands on.
     *
     * @param clusterId the association
     */
    public void sweep(int clusterId) {
        repository.deleteRetiredUnused(clusterId).forEach(resolver::invalidateClusterVersion);
    }
}
