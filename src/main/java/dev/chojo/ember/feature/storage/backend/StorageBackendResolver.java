/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Returns the {@link StorageBackend} that owns bytes for a {@code (scope, category)} pair.
 *
 * <p>Resolution order (concept §5.2):
 * <ol>
 *   <li>{@link StorageCategory#isLocalPinned()} → local backend, no overrides apply.</li>
 *   <li>Station override row in {@code station_storage_config} for this {@code (scope, category)}.</li>
 *   <li>Instance-level config in {@code conf.yml}.</li>
 *   <li>Default: {@link LocalStorageBackend} rooted at {@code data/}.</li>
 * </ol>
 *
 * <p>Steps 2–3 are wired in later phases; V1 only resolves to the local backend, which keeps
 * the contract stable for every producer that has already migrated to {@code StorageService}.
 */
@Singleton
public class StorageBackendResolver {

    private final LocalStorageBackend localBackend;

    @Inject
    public StorageBackendResolver(LocalStorageBackend localBackend) {
        this.localBackend = localBackend;
    }

    /**
     * Resolves the backend for a producer call. Throws {@link IllegalArgumentException} when
     * the supplied {@code scope} is incompatible with the category's expected scope kind, so
     * a mistyped call (e.g. an {@link StorageScope.Instance} on a station-scoped category)
     * fails at the call site instead of later inside the backend.
     */
    public StorageBackend forScope(StorageScope scope, StorageCategory category) {
        if (scope.kind() != category.scopeKind()) {
            throw new IllegalArgumentException(
                    "Category %s expects scope %s but got %s".formatted(category, category.scopeKind(), scope.kind()));
        }
        return localBackend;
    }
}
