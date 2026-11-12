/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.WaitingListRefusal;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    private final ModuleAvailability modules = mock(ModuleAvailability.class);

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
        service = new PublicWaitingListService(lists, stations, invitations, modules);
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
                WaitingListRefusal.PUBLIC_WAITING_LISTS_SWITCHED_OFF,
                assertThrows(RefusalResponse.class, () -> service.stationIdFor("sued"))
                        .refusal());
        assertEquals(
                WaitingListRefusal.STATION_NOT_HERE_BEHIND_PUBLIC_LIST,
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
        var notHere = WaitingListRefusal.PUBLIC_WAITING_LIST_NOT_HERE;

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
                WaitingListRefusal.STATION_NOT_HERE_BEHIND_INVITATION,
                assertThrows(RefusalResponse.class, () -> service.invitationDetails(4, invitation))
                        .refusal());
    }

    /** A station whose waiting list is switched off, by itself or its cluster, shows no list in public. */
    @Test
    void aSwitchedOffWaitingListIsNotShownInPublic() {
        var open = station(3, true);
        when(stations.findByAddress("nord")).thenReturn(Optional.of(open));
        when(modules.disabledAt(3)).thenReturn(Set.of(StationModule.WAITING_LIST));

        assertEquals(
                WaitingListRefusal.PUBLIC_WAITING_LISTS_SWITCHED_OFF,
                assertThrows(RefusalResponse.class, () -> service.stationIdFor("nord"))
                        .refusal());
    }

    /** An invite link sent out earlier takes no sign-up while the station's waiting list is off. */
    @Test
    void anInviteTakesNoSignUpWhileTheListIsOff() {
        var invite = new WaitingListInvite(1, 8, "abc", 5, 0, null, Instant.now());
        var listOfStation = list(3, false);
        when(lists.findInviteByCode("abc")).thenReturn(Optional.of(invite));
        when(lists.findById(8)).thenReturn(Optional.of(listOfStation));

        assertDoesNotThrow(() -> service.requireInviteTakesSignUps("abc"));
        assertDoesNotThrow(() -> service.requireInviteTakesSignUps("unknown"));

        when(modules.disabledAt(3)).thenReturn(Set.of(StationModule.WAITING_LIST));
        assertEquals(
                WaitingListRefusal.PUBLIC_WAITING_LISTS_SWITCHED_OFF,
                assertThrows(RefusalResponse.class, () -> service.requireInviteTakesSignUps("abc"))
                        .refusal());
    }
}
