/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageBackendResolverTest {
    private static final StorageScope.Station STATION_ONE = new StorageScope.Station(1, UUID.randomUUID());
    private static final StorageScope.Station STATION_TWO = new StorageScope.Station(2, UUID.randomUUID());

    @TempDir
    Path root;

    private RecordingFactory factory;
    private StorageBackendResolver resolver;

    @BeforeEach
    void setUp() {
        var overrides = Mockito.mock(StationStorageConfigRepository.class);
        Mockito.when(overrides.findOne(Mockito.anyInt()))
                .thenAnswer(call -> Optional.of(new StationStorageConfigRepository.Row(call.getArgument(0), config())));
        factory = new RecordingFactory(new LocalStorageBackend(root));
        resolver = new StorageBackendResolver(factory, overrides, Mockito.mock(ClusterStationStorageRepository.class));
    }

    @Test
    void aStationKeepsItsBackendUntilItIsInvalidated() {
        var first = resolver.forScope(STATION_ONE, StorageCategory.MEDIA_FILES);

        assertSame(first, resolver.forScope(STATION_ONE, StorageCategory.KB_FILES));
        assertSame(factory.localBackend(), resolver.forScope(new StorageScope.Instance(), StorageCategory.DOCUMENT));
    }

    @Test
    void anInvalidatedBackendIsClosed() {
        var replaced = (RecordingBackend) resolver.forScope(STATION_ONE, StorageCategory.MEDIA_FILES);
        var kept = (RecordingBackend) resolver.forScope(STATION_TWO, StorageCategory.MEDIA_FILES);

        resolver.invalidateStation(STATION_ONE.stationId());

        assertTrue(replaced.closed, "a backend nobody can reach any more must release its connections");
        assertFalse(kept.closed);
        assertNotSame(replaced, resolver.forScope(STATION_ONE, StorageCategory.MEDIA_FILES));
    }

    @Test
    void invalidatingEverythingClosesEveryCachedBackend() {
        resolver.forScope(STATION_ONE, StorageCategory.MEDIA_FILES);
        resolver.forScope(STATION_TWO, StorageCategory.MEDIA_FILES);

        resolver.invalidateStations(List.of(STATION_ONE.stationId()));
        resolver.invalidateAll();

        assertTrue(factory.built.stream().allMatch(backend -> backend.closed));
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

        @Override
        public StorageBackendType type() {
            return StorageBackendType.S3;
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
        public HealthStatus probe() {
            return HealthStatus.ok();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
