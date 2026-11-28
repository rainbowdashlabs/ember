/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.LendingReturnService.PieceCheck;
import dev.chojo.ember.feature.federation.service.LendingReturnService.ReturnState;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Taking gear back answers for every piece of the loan, marks the missing ones lost, and writes the
 * result into the messages; reminders go out by hand once overdue and on their own once.
 */
class LendingReturnServiceTest {
    private static final int STATION = 3;
    private static final int MEMBER = 11;
    private static final UUID OWN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDate END = LocalDate.of(2030, 6, 10);

    private LendingService lending;
    private LendingRepository repository;
    private InventoryService inventoryService;
    private StationRepository stations;
    private StationService stationService;
    private ItemCustodyService custody;
    private LendingReturnService service;

    private static LendingRequest request(LendingStatus status) {
        return new LendingRequest(
                41,
                PARTNER,
                OWN,
                status,
                END.minusDays(3),
                END,
                MEMBER,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "",
                UUID.randomUUID());
    }

    private static InventoryItem piece(int id, String name) {
        var piece = mock(InventoryItem.class);
        when(piece.id()).thenReturn(id);
        when(piece.name()).thenReturn(name);
        when(piece.internalId()).thenReturn("FG-" + id);
        return piece;
    }

    @BeforeEach
    void setUp() {
        lending = mock(LendingService.class);
        repository = mock(LendingRepository.class);
        var inventories = mock(InventoryRepository.class);
        inventoryService = mock(InventoryService.class);
        stations = mock(StationRepository.class);
        when(repository.findItemsByRequest(41))
                .thenReturn(List.of(new LendingRequestItem(5, 41, 1, null, 2, 2, null, "FuG")));
        when(repository.findAssignedItems(5)).thenReturn(List.of(7, 8));
        var first = piece(7, "FuG 1");
        var second = piece(8, "FuG 2");
        when(inventories.findItemById(7)).thenReturn(Optional.of(first));
        when(inventories.findItemById(8)).thenReturn(Optional.of(second));
        when(lending.stationName(any(), anyInt())).thenReturn("Süd");
        stationService = mock(StationService.class);
        custody = mock(ItemCustodyService.class);
        when(lending.markReturned(41, STATION)).thenReturn(true);
        service = new LendingReturnService(
                lending, repository, inventories, inventoryService, stations, stationService, custody);
    }

    /** The loan is returned, the missing piece marked lost afterwards, and the result written down. */
    @Test
    void aReturnMarksTheMissingLostAndWritesTheResult() {
        service.checkReturn(
                request(LendingStatus.LENT),
                STATION,
                MEMBER,
                "Nora",
                List.of(
                        new PieceCheck(7, ReturnState.DAMAGED, "Antenne ab"),
                        new PieceCheck(8, ReturnState.MISSING, null)));

        InOrder order = inOrder(lending, inventoryService);
        order.verify(lending).markReturned(41, STATION);
        order.verify(inventoryService).markLost(eq(8), contains("Süd"), eq(MEMBER));
        verify(lending)
                .sendMessage(
                        41,
                        STATION,
                        MEMBER,
                        "Nora",
                        "Rückgabe geprüft: 0 in Ordnung\n- FuG 1 (FG-7): beschädigt (Antenne ab)\n- FuG 2 (FG-8): fehlt");
        verify(inventoryService, never()).markLost(eq(7), anyString(), anyInt());
    }

    /** A check must answer for exactly the pieces of the loan, and only gear that is out is checked back. */
    @Test
    void aCheckAnswersForEveryPieceOfALoanThatIsOut() {
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.checkReturn(
                        request(LendingStatus.LENT),
                        STATION,
                        MEMBER,
                        "Nora",
                        List.of(new PieceCheck(7, ReturnState.OK, null))));
        assertEquals(FederationRefusal.LENDING_RETURN_PIECES_MISMATCH, refused.refusal());
        assertThrows(
                RefusalResponse.class,
                () -> service.checkReturn(
                        request(LendingStatus.LENT),
                        STATION,
                        MEMBER,
                        "Nora",
                        List.of(
                                new PieceCheck(7, ReturnState.OK, null),
                                new PieceCheck(8, ReturnState.OK, null),
                                new PieceCheck(9, ReturnState.OK, null))));
        assertEquals(
                FederationRefusal.LENDING_NOT_OUT,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.checkReturn(
                                        request(LendingStatus.APPROVED), STATION, MEMBER, "Nora", List.of()))
                        .refusal());
        verify(lending, never()).markReturned(anyInt(), anyInt());
    }

    /** A missing piece that cannot be marked lost stops the check before anything is taken back. */
    @Test
    void aMissingPieceThatCannotBeLostStopsTheCheck() {
        doThrow(InventoryRefusal.CUSTODY_PIECE_SET_ASIDE_FOR_A_LOAN.raise())
                .when(custody)
                .requireFreeToActOn(eq(8), any());

        assertThrows(
                RefusalResponse.class,
                () -> service.checkReturn(
                        request(LendingStatus.LENT),
                        STATION,
                        MEMBER,
                        "Nora",
                        List.of(
                                new PieceCheck(7, ReturnState.OK, null),
                                new PieceCheck(8, ReturnState.MISSING, null))));
        verify(lending, never()).markReturned(anyInt(), anyInt());
    }

    /** A check that loses the race against another one is refused instead of sent twice. */
    @Test
    void aReturnCheckedTwiceIsTakenBackOnce() {
        when(lending.markReturned(41, STATION)).thenReturn(false);

        assertEquals(
                FederationRefusal.LENDING_NOT_OUT,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.checkReturn(
                                        request(LendingStatus.LENT),
                                        STATION,
                                        MEMBER,
                                        "Nora",
                                        List.of(
                                                new PieceCheck(7, ReturnState.OK, null),
                                                new PieceCheck(8, ReturnState.OK, null))))
                        .refusal());
        verify(lending, never()).sendMessage(anyInt(), anyInt(), anyInt(), anyString(), anyString());
    }

    /** A reminder goes out by hand only once the last day has passed, and is noted. */
    @Test
    void aReminderNeedsTheLastDayPassed() {
        assertEquals(
                FederationRefusal.LENDING_NOT_OVERDUE,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.remind(request(LendingStatus.LENT), STATION, MEMBER, "Nora", END))
                        .refusal());

        service.remind(request(LendingStatus.LENT), STATION, MEMBER, "Nora", END.plusDays(1));
        verify(lending).sendMessage(eq(41), eq(STATION), eq(MEMBER), eq("Nora"), contains("10.06.2030"));
        verify(repository).markReminded(41);
    }

    /** The reminder on its own goes out in the lending station's name, for stations here only. */
    @Test
    void theReminderOnItsOwnGoesOutOnce() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(STATION);
        when(stations.findHereByUid(OWN)).thenReturn(Optional.of(station));
        when(repository.findOverdueUnreminded(END.plusDays(1))).thenReturn(List.of(request(LendingStatus.LENT)));

        service.sweep(END.plusDays(1));
        verify(lending, never()).sendStationMessage(anyInt(), anyInt(), anyString());

        when(stationService.isModuleEnabled(STATION, StationModule.INVENTORY_LENDING))
                .thenReturn(true);
        service.sweep(END.plusDays(1));
        verify(lending).sendStationMessage(eq(41), eq(STATION), contains("Erinnerung"));
        verify(repository).markReminded(41);
    }
}
