/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Returns the {@link StorageBackend} that owns bytes for a {@code (scope, category)} pair.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@link StorageCategory#isLocalPinned()} → local backend, no overrides apply.</li>
 *   <li>Station override row in {@code station_storage_config} for this {@code (scope, category)}.</li>
 *   <li>Instance default from {@code conf.yml}, built by {@link StorageBackendFactory}.</li>
 * </ol>
 *
 * <p>Station overrides are cached per {@code (stationId, category)} so the resolver does not
 * hit the database on every byte-level call; {@link #invalidateStation(int, StorageCategory)}
 * drops a single entry, {@link #invalidateAll()} flushes the cache after schema-wide changes.
 */
@Singleton
public class StorageBackendResolver {
    private static final long MAX_CACHED_OVERRIDES = 256;

    private final StorageBackendFactory factory;
    private final StationStorageConfigRepository overrideRepository;
    private final Cache<OverrideKey, StorageBackend> overrideCache;

    @Inject
    public StorageBackendResolver(StorageBackendFactory factory, StationStorageConfigRepository overrideRepository) {
        this.factory = factory;
        this.overrideRepository = overrideRepository;
        this.overrideCache =
                Caffeine.newBuilder().maximumSize(MAX_CACHED_OVERRIDES).build();
    }

    /**
     * Convenience constructor for tests that already have a {@link LocalStorageBackend} in
     * hand and want the resolver to pin every category to it without going through the
     * factory or the override repository.
     */
    public StorageBackendResolver(LocalStorageBackend localBackend) {
        this.factory = new StorageBackendFactory(new dev.chojo.ember.conf.file.elements.Storage(), localBackend, null);
        this.overrideRepository = null;
        this.overrideCache =
                Caffeine.newBuilder().maximumSize(MAX_CACHED_OVERRIDES).build();
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
        if (scope instanceof StorageScope.Station station && overrideRepository != null) {
            StorageBackend override = stationOverride(station.stationId(), category);
            if (override != null) return override;
        }
        return factory.instanceDefault();
    }

    /** Returns the configured instance-default backend, regardless of category. */
    public StorageBackend instanceDefault() {
        return factory.instanceDefault();
    }

    /** Drops the cached override for one {@code (station, category)} pair. */
    public void invalidateStation(int stationId, StorageCategory category) {
        overrideCache.invalidate(new OverrideKey(stationId, category));
    }

    /** Flushes the entire station-override cache. */
    public void invalidateAll() {
        overrideCache.invalidateAll();
    }

    private StorageBackend stationOverride(int stationId, StorageCategory category) {
        return overrideCache.get(new OverrideKey(stationId, category), key -> overrideRepository
                .findOne(key.stationId(), key.category())
                .map(row -> factory.buildForStation(row.config()))
                .orElse(null));
    }

    private record OverrideKey(int stationId, StorageCategory category) {}
}
