/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.entity.ClusterStationStorage;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageBackendResolverTest {
    private static final int OWN_ONE = 1;
    private static final int OWN_TWO = 2;
    private static final int CLUSTER_ONE = 11;
    private static final int CLUSTER_TWO = 12;
    private static final int ON_DEFAULT = 21;
    private static final int CONFIG_ID = 7;

    @TempDir
    Path root;

    private RecordingFactory factory;
    private StorageBackendResolver resolver;

    @BeforeEach
    void setUp() {
        var overrides = Mockito.mock(StationStorageConfigRepository.class);
        Mockito.when(overrides.findOne(Mockito.anyInt())).thenAnswer(call -> {
            int id = call.getArgument(0);
            return id < 10 ? Optional.of(new StationStorageConfigRepository.Row(id, config())) : Optional.empty();
        });
        var placements = Mockito.mock(ClusterStationStorageRepository.class);
        Mockito.when(placements.findByStation(Mockito.anyInt())).thenAnswer(call -> {
            int id = call.getArgument(0);
            return id > 10 && id < 20
                    ? Optional.of(new ClusterStationStorage(id, 3, CONFIG_ID, Instant.EPOCH))
                    : Optional.empty();
        });
        Mockito.when(placements.findConfigForStation(Mockito.anyInt())).thenReturn(Optional.of(config()));
        factory = new RecordingFactory(new LocalStorageBackend(root));
        resolver = new StorageBackendResolver(factory, overrides, placements);
    }

    @Test
    void aStationKeepsItsBackendUntilItIsInvalidated() {
        var first = backendOf(OWN_ONE);

        assertSame(first, resolver.forScope(station(OWN_ONE), StorageCategory.KB_FILES));
        assertSame(factory.localBackend(), resolver.forScope(new StorageScope.Instance(), StorageCategory.DOCUMENT));
        assertSame(factory.instanceDefault(), backendOf(ON_DEFAULT));
    }

    @Test
    void stationsOnOneClusterStorageShareOneBackend() {
        assertSame(backendOf(CLUSTER_ONE), backendOf(CLUSTER_TWO));
        assertNotSame(backendOf(OWN_ONE), backendOf(OWN_TWO));
        assertEquals(3, factory.built.size());
    }

    @Test
    void anInvalidatedOwnBackendIsClosedAndAClusterOneStays() {
        var own = (RecordingBackend) backendOf(OWN_ONE);
        var kept = (RecordingBackend) backendOf(OWN_TWO);
        var cluster = (RecordingBackend) backendOf(CLUSTER_ONE);

        resolver.invalidateStation(OWN_ONE);
        resolver.invalidateStation(CLUSTER_ONE);
        resolver.invalidateStation(ON_DEFAULT);

        assertTrue(own.closed, "a backend nobody can reach any more must release its connections");
        assertFalse(kept.closed);
        assertFalse(cluster.closed, "the other stations on the cluster storage still use it");
        assertNotSame(own, backendOf(OWN_ONE));
        assertSame(cluster, backendOf(CLUSTER_TWO));
    }

    @Test
    void aChangeAtTheClusterClosesTheBackendItsStationsStoodOn() {
        var cluster = (RecordingBackend) backendOf(CLUSTER_ONE);
        backendOf(CLUSTER_TWO);

        resolver.invalidateClusterVersion(CONFIG_ID);

        assertTrue(cluster.closed);
        assertNotSame(cluster, backendOf(CLUSTER_TWO));
    }

    /**
     * A station that stopped being remembered, because it moved or fell out of the cache, must not keep the
     * backend of its old version alive past a credential change at the cluster.
     */
    @Test
    void aCredentialChangeAtTheClusterReachesTheBackendWhoeverIsRemembered() {
        var cluster = (RecordingBackend) backendOf(CLUSTER_ONE);
        resolver.invalidateStation(CLUSTER_ONE);

        resolver.invalidateClusterVersion(CONFIG_ID);

        assertTrue(cluster.closed, "the backend built with the old credentials is gone");
        assertNotSame(cluster, backendOf(CLUSTER_TWO));
    }

    @Test
    void aMoveTakesTheOwnBackendOverAndClosesItItself() {
        var own = (RecordingBackend) backendOf(OWN_ONE);
        backendOf(CLUSTER_ONE);

        var detached = resolver.detachStation(OWN_ONE);

        assertSame(own, detached.orElseThrow());
        assertFalse(own.closed, "whoever took the backend over closes it");
        assertTrue(resolver.detachStation(CLUSTER_ONE).isEmpty());
        assertTrue(resolver.detachStation(ON_DEFAULT).isEmpty());
        assertTrue(resolver.detachStation(OWN_TWO).isEmpty());
        resolver.invalidateAll();
        assertFalse(own.closed);
    }

    @Test
    void closingClosesEveryBackendAndTheSharedClients() {
        var own = (RecordingBackend) backendOf(OWN_ONE);
        var cluster = (RecordingBackend) backendOf(CLUSTER_ONE);

        resolver.closeAll();

        assertTrue(own.closed);
        assertTrue(cluster.closed);
        assertThrows(StorageUnavailableException.class, () -> factory.clients().ssh());
    }

    @Test
    void aBackendThatFailsToCloseDoesNotKeepTheOthersOpen() {
        var failing = (RecordingBackend) backendOf(OWN_ONE);
        failing.failsToClose = true;
        var own = (RecordingBackend) backendOf(OWN_TWO);
        var cluster = (RecordingBackend) backendOf(CLUSTER_ONE);

        assertDoesNotThrow(resolver::closeAll);

        assertTrue(failing.closed);
        assertTrue(own.closed);
        assertTrue(cluster.closed);
        assertThrows(StorageUnavailableException.class, () -> factory.clients().ssh());
    }

    private StorageBackend backendOf(int stationId) {
        return resolver.forScope(station(stationId), StorageCategory.MEDIA_FILES);
    }

    private static StorageScope.Station station(int stationId) {
        return new StorageScope.Station(stationId, new UUID(0, stationId));
    }

    private static StationStorageBackendConfig config() {
        return new StationStorageBackendConfig.S3Variant(
                "https://s3.example.invalid",
                "eu-central-1",
                "bucket",
                true,
                Optional.empty(),
                "",
                new EncryptedBlob(new byte[12], new byte[16]));
    }

    private static final class RecordingFactory extends StorageBackendFactory {
        private final List<RecordingBackend> built = new ArrayList<>();

        private RecordingFactory(LocalStorageBackend local) {
            super(new Storage(), local, null);
        }

        @Override
        public StorageBackend buildForStation(StationStorageBackendConfig config) {
            var backend = new RecordingBackend();
            built.add(backend);
            return backend;
        }
    }

    private static final class RecordingBackend implements StorageBackend {
        private volatile boolean closed;
        private volatile boolean failsToClose;

        @Override
        public StorageBackendType type() {
            return StorageBackendType.S3;
        }

        @Override
        public String destination() {
            return "recording@" + System.identityHashCode(this);
        }

        @Override
        public void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateMetadata(String fullKey, ObjectMetadata metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredStream> read(String fullKey) {
            return Optional.empty();
        }

        @Override
        public void delete(String fullKey) {}

        @Override
        public boolean exists(String fullKey) {
            return false;
        }

        @Override
        public List<String> listByPrefix(String prefix) {
            return List.of();
        }

        @Override
        public long sumSizeByPrefix(String prefix) {
            return 0;
        }

        @Override
        public HealthStatus probe() {
            return HealthStatus.ok();
        }

        @Override
        public void close() {
            closed = true;
            if (failsToClose) throw new IllegalStateException("expected by the test");
        }
    }
}
