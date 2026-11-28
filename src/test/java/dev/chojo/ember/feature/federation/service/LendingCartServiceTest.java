/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.LendingCart;
import dev.chojo.ember.feature.federation.entity.LendingCartLine;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingCartRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository.OfferedLine;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteKind;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemotePiece;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProduct;
import dev.chojo.ember.feature.federation.service.LendingCartService.CartLineCheck;
import dev.chojo.ember.feature.federation.service.LendingCartService.CartLineState;
import dev.chojo.ember.feature.federation.service.LendingCartService.NewCartLine;
import dev.chojo.ember.feature.federation.service.LendingCatalogService.ProductDetail;
import dev.chojo.ember.feature.federation.service.LendingService.RequestLine;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cart's rules over its repository and the partners: what it accepts, how a line is found free,
 * how sending splits by station and keeps what failed, and what borrowing again leaves out.
 */
class LendingCartServiceTest {
    private static final int STATION = 3;
    private static final int MEMBER = 11;
    private static final UUID OWN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID NORTH = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SOUTH = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final LocalDate DAY = LocalDate.of(2030, 6, 12);

    private LendingCartRepository carts;
    private LendingRepository requests;
    private LendingService lending;
    private LendingCatalogService catalog;
    private LendingCartService service;

    @BeforeEach
    void setUp() {
        carts = mock(LendingCartRepository.class);
        requests = mock(LendingRepository.class);
        lending = mock(LendingService.class);
        catalog = mock(LendingCatalogService.class);
        var stations = mock(StationRepository.class);
        when(stations.requireUid(STATION)).thenReturn(OWN);
        for (UUID lender : List.of(NORTH, SOUTH)) {
            var partner = mock(FederationPartner.class);
            when(partner.partnerStationId()).thenReturn(lender);
            when(lending.lendingPartnerOf(STATION, lender)).thenReturn(partner);
        }
        when(carts.claimSending(STATION)).thenReturn(true);
        when(lending.stationName(any(), anyInt()))
                .thenAnswer(call -> call.getArgument(0).equals(NORTH) ? "Nord" : "Süd");
        service = new LendingCartService(
                carts,
                requests,
                lending,
                catalog,
                stations,
                new FederationFanout(Runnable::run, Duration.ofSeconds(5)));
    }

    private static RemoteProduct product(int inventoryId, List<RemoteKind> kinds, List<RemotePiece> pieces) {
        return new RemoteProduct(inventoryId, "Funk", "", "inv-photo", null, null, null, kinds, pieces);
    }

    private static RemoteKind kind(Integer artId, int free) {
        return new RemoteKind(artId, artId == null ? "" : "FuG " + artId, "", null, null, null, free);
    }

    private static LendingCartLine line(
            int id, UUID station, int inventoryId, Integer artId, Integer itemId, int quantity) {
        return new LendingCartLine(id, station, inventoryId, artId, itemId, quantity, "Funk", "", null);
    }

    private void offers(UUID station, RemoteProduct product) {
        when(catalog.findProduct(eq(STATION), eq(station), eq(product.inventoryId()), any(), any()))
                .thenReturn(new ProductDetail(station, "x", null, product));
    }

    /** A line counts at least one, never a kind and a piece at once, and a piece always once. */
    @Test
    void aLineCountsSomethingAndAPieceOnce() {
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.add(STATION, new NewCartLine(NORTH, 7, null, null, 0, "", "", null)));
        assertEquals(FederationRefusal.LENDING_CART_LINE_INVALID, refused.refusal());
        assertThrows(
                RefusalResponse.class, () -> service.add(STATION, new NewCartLine(NORTH, 7, 3, 14, 1, "", "", null)));

