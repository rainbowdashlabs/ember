/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.QuotaAuthority;
import dev.chojo.ember.feature.storage.entity.QuotaOrigin;
import dev.chojo.ember.feature.storage.entity.StationQuotas;
import dev.chojo.ember.feature.storage.entity.StationQuotas.ResolvedQuota;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository.StationPresetAssignment;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageUsageReportServiceTest {
    private static final StorageCategory UNCOUNTED = Arrays.stream(StorageCategory.values())
            .filter(category -> !category.enforcesQuota())
            .findFirst()
            .orElseThrow();

    private StorageQuotaService quotas;
    private StorageUsageRepository usage;
    private StorageQuotaPresetRepository presets;
    private StationRepository stations;
    private StorageUsageReportService service;

    @BeforeEach
    void setup() {
        quotas = mock(StorageQuotaService.class);
        usage = mock(StorageUsageRepository.class);
        presets = mock(StorageQuotaPresetRepository.class);
        stations = mock(StationRepository.class);
        service = new StorageUsageReportService(quotas, usage, presets, stations);
    }

    private static StorageUsage used(int stationId, StorageCategory category, long bytes) {
        return new StorageUsage(stationId, category, bytes, 1, Instant.EPOCH);
    }

    private static StationQuotas quotasOf(int stationId, long totalBytes, QuotaOrigin origin) {
        var total = new ResolvedQuota(totalBytes, origin);
        return new StationQuotas(stationId, QuotaAuthority.NOBODY, total, total, total, total, total, total, total);
    }

    private static Station station(int id, String name) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        when(station.name()).thenReturn(name);
        when(station.uid()).thenReturn(new UUID(0, id));
        return station;
    }

    @Test
    void aStationsUsageCountsOnlyWhatIsHeldAgainstItsQuota() {
        when(usage.findByStation(4))
                .thenReturn(List.of(used(4, StorageCategory.MEDIA_FILES, 250), used(4, UNCOUNTED, 9_000)));
        when(quotas.getEffectiveTotalQuota(4)).thenReturn(1_000L);
        when(quotas.getEffectiveCategoryQuota(4, StorageCategory.MEDIA_FILES)).thenReturn(500L);

        var report = service.stationUsage(4);

        assertEquals(250, report.totalBytes());
        assertEquals(1_000, report.quotaBytes());
        assertEquals(25, report.quotaUsedPercent());
        assertEquals(500L, report.categoryQuotas().get(StorageCategory.MEDIA_FILES.name()));
        assertFalse(report.categoryQuotas().containsKey(UNCOUNTED.name()));
        assertEquals(2, report.categories().size());
        assertFalse(report.usesOwnBackend());
    }

    @Test
    void aStationOnStorageOfItsOwnHasNoLimitRatherThanALimitOfNothing() {
        when(usage.findByStation(4)).thenReturn(List.of(used(4, StorageCategory.MEDIA_FILES, 250)));
        when(quotas.isUnbounded(4)).thenReturn(true);

        var report = service.stationUsage(4);

        assertTrue(report.usesOwnBackend());
        assertEquals(0, report.quotaBytes());
        assertEquals(0, report.quotaUsedPercent());
        assertTrue(report.categoryQuotas().isEmpty());
        verify(quotas, never()).getEffectiveTotalQuota(anyInt());
        verify(quotas, never()).getEffectiveCategoryQuota(eq(4), eq(StorageCategory.MEDIA_FILES));
    }

    @Test
    void theOverviewHasEveryStationWithItsPresetAndWhoseWordItsQuotaIsOn() {
        var bounded = station(1, "Nord");
        var unbounded = station(2, "Süd");
        when(stations.findAll()).thenReturn(List.of(bounded, unbounded));
        when(usage.findAll())
                .thenReturn(List.of(used(1, StorageCategory.MEDIA_FILES, 100), used(1, StorageCategory.KB_FILES, 100)));
        when(presets.findStationPresetAssignments()).thenReturn(Map.of(1, new StationPresetAssignment(9, "Klein")));
        when(quotas.resolveQuotas(1)).thenReturn(quotasOf(1, 400, QuotaOrigin.CLUSTER_GRANT));
        when(quotas.resolveQuotas(2)).thenReturn(quotasOf(2, Long.MAX_VALUE, QuotaOrigin.UNLIMITED));

        var rows = service.adminUsage();

        var nord = rows.getFirst();
        assertEquals(new UUID(0, 1).toString(), nord.stationId());
        assertEquals(200, nord.totalBytes());
        assertEquals(50, nord.quotaUsedPercent());
        assertEquals(9, nord.presetId());
        assertEquals("Klein", nord.presetName());
        assertEquals(QuotaOrigin.CLUSTER_GRANT, nord.origin());
        var sued = rows.get(1);
        assertTrue(sued.usesOwnBackend());
        assertEquals(0, sued.quotaBytes());
        assertNull(sued.presetId());
        assertTrue(sued.categories().isEmpty());
    }
}
