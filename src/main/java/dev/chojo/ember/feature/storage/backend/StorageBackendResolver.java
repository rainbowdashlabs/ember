/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Returns the {@link StorageBackend} that owns bytes for a {@code (scope, category)} pair.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@link StorageCategory#isLocalPinned()} → local backend, no overrides apply.</li>
 *   <li>Station override row in {@code station_storage_config} (wired in a later phase).</li>
 *   <li>Instance default from {@code conf.yml}, built by {@link StorageBackendFactory}.</li>
 * </ol>
 */
@Singleton
public class StorageBackendResolver {

    private final StorageBackendFactory factory;

    @Inject
    public StorageBackendResolver(StorageBackendFactory factory) {
        this.factory = factory;
    }

    /**
     * Convenience constructor for tests that already have a {@link LocalStorageBackend} in
     * hand and want the resolver to pin every category to it without going through the
     * factory.
     */
    public StorageBackendResolver(LocalStorageBackend localBackend) {
        this.factory = new StorageBackendFactory(new dev.chojo.ember.conf.file.elements.Storage(), localBackend);
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
        if (category.isLocalPinned()) {
            return factory.localBackend();
        }
        return factory.instanceDefault();
    }

    /** Returns the configured instance-default backend, regardless of category. */
    public StorageBackend instanceDefault() {
        return factory.instanceDefault();
    }
}
