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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageProbeServiceTest {
    private static final StationStorageBackendConfig CONFIG =
            new StationStorageBackendConfig.SftpVariant("sftp.test", 22, "ember", "", "", null);

    @Test
    void aBackendThatAnswersIsHealthyAndClosedAgain() throws Exception {
        var factory = mock(StorageBackendFactory.class);
        var backend = mock(StorageBackend.class);
        when(backend.probe()).thenReturn(HealthStatus.ok());
        when(factory.buildForStation(CONFIG)).thenReturn(backend);

        var result = new StorageProbeService(factory).probe(CONFIG);

        assertTrue(result.healthy());
        assertNull(result.error());
        verify(backend).close();
    }

    @Test
    void aBackendThatCannotBeBuiltAnswersAsNotHealthyWithTheReason() {
        var factory = mock(StorageBackendFactory.class);
        when(factory.buildForInstance(any())).thenThrow(new IllegalArgumentException("bucket missing"));

        var result = new StorageProbeService(factory).probe(new StorageBackendSettings());

        assertFalse(result.healthy());
        assertEquals("bucket missing", result.error());
    }
}
