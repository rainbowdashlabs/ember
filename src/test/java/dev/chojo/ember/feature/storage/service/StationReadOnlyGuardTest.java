/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class StationReadOnlyGuardTest {

    @Test
    void requireWritableDoesNotThrowWhenStationIsWritable() {
        var repo = Mockito.mock(StationRepository.class);
        Mockito.when(repo.isReadOnlyForTransfer(42)).thenReturn(false);
        var guard = new StationReadOnlyGuard(repo);

        assertDoesNotThrow(() -> guard.requireWritable(42));
    }

    @Test
    void requireWritableThrowsWhenStationIsReadOnly() {
        var repo = Mockito.mock(StationRepository.class);
        Mockito.when(repo.isReadOnlyForTransfer(7)).thenReturn(true);
        var guard = new StationReadOnlyGuard(repo);

        var ex = assertThrows(RefusalResponse.class, () -> guard.requireWritable(7));
        assertEquals(Refusal.STATION_READ_ONLY_FOR_BACKGROUND_WRITE, ex.refusal());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.getCode(), ex.getStatus());
    }

    @Test
    void isWritableMirrorsRepositoryFlag() {
        var repo = Mockito.mock(StationRepository.class);
        Mockito.when(repo.isReadOnlyForTransfer(1)).thenReturn(false);
        Mockito.when(repo.isReadOnlyForTransfer(2)).thenReturn(true);
        var guard = new StationReadOnlyGuard(repo);

        assertTrue(guard.isWritable(1));
        assertFalse(guard.isWritable(2));
    }
}
