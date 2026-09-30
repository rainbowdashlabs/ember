/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StationTransferFileServiceTest {
    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final StorageScope.Station SCOPE = new StorageScope.Station(4, UID);

    private StationRepository stations;
    private StorageService storage;
    private StationTransferFileService service;

    @BeforeEach
    void setup() {
        stations = mock(StationRepository.class);
        storage = mock(StorageService.class);
        var station = mock(Station.class);
        when(station.uid()).thenReturn(UID);
        when(stations.findById(4)).thenReturn(Optional.of(station));
        when(storage.listKeys(SCOPE, StorageCategory.KB_FILES, "")).thenReturn(List.of("c", "a", "d", "b"));
        service = new StationTransferFileService(stations, storage);
    }

    @Test
    void theKeysComeSortedAPageAtATimeWithTheCursorForTheNext() {
        var first = service.page(4, StorageCategory.KB_FILES, null, 3);
        var second = service.page(4, StorageCategory.KB_FILES, first.next(), 3);

        assertEquals(List.of("a", "b", "c"), first.keys());
        assertEquals("c", first.next());
        assertEquals(4, first.total());
        assertEquals(List.of("d"), second.keys());
        assertNull(second.next());
        assertEquals(4, second.total());
    }

    @Test
    void aPageWithoutALimitHoldsTheDefaultNumberOfKeys() {
        assertEquals(4, service.page(4, StorageCategory.KB_FILES, "", 0).keys().size());
    }

    @Test
    void aStationThatIsGoneHasNoFilesToHandOver() {
        var refused = assertThrows(RefusalResponse.class, () -> service.page(5, StorageCategory.KB_FILES, null, 10));

        assertEquals(Refusal.STATION_NOT_HERE_FOR_TRANSFER, refused.refusal());
    }

    @Test
    void aFileThatIsGoneIsRefused() {
        when(storage.readRelative(eq(SCOPE), any(), anyString())).thenReturn(Optional.empty());

        var refused = assertThrows(RefusalResponse.class, () -> service.open(4, StorageCategory.KB_FILES, "gone.pdf"));

        assertEquals(Refusal.TRANSFER_FILE_NOT_HERE, refused.refusal());
    }
}
