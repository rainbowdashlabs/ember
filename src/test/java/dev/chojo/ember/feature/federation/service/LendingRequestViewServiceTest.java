/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.entity.LentOutItem;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which requests a station reads and acts on, and how a request, its lines and its messages are
 * named for the screens.
 */
class LendingRequestViewServiceTest {
    private static final int STATION = 3;
    private static final UUID HERE = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000098");

    private LendingService lending;
    private LendingRepository lendingRepository;
    private StationRepository stations;
    private InventoryRepository inventories;
    private StationMemberRepository members;
    private AccountRepository accounts;
    private EventCrudService events;
    private LendingRequestViewService service;

    private static LendingRequest request(int id, UUID asking, UUID owning, LendingStatus status, LocalDate to) {
        return new LendingRequest(
                id,
                asking,
                owning,
                status,
                LocalDate.of(2020, 1, 1),
                to,
                11,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "");
    }

    @BeforeEach
    void setup() {
        lending = mock(LendingService.class);
        lendingRepository = mock(LendingRepository.class);
        stations = mock(StationRepository.class);
        inventories = mock(InventoryRepository.class);
        members = mock(StationMemberRepository.class);
        accounts = mock(AccountRepository.class);
        events = mock(EventCrudService.class);
        service = new LendingRequestViewService(
                lending, lendingRepository, stations, inventories, members, accounts, events);
        when(stations.resolveUid(STATION)).thenReturn(HERE);
        when(stations.requireUid(STATION)).thenReturn(HERE);
        when(lending.stationName(any(), anyInt())).thenAnswer(call -> "Station " + call.getArgument(0));
    }

    @Test
    void aManagerReadsEveryRequestAndAnAskerOnlyTheirOwn() {
        var asked = request(1, HERE, PARTNER, LendingStatus.REQUESTED, null);
        var lent = request(2, PARTNER, HERE, LendingStatus.REQUESTED, null);
        when(lending.findRequestsByStation(STATION)).thenReturn(List.of(asked, lent));

        assertEquals(2, service.requestsFor(STATION, true).size());
        var own = service.requestsFor(STATION, false);
        assertEquals(1, own.size());
        assertSame(asked, own.getFirst().request());
    }

    @Test
    void eitherPartyReadsARequestAndNobodyElse() {
        when(lending.findRequest(1)).thenReturn(Optional.of(request(1, HERE, PARTNER, LendingStatus.LENT, null)));
        when(lending.findRequest(2)).thenReturn(Optional.of(request(2, PARTNER, HERE, LendingStatus.LENT, null)));
        when(lending.findRequest(3)).thenReturn(Optional.of(request(3, PARTNER, STRANGER, LendingStatus.LENT, null)));

        assertEquals(1, service.requireParty(1, STATION).id());
        assertEquals(2, service.requireParty(2, STATION).id());
        assertEquals(
                FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS,
                refusal(() -> service.requireParty(3, STATION)));
        assertEquals(
                FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS,
                refusal(() -> service.requireParty(4, STATION)));
    }

    @Test
    void onlyTheOwnerActsOnARequest() {
        when(lending.findRequest(1)).thenReturn(Optional.of(request(1, HERE, PARTNER, LendingStatus.LENT, null)));
        when(lending.findRequest(2)).thenReturn(Optional.of(request(2, PARTNER, HERE, LendingStatus.LENT, null)));

        assertEquals(2, service.requireOwner(2, STATION).id());
        assertEquals(FederationRefusal.LENDING_NOT_THE_OWNING_STATION, refusal(() -> service.requireOwner(1, STATION)));
        assertEquals(
                FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS,
                refusal(() -> service.requireOwner(4, STATION)));
    }

    @Test
    void aStationRecognisesItself() {
        assertTrue(service.isOwnStation(STATION, HERE));
        assertFalse(service.isOwnStation(STATION, PARTNER));
    }

    @Test
    void theOccasionIsTheNameOfTheStationsOwnAppointment() {
        var own = mock(StationEvent.class);
        when(own.stationId()).thenReturn(STATION);
        when(own.name()).thenReturn("Zeltlager");
        var foreign = mock(StationEvent.class);
        when(foreign.stationId()).thenReturn(8);
        when(events.findById(4)).thenReturn(Optional.of(own));
        when(events.findById(5)).thenReturn(Optional.of(foreign));

        assertEquals("Zeltlager", service.occasionOf(STATION, 4));
        assertEquals("", service.occasionOf(STATION, 5));
        assertEquals("", service.occasionOf(STATION, null));
    }

