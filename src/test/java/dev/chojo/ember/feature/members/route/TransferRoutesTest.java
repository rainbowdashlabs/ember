/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.feature.station.service.StationTransferService;
import dev.chojo.ember.feature.station.service.StationTransferService.TransferStatusResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Where a station moving to another instance stands, and the destination saying it is done.
 */
class TransferRoutesTest {
    private final StationExportService exports = mock(StationExportService.class);
    private final StationTransferService transfers = mock(StationTransferService.class);
    private final RouteHarness harness =
            RouteHarness.serving(new TransferRoutes(exports, mock(StationImportService.class), transfers));

    @Test
    void theStatusIsTheStationsOwn() {
        when(transfers.status(3)).thenReturn(new TransferStatusResponse(true, "https://there.test"));

        var status = harness.request(client -> client.get(
                PREFIX + "/station/transfer/status", harness.as(TestSessions.member(3, StationPermission.LOGIN))));

        assertEquals(
                "https://there.test", json(status).path("targetInstanceUrl").asString());
    }

    @Test
    void theDestinationCompletesWithAGoodTokenOnly() {
        when(exports.validateToken("good")).thenReturn(Optional.of(9));
        when(exports.validateToken("bad")).thenReturn(Optional.empty());

        harness.run((server, client) -> {
            var done = client.post(
                    PREFIX + "/public/transfer/good/complete",
                    null,
                    request -> request.header("X-Ember-Importing-From", "https://there.test"));
            assertEquals(204, done.code());
            assertEquals(
                    Refusal.TRANSFER_TOKEN_NOT_GOOD_ON_COMPLETE,
                    refusalOf(client.post(PREFIX + "/public/transfer/bad/complete")));
        });

        verify(transfers).complete(9, "https://there.test");
        verify(transfers).complete(anyInt(), any());
    }
}
