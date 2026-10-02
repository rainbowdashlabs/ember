/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestReceipt;
import dev.chojo.ember.feature.federation.service.IncomingPairRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A request to federate arrives without any session: the route hands the body to the service and
 * answers what it says, the asked station's name or the refusal under its own code.
 */
class RemotePairRequestRoutesTest {
    private static final String REQUEST = """
            {"requesterStationUid": "00000000-0000-0000-0000-000000000001",
             "requesterStationName": "Wache Nord",
             "requesterPublicKey": "key",
             "requesterBaseUrl": "https://nord.example",
             "requesterInstanceKey": "instance",
             "targetStationUid": "00000000-0000-0000-0000-000000000002",
             "contract": {"core": "core", "features": {}},
             "issuedAt": "2026-10-03T10:00:00Z",
             "nonce": "nonce",
             "stationSignature": "station",
             "instanceSignature": "instance"}""";

    private IncomingPairRequestService incoming;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        incoming = mock(IncomingPairRequestService.class);
        harness = RouteHarness.serving(new RemotePairRequestRoutes(incoming));
    }

    @Test
    void aTakenRequestIsAnsweredWithTheAskedStationsName() {
        when(incoming.receive(any(PairRequestMessage.class))).thenReturn(new PairRequestReceipt("Wache Süd"));

        var answer = harness.request(client -> client.post(PREFIX + "/remote/pair-request", body(REQUEST)));

        assertEquals(201, answer.code());
        assertEquals("Wache Süd", json(answer).path("stationName").asString());
    }

    @Test
    void aRefusedRequestCarriesTheRefusalsCode() {
        when(incoming.receive(any(PairRequestMessage.class)))
                .thenThrow(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY.raise());

        var answer = harness.request(client -> client.post(PREFIX + "/remote/pair-request", body(REQUEST)));

        assertEquals(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY, refusalOf(answer));
    }
}
