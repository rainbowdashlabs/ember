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
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService;
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService.ShareDetail;
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService.SharePartner;
import dev.chojo.ember.feature.federation.service.InventoryShareService;
import dev.chojo.ember.feature.federation.service.LendingOfferService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The overview of what a station offers comes from the overview service, for the asking station. */
class InventoryShareRoutesTest {

    @Test
    void theOverviewListsTheStationsShareRowsAsDescribed() {
        var overview = mock(InventoryShareOverviewService.class);
        when(overview.overview(3))
                .thenReturn(List.of(new ShareDetail(
                        null,
                        "Zelte",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        List.of(new SharePartner(7, "Wache Nord")))));
        var harness = RouteHarness.serving(
                new InventoryShareRoutes(mock(InventoryShareService.class), overview, mock(LendingOfferService.class)));

        var answer = harness.request(client -> client.get(
                PREFIX + "/lending/shares",
                harness.as(TestSessions.member(3, StationPermission.INVENTORY_LENDING_MANAGER))));

        var row = json(answer).path(0);
        assertEquals("Zelte", row.path("inventoryName").asString());
        assertEquals(
                "Wache Nord", row.path("partners").path(0).path("stationName").asString());
    }

    /** The offer is the lending manager's, reads the partner named, and refuses one that is not a number. */
    @Test
    void theOfferReadsThePartnerNamed() {
        var offer = mock(LendingOfferService.class);
        when(offer.offer(anyInt(), any())).thenReturn(List.of());
        var harness = RouteHarness.serving(new InventoryShareRoutes(
                mock(InventoryShareService.class), mock(InventoryShareOverviewService.class), offer));

        var forbidden = harness.request(client -> client.get(
                PREFIX + "/lending/offer",
                harness.as(TestSessions.member(3, StationPermission.INVENTORY_LENDING_REQUEST))));
        assertEquals(403, forbidden.code());

        var manager = TestSessions.member(3, StationPermission.INVENTORY_LENDING_MANAGER);
        harness.request(client -> client.get(PREFIX + "/lending/offer?partnerId=9", harness.as(manager)));
        verify(offer).offer(3, 9);
        var refused =
                harness.request(client -> client.get(PREFIX + "/lending/offer?partnerId=nord", harness.as(manager)));
        assertEquals(FederationRefusal.LENDING_PARTNER_NOT_A_NUMBER, refusalOf(refused));
    }
}
