/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shutdown closes the backends the resolver holds open, and one that fails to close does not keep
 * the others open.
 */
class StorageBackendResolverCloseTest {

    @Test
    void closesTheCachedStationBackendsAndTheInstanceDefault() throws Exception {
        var factory = mock(StorageBackendFactory.class);
        var overrides = mock(StationStorageConfigRepository.class);
        var stationBackend = mock(StorageBackend.class);
        when(overrides.findOne(7)).thenReturn(Optional.of(new StationStorageConfigRepository.Row(7, null)));
        when(factory.buildForStation(any())).thenReturn(stationBackend);
        doThrow(new IllegalStateException("expected by the test"))
                .when(stationBackend)
                .close();
        var resolver = new StorageBackendResolver(factory, overrides, mock(ClusterStationStorageRepository.class));
        resolver.forScope(new StorageScope.Station(7, UUID.randomUUID()), StorageCategory.MEDIA_FILES);

        assertDoesNotThrow(resolver::closeAll);

        verify(stationBackend).close();
        verify(factory).closeInstanceDefault();
    }
}