    @Test
    void aRequestIsDescribedWithItsPartiesAndWhetherItIsOverdue() {
        when(lending.buildItemSummary(anyInt())).thenReturn("2 Zelte");
        var past = LocalDate.of(2020, 1, 3);

        var overdue = service.describe(request(1, PARTNER, HERE, LendingStatus.LENT, past), STATION);
        assertTrue(overdue.overdue());
        assertTrue(overdue.isOwner());
        assertEquals("Station " + PARTNER, overdue.requestingStationName());
        assertEquals("2 Zelte", overdue.itemSummary());

        assertTrue(service.describe(request(2, PARTNER, HERE, LendingStatus.APPROVED, past), STATION)
                .overdue());
        assertFalse(service.describe(request(3, HERE, PARTNER, LendingStatus.RETURNED, past), STATION)
                .overdue());
        assertFalse(service.describe(request(4, HERE, PARTNER, LendingStatus.LENT, null), STATION)
                .overdue());
        assertFalse(service.describe(request(5, HERE, PARTNER, LendingStatus.LENT, LocalDate.of(2999, 1, 1)), STATION)
                .overdue());
    }

    @Test
    void theLinesCarryTheNameOfTheirInventory() {
        var line = new LendingRequestItem(1, 5, 2, null, null, 1, null, null);
        when(lending.findRequestItems(5)).thenReturn(List.of(line));
        when(lending.inventoryName(line)).thenReturn("Zelte");

        assertEquals("Zelte", service.describeItems(5).getFirst().inventoryName());
    }

    @Test
    void theAssignablePiecesAreListedPerLineWithTheNamedOnePreselected() {
        var tents = mock(Inventory.class);
        when(tents.name()).thenReturn("Zelte");
        when(inventories.findById(2)).thenReturn(Optional.of(tents));
        when(inventories.findSizes(2)).thenReturn(List.of(new InventorySize(30, 2, "groß", 0, null)));
        var sized = mock(InventoryItem.class);
        when(sized.id()).thenReturn(9);
        when(sized.sizeId()).thenReturn(30);
        var plain = mock(InventoryItem.class);
        when(plain.id()).thenReturn(10);
        when(plain.sizeId()).thenReturn(null);
        when(lending.findAssignableItems(STATION, 2)).thenReturn(List.of(sized, plain));
        when(lending.findRequestItems(5))
                .thenReturn(List.of(
                        new LendingRequestItem(1, 5, 2, 9, null, 1, null, null),
                        new LendingRequestItem(2, 5, null, null, 4, 1, null, null),
                        new LendingRequestItem(3, 5, 6, null, null, 1, null, null)));

        var available = service.availableItems(5, STATION);

        assertEquals(2, available.size());
        assertEquals("groß", available.get(0).sizeName());
        assertTrue(available.get(0).preselected());
        assertNull(available.get(1).sizeName());
        assertFalse(available.get(1).preselected());
        assertEquals("Zelte", available.get(1).inventoryName());
    }

    @Test
    void aMessageWrittenHereNamesItsWriter() {
        var station = mock(Station.class);
        when(stations.findByUid(HERE)).thenReturn(Optional.of(station));
        when(members.findById(21))
                .thenReturn(Optional.of(new StationMember(
                        21, STATION, null, null, false, null, "Mara", StationUserType.MEMBER, LocalDate.EPOCH)));
        when(members.findById(22))
                .thenReturn(Optional.of(new StationMember(
                        22, STATION, null, 1, false, null, " ", StationUserType.MEMBER, LocalDate.EPOCH)));
        when(accounts.findById(1)).thenReturn(Optional.of(TestSessions.account()));
        when(members.findById(23))
                .thenReturn(Optional.of(new StationMember(
                        23, STATION, null, null, false, null, null, StationUserType.MEMBER, LocalDate.EPOCH)));

        assertEquals("Mara", service.describe(message(21, HERE, false), STATION).senderName());
        assertEquals(
                NameParts.of(TestSessions.account()).called(),
                service.describe(message(22, HERE, false), STATION).senderName());
        assertNull(service.describe(message(23, HERE, false), STATION).senderName());
        assertNull(service.describe(message(24, HERE, false), STATION).senderName());
        assertEquals(
                "Station " + HERE,
                service.describe(message(21, HERE, false), STATION).senderStationName());
    }

    @Test
    void aMessageFromElsewhereOrFromTheSystemNamesNoWriter() {
        assertNull(service.describe(message(21, PARTNER, false), STATION).senderName());
        when(stations.findByUid(HERE)).thenReturn(Optional.of(mock(Station.class)));
        assertNull(service.describe(message(21, HERE, true), STATION).senderName());
        assertNull(service.describe(message(null, HERE, false), STATION).senderName());
    }

    @Test
    void whatIsLentOutIsAskedForTheStationsOwnGear() {
        var out = new LentOutItem(1, 5, 9, 1, 9, "LENT", null, null, "Wache Süd");
        when(lendingRepository.findLentOutByInventory(2, HERE)).thenReturn(List.of(out));

        assertEquals(List.of(out), service.lentOut(2, STATION));
    }

    private static LendingMessage message(Integer memberId, UUID station, boolean system) {
        return new LendingMessage(1, 5, station, memberId, "Hallo", system, Instant.EPOCH);
    }

    private static Refusal refusal(Runnable call) {
        return assertThrows(RefusalResponse.class, call::run).refusal();
    }
}
