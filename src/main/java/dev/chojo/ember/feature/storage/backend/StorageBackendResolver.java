/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Returns the {@link StorageBackend} that owns bytes for a {@code (scope, category)} pair.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@link StorageCategory#isLocalPinned()} → local backend, no override applies.</li>
 *   <li>{@link StorageScope.Station} with an override row in {@code station_storage_config} →
 *       that backend. The override is per-station, not per-category - it applies across every
 *       station-scoped movable category.</li>
 *   <li>A row in {@code cluster_station_storage} → the version of its cluster's storage the station's
 *       bytes were actually carried to. What the cluster decided is not read here at all.</li>
 *   <li>Instance default from {@code conf.yml}, built by {@link StorageBackendFactory}.</li>
 * </ol>
 *
 * <p>Backends are cached by the configuration they were built from: a station's own override row, or
 * the version of cluster storage it stands on. Every station on one version therefore shares one
 * backend and its one pool of sessions. Each station is mapped to its key in a cache of its own, so
 * the database is read once per station and not on every byte-level call.
 *
 * <p>A backend that leaves the cache, invalidated or evicted, is closed once the calls still using it
 * are done, at the latest after the pool's drain limit. On shutdown every cached backend, the instance
 * default and the shared protocol clients are closed.
 */
@Singleton
public class StorageBackendResolver {
    private static final long MAX_CACHED = 256;

    private final StorageBackendFactory factory;
    private final StationStorageConfigRepository overrideRepository;
    private final ClusterStationStorageRepository placementRepository;
    private final Cache<Integer, Optional<BackendKey>> stationKeys;
    private final Set<StorageBackend> handedOver =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Cache<BackendKey, StorageBackend> backends;

    @Inject
    public StorageBackendResolver(
            StorageBackendFactory factory,
            StationStorageConfigRepository overrideRepository,
            ClusterStationStorageRepository placementRepository) {
        this.placementRepository = placementRepository;
        this.factory = factory;
        this.overrideRepository = overrideRepository;
        this.stationKeys = Caffeine.newBuilder().maximumSize(MAX_CACHED).build();
        this.backends = backendCache();
    }

    /**
     * Convenience constructor for tests that already have a {@link LocalStorageBackend} in
     * hand and want the resolver to pin every category to it without going through the
     * factory or the override repository.
     */
    public StorageBackendResolver(LocalStorageBackend localBackend) {
        this.factory = new StorageBackendFactory(new Storage(), localBackend, null);
        this.overrideRepository = null;
        this.placementRepository = null;
        this.stationKeys = Caffeine.newBuilder().maximumSize(MAX_CACHED).build();
        this.backends = backendCache();
    }

    private Cache<BackendKey, StorageBackend> backendCache() {
        return Caffeine.newBuilder()
                .maximumSize(MAX_CACHED)
                .executor(Runnable::run)
                .<BackendKey, StorageBackend>removalListener((key, backend, cause) -> {
                    if (backend != null && !handedOver.remove(backend)) backend.close();
                })
                .build();
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
            Optional<StorageBackend> override = stationBackend(station.stationId());
            if (override.isPresent()) return override.get();
        }
        return factory.instanceDefault();
    }

    /**
     * Returns the configured instance-default backend, regardless of category.
     */
    public StorageBackend instanceDefault() {
        return factory.instanceDefault();
    }

    /**
     * Forgets where one station's bytes go. A backend of the station's own is closed with it; a
     * version of cluster storage stays, since other stations stand on it.
     */
    public void invalidateStation(int stationId) {
        var key = stationKeys.getIfPresent(stationId);
        stationKeys.invalidate(stationId);
        if (key != null) key.filter(BackendKey.Own.class::isInstance).ifPresent(backends::invalidate);
    }

    /**
     * Forgets where one station's bytes go, like {@link #invalidateStation}, but hands a backend of
     * the station's own to the caller instead of closing it. A move still has to delete the station's
     * bytes from where they were after it pointed the station elsewhere, and closes that backend itself
     * once it is done with it.
     *
     * @param stationId the station that moved
     * @return the station's own backend as it was cached, now the caller's to close
     */
    public Optional<StorageBackend> detachStation(int stationId) {
        var key = stationKeys.getIfPresent(stationId);
        stationKeys.invalidate(stationId);
        if (key == null || key.filter(BackendKey.Own.class::isInstance).isEmpty()) return Optional.empty();
        var backend = backends.getIfPresent(key.get());
        if (backend == null) return Optional.empty();
        handedOver.add(backend);
        backends.invalidate(key.get());
        return Optional.of(backend);
    }

    /**
     * Forgets where several stations' bytes go, which is what a change at their cluster needs, and
     * closes the backends they stood on: the change may have been to the credentials of that version.
     *
     * @param stationIds the stations whose resolved backend may have moved
     */
    public void invalidateStations(Iterable<Integer> stationIds) {
        for (int stationId : stationIds) {
            var key = stationKeys.getIfPresent(stationId);
            stationKeys.invalidate(stationId);
            if (key != null) key.ifPresent(backends::invalidate);
        }
    }

    /**
     * Flushes every mapping and closes every cached backend.
     */
    public void invalidateAll() {
        stationKeys.invalidateAll();
        backends.invalidateAll();
    }

    /**
     * Closes every cached backend, the instance default and the shared protocol clients. Called once by
     * the storage stage of the shutdown, after the last write; each backend waits for the calls still
     * using it, at most the pool's drain limit.
     */
    public void closeAll() {
        invalidateAll();
        factory.closeInstanceDefault();
    }

    /**
     * What a station's bytes go to: its own override, then the cluster storage they were carried to, then the
     * instance default.
     *
     * <p>Read from where the bytes are and never from what a cluster decided. A decision takes effect the
     * moment it is written and a copy does not, so resolving from policy points a station at storage its
     * files are not on, which is the whole reason placement exists.
     */
    private Optional<StorageBackend> stationBackend(int stationId) {
        Optional<BackendKey> key = stationKeys.get(stationId, this::keyOf);
        return key.map(found -> backends.get(found, unused -> factory.buildForStation(configOf(stationId))));
    }

    private Optional<BackendKey> keyOf(int stationId) {
        if (overrideRepository.findOne(stationId).isPresent()) return Optional.of(new BackendKey.Own(stationId));
        if (placementRepository == null) return Optional.empty();
        return placementRepository
                .findByStation(stationId)
                .map(placement -> new BackendKey.Cluster(placement.configId()));
    }

    private StationStorageBackendConfig configOf(int stationId) {
        return overrideRepository
                .findOne(stationId)
                .map(StationStorageConfigRepository.Row::config)
                .or(() -> placementRepository.findConfigForStation(stationId))
                .orElseThrow(() -> new StorageException("Station " + stationId + " stands on no storage any more"));
    }

    /** The configuration a backend was built from. */
    private sealed interface BackendKey {
        /** A station's own override row. */
        record Own(int stationId) implements BackendKey {}

        /** A version of a cluster's storage, which every station carried to it shares. */
        record Cluster(int configId) implements BackendKey {}
    }
}