        service.add(STATION, new NewCartLine(NORTH, 9, null, 14, 4, " Spiele ", "Catan", null));
        verify(carts).addLine(STATION, NORTH, 9, null, 14, 1, "Spiele", "Catan", null);
    }

    /** Gear goes in the cart only from a station this one lends with. */
    @Test
    void onlyAPartnerThatLendsFillsTheCart() {
        when(lending.lendingPartnerOf(STATION, SOUTH)).thenReturn(null);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.add(STATION, new NewCartLine(SOUTH, 7, null, null, 1, "", "", null)));
        assertEquals(FederationRefusal.LENDING_PARTNER_DOES_NOT_LEND, refused.refusal());
    }

    /** A count is free in full, short, or gone with its kind; a piece is free or not. */
    @Test
    void aLineIsFreeShortOrGone() {
        var product =
                product(7, List.of(kind(null, 1), kind(3, 2)), List.of(new RemotePiece(14, "Catan", "", null, false)));

        assertEquals(
                CartLineState.FREE,
                LendingCartService.checkLine(line(1, NORTH, 7, 3, null, 2), product)
                        .state());
        var short_ = LendingCartService.checkLine(line(2, NORTH, 7, 3, null, 3), product);
        assertEquals(CartLineState.SHORT, short_.state());
        assertEquals(2, short_.free());
        assertEquals(
                CartLineState.FREE,
                LendingCartService.checkLine(line(3, NORTH, 7, null, null, 1), product)
                        .state());
        assertEquals(
                CartLineState.GONE,
                LendingCartService.checkLine(line(4, NORTH, 7, 5, null, 1), product)
                        .state());
        assertEquals(
                CartLineState.SHORT,
                LendingCartService.checkLine(line(5, NORTH, 7, null, 14, 1), product)
                        .state());
        assertEquals(
                CartLineState.GONE,
                LendingCartService.checkLine(line(6, NORTH, 7, 3, null, 1), null)
                        .state());
    }

    /** The check asks each product once with the cart's days, and a product no longer offered is gone. */
    @Test
    void theCheckAsksEachProductOnce() {
        when(carts.find(STATION)).thenReturn(Optional.of(new LendingCart(STATION, DAY, null, "", "")));
        when(carts.lines(STATION))
                .thenReturn(List.of(
                        line(1, NORTH, 7, 3, null, 1),
                        line(2, NORTH, 7, null, null, 1),
                        line(3, SOUTH, 8, null, null, 1)));
        offers(NORTH, product(7, List.of(kind(null, 1), kind(3, 1)), List.of()));
        when(catalog.findProduct(eq(STATION), eq(SOUTH), eq(8), any(), any()))
                .thenThrow(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED.raise());

        var checks = service.check(STATION);

        assertEquals(
                List.of(CartLineState.FREE, CartLineState.FREE, CartLineState.GONE),
                checks.stream().map(c -> c.state()).toList());
        verify(catalog).findProduct(STATION, NORTH, 7, DAY, null);
    }

    /** Sending opens one request per station with the note as first message and keeps what failed. */
    @Test
    void sendingSplitsByStationAndKeepsWhatFailed() {
        when(carts.find(STATION))
                .thenReturn(Optional.of(new LendingCart(STATION, DAY, null, "Fest", "Freitag abholen")));
        when(carts.lines(STATION)).thenReturn(List.of(line(1, NORTH, 7, 3, null, 1), line(2, SOUTH, 8, null, null, 1)));
        offers(NORTH, product(7, List.of(kind(3, 1)), List.of()));
        offers(SOUTH, product(8, List.of(kind(null, 1)), List.of()));
        var request = new LendingRequest(
                41,
                OWN,
                NORTH,
                LendingStatus.REQUESTED,
                DAY,
                DAY,
                MEMBER,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "Fest",
                UUID.randomUUID());
        when(lending.createRequest(
                        eq(STATION), eq(NORTH), eq(DAY), eq(DAY), eq(MEMBER), isNull(), isNull(), eq("Fest"), any()))
                .thenReturn(request);
        when(lending.createRequest(eq(STATION), eq(SOUTH), any(), any(), anyInt(), any(), any(), anyString(), any()))
                .thenThrow(new IllegalStateException("unreachable"));

        var result = service.checkout(STATION, MEMBER, "Nora");

        assertEquals(List.of(request), result.sent());
        assertEquals(List.of("Süd"), result.failed());
        verify(lending).sendMessage(41, STATION, MEMBER, "Nora", "Freitag abholen");
        verify(carts).removeLines(STATION, List.of(1));
        verify(carts, never()).removeLines(STATION, List.of(2));
        verify(carts).clearIfEmpty(STATION);
        verify(carts).releaseSending(STATION);
        verify(lending)
                .createRequest(
                        eq(STATION),
                        eq(NORTH),
                        eq(DAY),
                        eq(DAY),
                        eq(MEMBER),
                        isNull(),
                        isNull(),
                        eq("Fest"),
                        eq(List.of(new RequestLine(7, null, 3, 1, null))));
    }

    /** A request once opened leaves the cart even where its note did not follow, and is named. */
    @Test
    void aSentRequestLeavesTheCartWithoutItsNote() {
        when(carts.find(STATION)).thenReturn(Optional.of(new LendingCart(STATION, DAY, null, "", "Freitag")));
        when(carts.lines(STATION)).thenReturn(List.of(line(1, NORTH, 9, null, 14, 1)));
        offers(NORTH, product(9, List.of(), List.of(new RemotePiece(14, "Catan", "", null, true))));
        var request = new LendingRequest(
                41,
                OWN,
                NORTH,
                LendingStatus.REQUESTED,
                DAY,
                DAY,
                MEMBER,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "",
                UUID.randomUUID());
        when(lending.createRequest(anyInt(), any(UUID.class), any(), any(), anyInt(), any(), any(), anyString(), any()))
                .thenReturn(request);
        when(lending.sendMessage(anyInt(), anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("database gone"));

        var result = service.checkout(STATION, MEMBER, "Nora");

        assertEquals(List.of(request), result.sent());
        assertEquals(List.of("Nord"), result.notesMissing());
        verify(carts).removeLines(STATION, List.of(1));
        verify(lending)
                .createRequest(
                        anyInt(),
                        any(UUID.class),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        anyString(),
                        eq(List.of(new RequestLine(9, 14, null, 1, null))));
    }

    /** The lines are read once the cart is claimed, so a sending never works from an older list. */
    @Test
    void theLinesAreReadOnceClaimed() {
        when(carts.find(STATION)).thenReturn(Optional.of(new LendingCart(STATION, DAY, null, "", "")));
        when(carts.lines(STATION)).thenReturn(List.of());

        assertEquals(
                FederationRefusal.LENDING_CART_EMPTY,
                assertThrows(RefusalResponse.class, () -> service.checkout(STATION, MEMBER, "Nora"))
                        .refusal());
        var order = inOrder(carts);
        order.verify(carts).claimSending(STATION);
        order.verify(carts).lines(STATION);
        order.verify(carts).releaseSending(STATION);
    }

    /** A cart already being sent is not sent a second time. */
    @Test
    void aCartIsSentOnceAtATime() {
        when(carts.lines(STATION)).thenReturn(List.of(line(1, NORTH, 7, 3, null, 1)));
        when(carts.claimSending(STATION)).thenReturn(false);

        assertEquals(
                FederationRefusal.LENDING_CART_BEING_SENT,
                assertThrows(RefusalResponse.class, () -> service.checkout(STATION, MEMBER, "Nora"))
                        .refusal());
        verify(lending, never())
                .createRequest(anyInt(), any(UUID.class), any(), any(), anyInt(), any(), any(), anyString(), any());
    }

    /** A station that cannot be reached is asked once, and a station that stopped lending is gone. */
    @Test
    void anUnreachableStationIsAskedOnce() {
        when(carts.find(STATION)).thenReturn(Optional.of(new LendingCart(STATION, null, null, "", "")));
        when(carts.lines(STATION))
                .thenReturn(List.of(
                        line(1, NORTH, 7, null, null, 1),
                        line(2, NORTH, 8, null, null, 1),
                        line(3, SOUTH, 9, null, null, 1)));
        when(catalog.findProduct(eq(STATION), eq(NORTH), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("no answer"));
        when(lending.lendingPartnerOf(STATION, SOUTH)).thenReturn(null);

        var states = service.check(STATION).stream().map(CartLineCheck::state).toList();

        assertEquals(List.of(CartLineState.UNREACHABLE, CartLineState.UNREACHABLE, CartLineState.GONE), states);
        verify(catalog).findProduct(eq(STATION), eq(NORTH), anyInt(), any(), any());
    }

    /** A line counts at most 999, and names the station it is borrowed from. */
    @Test
    void aLineHasABoundAndAStation() {
        assertThrows(
                RefusalResponse.class,
                () -> service.add(STATION, new NewCartLine(NORTH, 7, null, null, 1000, "", "", null)));
        assertThrows(
                RefusalResponse.class,
                () -> service.add(STATION, new NewCartLine(null, 7, null, null, 1, "", "", null)));
        assertThrows(RefusalResponse.class, () -> service.setQuantity(STATION, 1, 1000));
    }

    /** Nothing is sent while a line is short, nor without a first day. */
    @Test
    void nothingIsSentShortOrWithoutADay() {
        when(carts.lines(STATION)).thenReturn(List.of(line(1, NORTH, 7, 3, null, 2)));
        offers(NORTH, product(7, List.of(kind(3, 1)), List.of()));
        when(carts.find(STATION)).thenReturn(Optional.of(new LendingCart(STATION, null, null, "", "")));
        assertEquals(
                FederationRefusal.LENDING_FIRST_DAY_MISSING,
                assertThrows(RefusalResponse.class, () -> service.checkout(STATION, MEMBER, "Nora"))
                        .refusal());

        when(carts.find(STATION)).thenReturn(Optional.of(new LendingCart(STATION, DAY, null, "", "")));
        assertEquals(
                FederationRefusal.LENDING_CART_NOT_FREE,
                assertThrows(RefusalResponse.class, () -> service.checkout(STATION, MEMBER, "Nora"))
                        .refusal());
        verify(lending, never())
                .createRequest(anyInt(), any(UUID.class), any(), any(), anyInt(), any(), any(), anyString(), any());
    }

    /** Borrowing again puts back what is still offered, names what is not, and needs an answered request. */
    @Test
    void borrowingAgainLeavesOutWhatIsGone() {
        var answered = new LendingRequest(
                41,
                OWN,
                NORTH,
                LendingStatus.RETURNED,
                DAY,
                DAY,
                MEMBER,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "",
                UUID.randomUUID());
        when(requests.findRequestById(41)).thenReturn(Optional.of(answered));
        when(requests.findOfferedLines(41))
                .thenReturn(List.of(
                        new OfferedLine(7, 3, null, 2, "FuG 3"),
                        new OfferedLine(7, 5, null, 1, "FuG 5"),
                        new OfferedLine(null, null, null, 1, "Alt")));
        offers(NORTH, product(7, List.of(kind(3, 0)), List.of()));

        var result = service.borrowAgain(STATION, 41);

        assertEquals(1, result.added());
        assertEquals(List.of("FuG 5", "Alt"), result.skipped());
        verify(carts).addLine(STATION, NORTH, 7, 3, null, 2, "Funk", "FuG 3", "inv-photo");

        when(requests.findRequestById(42))
                .thenReturn(Optional.of(new LendingRequest(
                        42,
                        OWN,
                        NORTH,
                        LendingStatus.REQUESTED,
                        DAY,
                        DAY,
                        MEMBER,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        null,
                        null,
                        "",
                        UUID.randomUUID())));
        assertEquals(
                FederationRefusal.LENDING_NOT_BORROWED_AGAIN,
                assertThrows(RefusalResponse.class, () -> service.borrowAgain(STATION, 42))
                        .refusal());
        when(requests.findRequestById(43))
                .thenReturn(Optional.of(new LendingRequest(
                        43,
                        NORTH,
                        OWN,
                        LendingStatus.RETURNED,
                        DAY,
                        DAY,
                        MEMBER,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        null,
                        null,
                        "",
                        UUID.randomUUID())));
        assertEquals(
                FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS,
                assertThrows(RefusalResponse.class, () -> service.borrowAgain(STATION, 43))
                        .refusal());
    }

    /** The last day never comes before the first, and a last day alone is a single day. */
    @Test
    void theDaysRunForward() {
        assertEquals(
                FederationRefusal.LENDING_WINDOW_BACKWARDS,
                assertThrows(RefusalResponse.class, () -> service.setDetails(STATION, DAY, DAY.minusDays(1), "", ""))
                        .refusal());

        service.setDetails(STATION, null, DAY, " Fest ", "");
        verify(carts).setDetails(STATION, DAY, DAY, "Fest", "");
    }
}
