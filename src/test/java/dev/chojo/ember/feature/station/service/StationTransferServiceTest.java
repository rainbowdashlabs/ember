/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.federation.service.MovedStationSwitchover;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationTransferServiceTest {
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000009");

    private StationRepository stations;
    private StationExportService exports;
    private MovedStationSwitchover switchover;
    private StationTransferService service;
    private Station station;

    @BeforeEach
    void setup() {
        stations = mock(StationRepository.class);
        exports = mock(StationExportService.class);
        switchover = mock(MovedStationSwitchover.class);
        service = new StationTransferService(stations, exports, switchover);
        station = mock(Station.class);
        when(station.uid()).thenReturn(STATION_UID);
        when(stations.findById(9)).thenReturn(Optional.of(station));
        when(exports.findTransferTarget(9)).thenReturn(Optional.of("https://recorded.test"));
    }

    @Test
    void aStationMovingOutNamesWhereTo() {
        when(stations.isReadOnlyForTransfer(9)).thenReturn(true);

        assertEquals("https://recorded.test", service.status(9).targetInstanceUrl());
    }

    @Test
    void aStationStayingNamesNothing() {
        var status = service.status(9);

        assertFalse(status.readOnly());
        assertNull(status.targetInstanceUrl());
        verify(exports, never()).findTransferTarget(anyInt());
    }

    @Test
    void theDestinationAsItNamesItselfWins() {
        service.complete(9, "https://signalled.test");

        verify(exports).markTransferComplete(9);
        verify(stations).markMovedAway(9, "https://signalled.test");
        verify(switchover).switchOver(station, "https://signalled.test");
    }

    @Test
    void withoutASignalTheRecordedDestinationIsUsed() {
        service.complete(9, " ");

        verify(stations).markMovedAway(9, "https://recorded.test");
        verify(switchover).switchOver(station, "https://recorded.test");
    }

    @Test
    void aStationThatIsGoneHasNoPartnersToSwitchOver() {
        when(exports.findTransferTarget(7)).thenReturn(Optional.empty());

        service.complete(7, null);

        verify(exports).markTransferComplete(7);
        verify(switchover, never()).switchOver(any(), any());
    }
}
