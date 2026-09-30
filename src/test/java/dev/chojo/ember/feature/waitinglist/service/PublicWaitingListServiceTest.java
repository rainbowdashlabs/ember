/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicWaitingListServiceTest {
    private WaitingListService lists;
    private StationRepository stations;
    private WaitlistInvitationMessage invitations;
    private PublicWaitingListService service;

    private static Station station(int id, boolean publicLists) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        when(station.publicWaitlistEnabled()).thenReturn(publicLists);
        return station;
    }

    private static WaitingList list(int stationId, boolean isPublic) {
        var list = mock(WaitingList.class);
        when(list.stationId()).thenReturn(stationId);
        when(list.isPublic()).thenReturn(isPublic);
        return list;
    }

    @BeforeEach
    void setup() {
        lists = mock(WaitingListService.class);
        stations = mock(StationRepository.class);
        invitations = mock(WaitlistInvitationMessage.class);
        service = new PublicWaitingListService(lists, stations, invitations);
        when(stations.findByAddress(anyString())).thenReturn(Optional.empty());
        when(lists.findById(anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void aStationIsReachedByItsAddressWhenItShowsItsListsInPublic() {
        var open = station(3, true);
        var closed = station(4, false);
        when(stations.findByAddress("nord")).thenReturn(Optional.of(open));
        when(stations.findByAddress("sued")).thenReturn(Optional.of(closed));

        assertEquals(3, service.stationIdFor("nord"));
        assertEquals(
                Refusal.PUBLIC_WAITING_LISTS_SWITCHED_OFF,
                assertThrows(RefusalResponse.class, () -> service.stationIdFor("sued"))
                        .refusal());
        assertEquals(
                Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_LIST,
                assertThrows(RefusalResponse.class, () -> service.stationIdFor("west"))
                        .refusal());
    }

    @Test
    void onlyAPublicListOfTheStationIsHandedOut() {
        var open = list(3, true);
        var hidden = list(3, false);
        var foreign = list(4, true);
        when(lists.findById(1)).thenReturn(Optional.of(open));
        when(lists.findById(2)).thenReturn(Optional.of(hidden));
        when(lists.findById(3)).thenReturn(Optional.of(foreign));
        var notHere = Refusal.PUBLIC_WAITING_LIST_NOT_HERE;

        assertSame(open, service.publicList(3, 1, notHere));
        for (int id : new int[] {2, 3, 4}) {
            assertEquals(
                    notHere,
                    assertThrows(RefusalResponse.class, () -> service.publicList(3, id, notHere))
                            .refusal());
        }
    }

    @Test
    void anInvitationIsDescribedAsItsMailDescribedIt() {
        var open = station(3, true);
        when(stations.findById(3)).thenReturn(Optional.of(open));
        var invitation = new WaitingListInvitation(7, LocalDate.of(2026, 10, 1), null);
        when(invitations.describe(open, invitation)).thenReturn(WaitlistInvitationDetails.NONE);

        assertSame(WaitlistInvitationDetails.NONE, service.invitationDetails(3, invitation));
        assertEquals(
                Refusal.STATION_NOT_HERE_BEHIND_INVITATION,
                assertThrows(RefusalResponse.class, () -> service.invitationDetails(4, invitation))
                        .refusal());
    }
}
