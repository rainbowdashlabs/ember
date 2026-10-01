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
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * The {@link StorageBackend} that holds the bytes of a scope and category: the local backend for a
 * local-pinned category, else a station's own override, else the cluster storage the station's bytes
 * were carried to, else the instance default.
 *
 * <p>Backends are cached by the configuration they were built from, so every station on one version of
 * cluster storage shares one backend and its pool. A backend that leaves the cache is closed once the
 * calls still using it are done.
 */
@Singleton
public class StorageBackendResolver {
    private static final long MAX_CACHED = 256;
    private static final Logger log = LoggerFactory.getLogger(StorageBackendResolver.class);

    private final StorageBackendFactory factory;
    private final @Nullable StationPlacements placements;
    private final Cache<Integer, Optional<BackendKey>> stationKeys =
            Caffeine.newBuilder().maximumSize(MAX_CACHED).build();
    private final Set<StorageBackend> handedOver =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Cache<BackendKey, StorageBackend> backends = Caffeine.newBuilder()
            .maximumSize(MAX_CACHED)
            .executor(Runnable::run)
            .<BackendKey, StorageBackend>removalListener((key, backend, cause) -> {
                if (backend != null && !handedOver.remove(backend)) closeQuietly(backend);
            })
            .build();

    @Inject
    public StorageBackendResolver(
            StorageBackendFactory factory,
            StationStorageConfigRepository overrideRepository,
            ClusterStationStorageRepository placementRepository) {
        this(factory, new StationPlacements(overrideRepository, placementRepository));
    }

    /** A resolver that pins every category to one local backend, for tests. */
    public StorageBackendResolver(LocalStorageBackend localBackend) {
        this(new StorageBackendFactory(new Storage(), localBackend, new CredentialCipher("")), null);
    }

    /**
     * @param placements where a station's own bytes are kept, or null for a resolver that sends every
     *                   station to the instance default
     */
    private StorageBackendResolver(StorageBackendFactory factory, @Nullable StationPlacements placements) {
        this.factory = factory;
        this.placements = placements;
    }

    private static void closeQuietly(StorageBackend backend) {
        try {
            backend.close();
        } catch (RuntimeException e) {
            log.warn("Could not close a station storage backend of type {}", backend.type(), e);
        }
    }

    /**
     * The backend for a producer call.
     *
     * @throws IllegalArgumentException when the scope is not the kind the category expects
     */
    public StorageBackend forScope(StorageScope scope, StorageCategory category) {
        if (scope.kind() != category.scopeKind()) {
            throw new IllegalArgumentException(
                    "Category %s expects scope %s but got %s".formatted(category, category.scopeKind(), scope.kind()));
        }
        if (category.isLocalPinned()) {
            return factory.localBackend();
        }
        var stationPlacements = placements;
        if (scope instanceof StorageScope.Station station && stationPlacements != null) {
            Optional<StorageBackend> override = stationBackend(stationPlacements, station.stationId());
            if (override.isPresent()) return override.get();
        }
        return factory.instanceDefault();
    }

    public StorageBackend instanceDefault() {
        return factory.instanceDefault();
    }

    /**
     * Forgets where one station's bytes go. A backend of the station's own is closed; a version of
     * cluster storage stays, since other stations stand on it.
     */
    public void invalidateStation(int stationId) {
        forget(stationId).filter(BackendKey.Own.class::isInstance).ifPresent(backends::invalidate);
    }

    /**
     * Forgets where one station's bytes go and hands a backend of its own to the caller instead of
     * closing it, since a move still deletes the old bytes through it.
     *
     * @return the station's own backend as it was cached, now the caller's to close
     */
    public Optional<StorageBackend> detachStation(int stationId) {
        var key = forget(stationId).filter(BackendKey.Own.class::isInstance);
        if (key.isEmpty()) return Optional.empty();
        var backend = backends.getIfPresent(key.get());
        if (backend == null) return Optional.empty();
        handedOver.add(backend);
        backends.invalidate(key.get());
        return Optional.of(backend);
    }

    /**
     * Forgets where several stations' bytes go after a change at their cluster, and closes the backends
     * they stood on, since the change may have been to the credentials.
     */
    public void invalidateStations(Iterable<Integer> stationIds) {
        for (int stationId : stationIds) {
            forget(stationId).ifPresent(backends::invalidate);
        }
    }

    /** Flushes every mapping and closes every cached backend. */
    public void invalidateAll() {
        stationKeys.invalidateAll();
        backends.invalidateAll();
    }

    /** Closes every cached backend, the instance default and the shared clients, once, at shutdown. */
    public void closeAll() {
        invalidateAll();
        factory.closeInstanceDefault();
    }

    private Optional<BackendKey> forget(int stationId) {
        var key = stationKeys.getIfPresent(stationId);
        stationKeys.invalidate(stationId);
        return key == null ? Optional.empty() : key;
    }

    /**
     * What a station's bytes go to, read from where they are and never from what a cluster decided: a
     * decision takes effect when it is written, and the copy of the bytes only later.
     */
    private Optional<StorageBackend> stationBackend(StationPlacements stationPlacements, int stationId) {
        Optional<BackendKey> key = stationKeys.get(stationId, stationPlacements::keyOf);
        return key.map(
                found -> backends.get(found, unused -> factory.buildForStation(stationPlacements.configOf(stationId))));
    }

    /**
     * Where a station's own bytes are kept: an override of its own, else the cluster storage they were
     * carried to.
     */
    private record StationPlacements(
            StationStorageConfigRepository overrideRepository, ClusterStationStorageRepository placementRepository) {

        Optional<BackendKey> keyOf(int stationId) {
            if (overrideRepository.findOne(stationId).isPresent()) return Optional.of(new BackendKey.Own(stationId));
            return placementRepository
                    .findByStation(stationId)
                    .map(placement -> new BackendKey.Cluster(placement.configId()));
        }

        StationStorageBackendConfig configOf(int stationId) {
            return overrideRepository
                    .findOne(stationId)
                    .map(StationStorageConfigRepository.Row::config)
                    .or(() -> placementRepository.findConfigForStation(stationId))
                    .orElseThrow(() -> new StorageException("Station " + stationId + " stands on no storage any more"));
        }
    }

    /** The configuration a backend was built from. */
    private sealed interface BackendKey {
        /** A station's own override row. */
        record Own(int stationId) implements BackendKey {}

        /** A version of a cluster's storage, shared by every station carried to it. */
        record Cluster(int configId) implements BackendKey {}
    }
}
