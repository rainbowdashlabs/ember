/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProduct;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProductDays;
import dev.chojo.ember.feature.federation.service.LendingCatalogService;
import dev.chojo.ember.feature.federation.service.LendingCatalogService.ProductDetail;
import dev.chojo.ember.feature.federation.service.LendingCatalogService.ProductList;
import dev.chojo.ember.feature.federation.service.LendingPictureService;
import dev.chojo.ember.feature.federation.service.LendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shop's three questions as the browser asks them: who may ask, how the days are read, and that
 * a day or a piece that is not one is refused before any partner is asked.
 */
class FederatedLendingRoutesTest {
    private static final int STATION = 3;
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private LendingCatalogService catalog;
    private RouteHarness harness;

    @BeforeEach
    void setUp() {
        catalog = mock(LendingCatalogService.class);
        when(catalog.findProducts(anyInt(), any(), any(), any())).thenReturn(new ProductList(List.of(), null));
        when(catalog.findProduct(anyInt(), any(), anyInt(), any(), any()))
                .thenReturn(new ProductDetail(
                        PARTNER,
                        "FF Nord",
                        null,
                        new RemoteProduct(7, "Funk", "", null, null, null, null, List.of(), List.of())));
        when(catalog.findProductDays(anyInt(), any(), anyInt(), any(), any()))
                .thenReturn(new RemoteProductDays(List.of()));
        harness = RouteHarness.serving(
                new FederatedLendingRoutes(mock(LendingService.class), mock(LendingPictureService.class), catalog));
    }

    private int get(String path, StationPermission... permissions) {
        return harness.request(
                        client -> client.get(PREFIX + path, harness.as(TestSessions.member(STATION, permissions))))
                .code();
    }

    /** Only whoever may ask for gear reaches the shop. */
    @Test
    void theShopNeedsTheRightToAskForGear() {
        assertEquals(403, get("/federated/lending/products"));
        assertEquals(403, get("/federated/lending/products/%s/7".formatted(PARTNER)));
        assertEquals(403, get("/federated/lending/products/%s/7/days?month=2030-06".formatted(PARTNER)));
        assertEquals(200, get("/federated/lending/products", StationPermission.INVENTORY_LENDING_REQUEST));
    }

    /** One day given means that one day; the search goes along as typed. */
    @Test
    void aSingleDayIsTheWholeWindow() {
        get("/federated/lending/products?q=funk&from=2030-06-12", StationPermission.INVENTORY_LENDING_REQUEST);

        LocalDate day = LocalDate.of(2030, 6, 12);
        verify(catalog).findProducts(STATION, "funk", day, day);
    }

    /** The product page and its calendar ask the partner named in the address. */
    @Test
    void theProductAndItsCalendarAskThePartner() {
        var detail = harness.request(client -> client.get(
                PREFIX + "/federated/lending/products/%s/7?from=2030-06-12&to=2030-06-14".formatted(PARTNER),
                harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_LENDING_REQUEST))));
        assertEquals("Funk", json(detail).path("product").path("name").asString());
        verify(catalog).findProduct(STATION, PARTNER, 7, LocalDate.of(2030, 6, 12), LocalDate.of(2030, 6, 14));

        get(
                "/federated/lending/products/%s/7/days?month=2030-06&itemId=14".formatted(PARTNER),
                StationPermission.INVENTORY_LENDING_REQUEST);
        verify(catalog).findProductDays(STATION, PARTNER, 7, "2030-06", 14);
    }

    /** A day that is not one, or a piece that is not a number, is refused by name and asks nobody. */
    @Test
    void whatIsNotADayOrAPieceIsRefused() {
        var day = harness.request(client -> client.get(
                PREFIX + "/federated/lending/products?from=morgen",
                harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_LENDING_REQUEST))));
        assertEquals(FederationRefusal.LENDING_DAY_NOT_A_DAY, refusalOf(day));
        var piece = harness.request(client -> client.get(
                PREFIX + "/federated/lending/products/%s/7/days?month=2030-06&itemId=abc".formatted(PARTNER),
                harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_LENDING_REQUEST))));
        assertEquals(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED, refusalOf(piece));
        verify(catalog, never()).findProductDays(anyInt(), any(), anyInt(), any(), any());
    }
}
