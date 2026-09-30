/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService;
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService.ShareDetail;
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService.SharePartner;
import dev.chojo.ember.feature.federation.service.InventoryShareService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The overview of what a station offers comes from the overview service, for the asking station. */
class InventoryShareRoutesTest {

    @Test
    void theOverviewListsTheStationsShareRowsAsDescribed() {
        var overview = mock(InventoryShareOverviewService.class);
        when(overview.overview(3))
                .thenReturn(List.of(
                        new ShareDetail(null, "Zelte", null, null, null, List.of(new SharePartner(7, "Wache Nord")))));
        var harness = RouteHarness.serving(new InventoryShareRoutes(mock(InventoryShareService.class), overview));

        var answer = harness.request(client -> client.get(
                PREFIX + "/lending/shares",
                harness.as(TestSessions.member(3, StationPermission.INVENTORY_LENDING_MANAGER))));

        var row = json(answer).path(0);
        assertEquals("Zelte", row.path("inventoryName").asString());
        assertEquals(
                "Wache Nord", row.path("partners").path(0).path("stationName").asString());
    }
}
