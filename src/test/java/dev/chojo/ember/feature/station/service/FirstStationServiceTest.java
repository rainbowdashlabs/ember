/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Founding the first station of a fresh instance, and refusing a second one there.
 */
class FirstStationServiceTest {
    private static final int ACCOUNT = 11;
    private static final int STATION = 21;
    private static final int MEMBER = 31;
    private static final int PERMISSION = 41;

    private StationService stationService;
    private StationRepository stations;
    private StationMemberRepository members;
    private FirstStationService service;
    private Station founded;

    @BeforeEach
    void setup() {
        stationService = mock(StationService.class);
        stations = mock(StationRepository.class);
        members = mock(StationMemberRepository.class);
        service = new FirstStationService(stationService, stations, members);

        founded = mock(Station.class);
        when(founded.id()).thenReturn(STATION);
        when(stationService.create("Jugendfeuerwehr Musterstadt")).thenReturn(founded);
        var member = mock(StationMember.class);
        when(member.id()).thenReturn(MEMBER);
        when(members.create(STATION, ACCOUNT)).thenReturn(member);
        var permission = mock(Permission.class);
        when(permission.id()).thenReturn(PERMISSION);
        when(members.findPermissionByName(StationPermission.STATION_ADMINISTRATOR))
                .thenReturn(Optional.of(permission));
    }

    @Test
    void anInstanceWithoutStationsWaitsForItsFirst() {
        when(stations.findAllRegular()).thenReturn(List.of());

        assertTrue(service.isNeeded());
    }

    @Test
    void anInstanceWithAStationWaitsForNothing() {
        when(stations.findAllRegular()).thenReturn(List.of(founded));

        assertFalse(service.isNeeded());
    }

    @Test
    void theFounderBecomesManagerAndOwnerOfTheFirstStation() {
        when(stations.findAllRegular()).thenReturn(List.of());

        var station = service.found("  Jugendfeuerwehr Musterstadt ", ACCOUNT);

        assertSame(founded, station);
        verify(members).create(STATION, ACCOUNT);
        verify(members).setUserType(MEMBER, StationUserType.MANAGER);
        verify(members).grantPermission(MEMBER, PERMISSION);
        verify(stations).setOwner(STATION, MEMBER);
    }

    @Test
    void aSecondFirstStationIsRefused() {
        when(stations.findAllRegular()).thenReturn(List.of(founded));

        var refused = assertThrows(RefusalResponse.class, () -> service.found("Noch eine", ACCOUNT));

        assertEquals(StationRefusal.FIRST_STATION_ALREADY_FOUNDED, refused.refusal());
        verify(stationService, never()).create(anyString());
    }

    @Test
    void aFirstStationNeedsAName() {
        var refused = assertThrows(RefusalResponse.class, () -> service.found("  ", ACCOUNT));

        assertEquals(StationRefusal.FIRST_STATION_NEEDS_A_NAME, refused.refusal());
        assertThrows(RefusalResponse.class, () -> service.found(null, ACCOUNT));
    }
}
