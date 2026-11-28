/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.service.LendingOfferService.OfferKind;
import dev.chojo.ember.feature.federation.service.LendingOfferService.OfferProduct;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The calendar draws the requests for the station's own gear inside the window as bars on the rows
 * they draw from, and the block periods touching it, a piece's block on its product's row.
 */
class LendingCalendarServiceTest {
    private static final int STATION = 3;
    private static final UUID OWN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDate FROM = LocalDate.of(2030, 6, 1);
    private static final LocalDate TO = LocalDate.of(2030, 6, 28);

    private LendingService lending;
    private InventoryRepository inventories;
    private LendingCalendarService service;

    private static LendingRequest request(
            int id, UUID asking, UUID owning, LendingStatus status, LocalDate from, LocalDate to) {
        return new LendingRequest(
                id,
                asking,
                owning,
                status,
                from,
                to,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "",
                UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        lending = mock(LendingService.class);
        inventories = mock(InventoryRepository.class);
        var offer = mock(LendingOfferService.class);
        var stations = mock(StationRepository.class);
        when(stations.requireUid(STATION)).thenReturn(OWN);
        when(offer.offer(STATION, null))
                .thenReturn(List.of(new OfferProduct(
                        1,
                        "Funk",
                        "",
                        null,
                        null,
                        null,
                        true,
                        4,
                        null,
                        true,
                        null,
                        List.of(new OfferKind(10, "FuG", "", null, 4, null, null)),
                        List.of())));
        when(lending.stationName(any(), anyInt())).thenReturn("Nord");
        var piece = mock(InventoryItem.class);
        when(piece.inventoryId()).thenReturn(1);
        when(piece.artId()).thenReturn(10);
        when(inventories.findItemById(77)).thenReturn(Optional.of(piece));
        service = new LendingCalendarService(lending, offer, inventories, stations);
    }

    /** Only own gear inside the window becomes bars; a piece line takes its kind; out past its last day is overdue. */
    @Test
    void ownRequestsInsideTheWindowBecomeBars() {
        when(lending.findRequestsByStation(STATION))
                .thenReturn(List.of(
                        request(
                                1,
                                PARTNER,
                                OWN,
                                LendingStatus.LENT,
                                LocalDate.of(2030, 5, 28),
                                LocalDate.of(2030, 6, 3)),
                        request(
                                2,
                                PARTNER,
                                OWN,
                                LendingStatus.REQUESTED,
                                LocalDate.of(2030, 7, 2),
                                LocalDate.of(2030, 7, 3)),
                        request(
                                3,
                                OWN,
                                PARTNER,
                                LendingStatus.APPROVED,
                                LocalDate.of(2030, 6, 5),
                                LocalDate.of(2030, 6, 6)),
                        request(
                                4,
                                PARTNER,
                                OWN,
                                LendingStatus.DECLINED,
                                LocalDate.of(2030, 6, 5),
                                LocalDate.of(2030, 6, 6)),
                        request(
                                5,
                                PARTNER,
                                OWN,
                                LendingStatus.APPROVED,
                                LocalDate.of(2030, 5, 20),
                                LocalDate.of(2030, 6, 1))));
        when(lending.findRequestItems(5))
                .thenReturn(List.of(new LendingRequestItem(5, 5, 1, null, 10, 1, null, "FuG")));
        when(lending.findRequestItems(1))
                .thenReturn(List.of(
                        new LendingRequestItem(1, 1, 1, 77, null, 1, null, "FuG 1"),
                        new LendingRequestItem(2, 1, null, null, null, 2, null, "fremd")));
        when(lending.findBlocks(STATION))
                .thenReturn(List.of(
                        new InventoryBlock(
                                5, STATION, null, 77, LocalDate.of(2030, 6, 10), LocalDate.of(2030, 6, 12), "Wartung"),
                        new InventoryBlock(
                                6, STATION, null, null, LocalDate.of(2030, 8, 1), LocalDate.of(2030, 8, 2), "Fest")));

        var calendar = service.calendar(STATION, FROM, TO, LocalDate.of(2030, 6, 5));

        assertEquals(1, calendar.rows().size());
        assertEquals(
                List.of(1, 5), calendar.bars().stream().map(b -> b.requestId()).toList());
        assertEquals(false, calendar.bars().get(1).overdue());
        var bar = calendar.bars().getFirst();
        assertEquals(10, bar.artId());
        assertTrue(bar.overdue());
        assertEquals("Nord", bar.partner());
        assertEquals(List.of(5), calendar.blocks().stream().map(b -> b.id()).toList());
        assertEquals(1, calendar.blocks().getFirst().inventoryId());
        assertEquals(10, calendar.blocks().getFirst().artId());
    }

    /** A window that runs backwards or past a quarter of a year is refused. */
    @Test
    void theWindowRunsForwardForAtMostAQuarter() {
        assertEquals(
                FederationRefusal.LENDING_WINDOW_BACKWARDS,
                assertThrows(RefusalResponse.class, () -> service.calendar(STATION, TO, FROM, FROM))
                        .refusal());
        when(lending.findRequestsByStation(STATION)).thenReturn(List.of());
        when(lending.findBlocks(STATION)).thenReturn(List.of());
        assertEquals(
                1,
                service.calendar(STATION, FROM, FROM.plusDays(91), FROM).rows().size());
        assertEquals(
                FederationRefusal.LENDING_CALENDAR_TOO_LONG,
                assertThrows(RefusalResponse.class, () -> service.calendar(STATION, FROM, FROM.plusDays(92), FROM))
                        .refusal());
    }

    /** A block on a piece that is gone keeps the piece, draws on no product row, and asks for the piece once. */
    @Test
    void aBlockOnAGonePieceStaysAPieceBlock() {
        when(lending.findRequestsByStation(STATION)).thenReturn(List.of());
        when(inventories.findItemById(88)).thenReturn(Optional.empty());
        when(lending.findBlocks(STATION))
                .thenReturn(List.of(
                        new InventoryBlock(
                                7, STATION, null, 88, LocalDate.of(2030, 6, 10), LocalDate.of(2030, 6, 12), "Weg"),
                        new InventoryBlock(
                                8, STATION, null, 88, LocalDate.of(2030, 6, 14), LocalDate.of(2030, 6, 15), "Weg")));

        var block = service.calendar(STATION, FROM, TO, FROM).blocks().getFirst();

        assertNull(block.inventoryId());
        assertNull(block.artId());
        assertEquals(88, block.itemId());
        verify(inventories, times(1)).findItemById(88);
    }
}
