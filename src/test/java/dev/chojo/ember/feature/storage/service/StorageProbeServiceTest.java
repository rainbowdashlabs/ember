/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The probe answers every owner the same way: whether the storage answered, never why it did not.
 */
class StorageProbeServiceTest {
    private static final StationStorageBackendConfig CONFIG =
            new StationStorageBackendConfig.SftpVariant("sftp.test", 22, "ember", "", "", null);
    private static final Actor ACTOR = Actor.human(1, null);

    private StorageBackendFactory factory;
    private StorageBackendAuditService audit;
    private StorageProbeService service;

    @BeforeEach
    void setup() {
        factory = mock(StorageBackendFactory.class);
        audit = mock(StorageBackendAuditService.class);
        service = new StorageProbeService(factory, audit);
    }

    @Test
    void aBackendThatAnswersIsHealthyAndClosedAgain() throws Exception {
        var backend = mock(StorageBackend.class);
        when(backend.probe()).thenReturn(HealthStatus.ok());
        when(factory.buildForStation(CONFIG)).thenReturn(backend);

        var result = service.probe(new Owner.Station(4), CONFIG);

        assertTrue(result.healthy());
        assertNull(result.error());
        verify(backend).close();
        verifyNoInteractions(audit);
    }

    /** The instance is told what a station is told: an operator who wants the reason reads the log. */
    @Test
    void aBackendThatCannotBeBuiltAnswersAsNotHealthyWithoutTheReason() {
        when(factory.buildForInstance(any())).thenThrow(new IllegalArgumentException("bucket missing"));

        var result = service.probe(new StorageBackendSettings());

        assertFalse(result.healthy());
        assertEquals(StorageProbeService.PROBE_FAILED, result.error());
    }

    @Test
    void storageNotSavedYetIsMaskedForAnAssociationAndNotWrittenDown() {
        var backend = mock(StorageBackend.class);
        when(backend.probe()).thenReturn(HealthStatus.unhealthy("Connection refused"));
        when(factory.buildForStation(CONFIG)).thenReturn(backend);

        var result = service.probe(new Owner.Association(3), CONFIG);

        assertEquals(StorageProbeService.PROBE_FAILED, result.error());
        verifyNoInteractions(audit);
    }

    @Test
    void savedStorageIsWrittenToItsOwnersHistoryWithTheMaskedReason() {
        var backend = mock(StorageBackend.class);
        when(backend.probe()).thenReturn(HealthStatus.unhealthy("timeout"));
        when(factory.buildForStation(CONFIG)).thenReturn(backend);

        var result = service.probeSaved(ACTOR, new Owner.Association(3), CONFIG);

        assertEquals(StorageProbeService.PROBE_FAILED, result.error());
        verify(audit)
                .recordProbe(
                        ACTOR, new Owner.Association(3), StorageAuditOutcome.FAILED, StorageProbeService.PROBE_FAILED);
    }

    /** The backend in use is asked, not rebuilt, and stays open: files are being kept on it. */
    @Test
    void theRunningBackendIsProbedInPlaceAndLeftOpen() throws Exception {
        var running = mock(StorageBackend.class);
        when(running.probe()).thenReturn(HealthStatus.ok());

        var result = service.probeRunning(ACTOR, new Owner.Instance(), running);

        assertTrue(result.healthy());
        verify(running, never()).close();
        verify(audit).recordProbe(ACTOR, new Owner.Instance(), StorageAuditOutcome.OK, null);
    }
}
