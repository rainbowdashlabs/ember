/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.QuotaUpdateRequest;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageQuotaAdminServiceTest {
    private static final UUID HERE = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID GONE = UUID.fromString("00000000-0000-0000-0000-000000000008");

    private StorageQuotaPresetRepository presets;
    private StorageQuotaService quotas;
    private StorageReconciliationService reconciliation;
    private TaskScheduler scheduler;
    private StorageQuotaAdminService service;

    @BeforeEach
    void setup() {
        presets = mock(StorageQuotaPresetRepository.class);
        quotas = mock(StorageQuotaService.class);
        reconciliation = mock(StorageReconciliationService.class);
        scheduler = mock(TaskScheduler.class);
        var stations = mock(StationRepository.class);
        when(stations.resolveId(HERE)).thenReturn(Optional.of(7));
        when(stations.resolveId(GONE)).thenReturn(Optional.empty());
        service = new StorageQuotaAdminService(presets, quotas, reconciliation, stations, scheduler);
    }

    @Test
    void aPresetGoesToEveryStationStillHereAndPassesOverOneThatIsGone() {
        service.applyPreset(3, List.of(HERE.toString(), GONE.toString()));

        verify(presets).applyToStation(3, 7);
        verify(presets, never()).applyToStation(eq(3), eq(8));
    }

    @Test
    void aPresetForSomethingThatIsNoStationGoesToNobody() {
        var refused = assertThrows(
                RefusalResponse.class, () -> service.applyPreset(3, List.of(HERE.toString(), "not-a-station")));

        assertEquals(Refusal.STATION_NOT_AN_IDENTITY_FOR_STORAGE_ADMIN, refused.refusal());
        verify(presets, never()).applyToStation(anyInt(), anyInt());
    }

    @Test
    void theLimitsOfAStationThatIsNotHereAreRefused() {
        var request = new QuotaUpdateRequest(1L, null, null, null, null, null, null);

        var refused = assertThrows(RefusalResponse.class, () -> service.updateStationQuotas(GONE, request));

        assertEquals(Refusal.STATION_NOT_HERE_FOR_STORAGE_ADMIN, refused.refusal());
        verify(quotas, never()).updateStationQuotas(anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aStationsLimitsAreSetAndReset() {
        service.updateStationQuotas(HERE, new QuotaUpdateRequest(10L, 1L, 2L, 3L, 4L, 5L, 6L));
        service.resetStationQuotas(HERE);

        verify(quotas).updateStationQuotas(7, 10L, 1L, 2L, 3L, 4L, 5L, 6L);
        verify(presets).resetStationQuotas(7);
    }

    @Test
    void aStationIsCountedAgainAtOnceAndAllOfThemInTheBackground() {
        service.recalculateStation(HERE);
        service.recalculateAll();

        verify(reconciliation).reconcileStation(7);
        verify(scheduler).background(eq("admin-reconcile-all"), any());
        verify(reconciliation, never()).reconcileAll();
        assertThrows(RefusalResponse.class, () -> service.recalculateStation(GONE));
        assertThrows(RefusalResponse.class, () -> service.resetStationQuotas(GONE));
    }
}
