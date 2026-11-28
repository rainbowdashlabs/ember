/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.LendingCart;
import dev.chojo.ember.feature.federation.service.LendingCartService;
import dev.chojo.ember.feature.federation.service.LendingCartService.CheckoutResult;
import dev.chojo.ember.feature.federation.service.LendingCartService.NewCartLine;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The cart as the browser reaches it: only whoever may ask for gear, and the body reaches the service. */
class LendingCartRoutesTest {
    private static final int STATION = 3;
    private static final UUID PARTNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private LendingCartService service;
    private RouteHarness harness;

    @BeforeEach
    void setUp() {
        service = mock(LendingCartService.class);
        when(service.cart(anyInt())).thenReturn(LendingCart.empty(STATION));
        when(service.lines(anyInt())).thenReturn(List.of());
        when(service.check(anyInt())).thenReturn(List.of());
        when(service.checkout(anyInt(), anyInt(), anyString()))
                .thenReturn(new CheckoutResult(List.of(), List.of(), List.of()));
        harness = RouteHarness.serving(new LendingCartRoutes(service, mock(LendingRequestViewService.class)));
    }

    private int call(String method, String path, String body, StationPermission... permissions) {
        var session = harness.as(TestSessions.member(STATION, permissions));
        return harness.request(client -> switch (method) {
                    case "GET" -> client.get(PREFIX + path, session);
                    case "PUT" -> client.put(PREFIX + path, body, session);
                    default -> client.post(PREFIX + path, body, session);
                })
                .code();
    }

    /** Every cart route needs the right to ask for gear. */
    @Test
    void theCartNeedsTheRightToAskForGear() {
        assertEquals(403, call("GET", "/lending/cart", null));
        assertEquals(403, call("POST", "/lending/cart/checkout", "{}"));
        assertEquals(403, call("POST", "/lending/requests/4/borrow-again", "{}"));
        assertEquals(200, call("GET", "/lending/cart", null, StationPermission.INVENTORY_LENDING_REQUEST));
        assertEquals(200, call("GET", "/lending/cart/check", null, StationPermission.INVENTORY_LENDING_REQUEST));
    }

    /** A line and the days reach the service as sent. */
    @Test
    void aLineAndTheDaysReachTheService() {
        String line = """
                {"lendingStationUid":"%s","inventoryId":7,"artId":3,"itemId":null,"quantity":2,
                 "inventoryName":"Funk","lineName":"FuG 8b","pictureKey":null}""".formatted(PARTNER);
        assertEquals(201, call("POST", "/lending/cart/lines", line, StationPermission.INVENTORY_LENDING_REQUEST));
        verify(service).add(STATION, new NewCartLine(PARTNER, 7, 3, null, 2, "Funk", "FuG 8b", null));

        String details = """
                {"dateFrom":"2030-06-12","dateTo":"2030-06-14","occasion":"Fest","note":""}""";
        assertEquals(200, call("PUT", "/lending/cart", details, StationPermission.INVENTORY_LENDING_REQUEST));
        verify(service).setDetails(STATION, LocalDate.of(2030, 6, 12), LocalDate.of(2030, 6, 14), "Fest", "");
    }
}
