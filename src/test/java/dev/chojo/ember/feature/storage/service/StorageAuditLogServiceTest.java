/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.repository.StorageBackendAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageAuditLogServiceTest {
    private static final UUID STATION = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant BEFORE = Instant.parse("2026-09-01T10:00:00Z");

    private StorageBackendAuditRepository repository;
    private StorageAuditLogService service;

    @BeforeEach
    void setup() {
        repository = mock(StorageBackendAuditRepository.class);
        var stations = mock(StationRepository.class);
        when(stations.resolveId(STATION)).thenReturn(Optional.of(4));
        service = new StorageAuditLogService(repository, stations);
    }

    private static StorageAuditEntry entry() {
        return new StorageAuditEntry(
                5,
                BEFORE,
                Optional.of(1),
                Optional.empty(),
                Optional.empty(),
                Optional.of(4),
                StorageAuditAction.MIGRATION_COMPLETED,
                Optional.empty(),
                Optional.of("{}"),
                StorageAuditOutcome.OK,
                Optional.empty());
    }

    @Test
    void theHistoryIsNarrowedToTheStationNamedAndReadBeforeThePointGiven() {
        when(repository.findAll(Optional.of(BEFORE), Optional.of(4), 200)).thenReturn(List.of(entry()));

        var entries = service.list(BEFORE.toString(), STATION.toString(), 5_000);

        assertEquals(1, entries.size());
        assertEquals(BEFORE.toString(), entries.getFirst().ts());
        assertEquals(1, entries.getFirst().actorAccountId());
        assertNull(entries.getFirst().actorMemberId());
        assertEquals("{}", entries.getFirst().newConfig());
    }

    @Test
    void aStationsHistoryHoldsAtLeastOneEntryPerPage() {
        service.listForStation(4, null, 0);

        verify(repository).findByStation(4, Optional.empty(), 1);
    }

    @Test
    void aPointInTimeThatIsNoneIsRefused() {
        var refused = assertThrows(RefusalResponse.class, () -> service.listForStation(4, "yesterday", 50));

        assertEquals(Refusal.STORAGE_AUDIT_BEFORE_NOT_A_TIME, refused.refusal());
        verify(repository, never()).findByStation(anyInt(), any(), anyInt());
    }

    @Test
    void somethingNamedAsAStationThatIsNoneIsRefused() {
        var refused = assertThrows(RefusalResponse.class, () -> service.list(null, "station-4", 50));

        assertEquals(Refusal.STATION_NOT_AN_IDENTITY_FOR_STORAGE_ADMIN, refused.refusal());
    }
}
