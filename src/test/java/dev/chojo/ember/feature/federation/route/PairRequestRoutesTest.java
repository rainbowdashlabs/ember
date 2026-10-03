/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestReceipt;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestStatusQuery;
import dev.chojo.ember.feature.federation.service.IncomingPairRequestService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A request to federate arrives without any session: the route hands the body to the service and
 * answers what it says, the asked station's name or the refusal under its own code.
 */
class PairRequestRoutesTest {
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

    private static final String QUESTION = """
            {"requesterStationUid": "00000000-0000-0000-0000-000000000001",
             "targetStationUid": "00000000-0000-0000-0000-000000000002",
             "issuedAt": "2026-10-03T10:00:00Z",
             "nonce": "nonce",
             "stationSignature": "station",
             "instanceSignature": "instance"}""";

    private static final String ANSWER = """
            {"requesterStationUid": "00000000-0000-0000-0000-000000000001",
             "targetStationUid": "00000000-0000-0000-0000-000000000002",
             "status": "DECLINED",
             "stationName": "Wache Süd",
             "baseUrl": "https://sued.example",
             "issuedAt": "2026-10-03T10:00:00Z",
             "nonce": "nonce",
             "instanceSignature": "instance"}""";

    private IncomingPairRequestService incoming;
    private OutgoingPairRequestService outgoing;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        incoming = mock(IncomingPairRequestService.class);
        outgoing = mock(OutgoingPairRequestService.class);
        harness = RouteHarness.serving(new PairRequestRoutes(incoming, outgoing));
    }

    @Test
    void aQuestionIsAnsweredWithWhereTheRequestStands() {
        when(incoming.status(any(PairRequestStatusQuery.class)))
                .thenReturn(new PairRequestAnswer(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        PairRequestStatus.PENDING,
                        "Wache Süd",
                        "https://sued.example",
                        null,
                        null,
                        Instant.EPOCH,
                        "nonce",
                        null,
                        "instance"));

        var answer = harness.request(
                client -> client.post(PREFIX + "/public/discovery/pair-request/status", body(QUESTION)));

        assertEquals("PENDING", json(answer).path("status").asString());
    }

    @Test
    void aPushedAnswerIsHandedToTheAskingSide() {
        var answer =
                harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request/answer", body(ANSWER)));

        assertEquals(200, answer.code());
        verify(outgoing).receiveAnswer(any(PairRequestAnswer.class));
    }

    @Test
    void anAnswerNobodyWaitsForCarriesItsRefusal() {
        doThrow(FederationRefusal.PAIR_ANSWER_NOT_EXPECTED.raise())
                .when(outgoing)
                .receiveAnswer(any(PairRequestAnswer.class));

        var answer =
                harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request/answer", body(ANSWER)));

        assertEquals(FederationRefusal.PAIR_ANSWER_NOT_EXPECTED, refusalOf(answer));
    }

    @Test
    void aTakenRequestIsAnsweredWithTheAskedStationsName() {
        when(incoming.receive(any(PairRequestMessage.class))).thenReturn(new PairRequestReceipt("Wache Süd"));

        var answer = harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request", body(REQUEST)));

        assertEquals(201, answer.code());
        assertEquals("Wache Süd", json(answer).path("stationName").asString());
    }

    @Test
    void aRefusedRequestCarriesTheRefusalsCode() {
        when(incoming.receive(any(PairRequestMessage.class)))
                .thenThrow(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY.raise());

        var answer = harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request", body(REQUEST)));

        assertEquals(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY, refusalOf(answer));
    }

    @Test
    void aRefusedRequestNamesItsReasonForTheOtherInstance() {
        when(incoming.receive(any(PairRequestMessage.class)))
                .thenThrow(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY.raise());

        var answer = harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request", body(REQUEST)));

        assertEquals(409, answer.code());
        var refusal = json(answer);
        assertEquals("DECLINED_RECENTLY", refusal.path("reason").asString());
        assertEquals(
                FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY.code(),
                refusal.path("code").asString());
    }

    @Test
    void aLimitedRequestSaysHowLongToWait() {
        when(incoming.receive(any(PairRequestMessage.class))).thenAnswer(invocation -> {
            RateLimits.enforce(FederationRefusal.PAIR_REQUEST_TOO_MANY_FOR_STATION, Optional.of(60L));
            return new PairRequestReceipt("never");
        });

        var answer = harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request", body(REQUEST)));

        assertEquals(429, answer.code());
        assertEquals("60", RouteHarness.header(answer, "Retry-After"));
        var refusal = json(answer);
        assertEquals("TOO_MANY_FOR_STATION", refusal.path("reason").asString());
        assertEquals(60, refusal.path("retryAfterSeconds").asInt());
    }

    @Test
    void aQuestionAndAnAnswerRefusedNameTheirReasons() {
        when(incoming.status(any(PairRequestStatusQuery.class)))
                .thenThrow(FederationRefusal.PAIR_STATUS_NOT_HERE.raise());
        doThrow(FederationRefusal.PAIR_ANSWER_SIGNATURE_NOT_GOOD.raise())
                .when(outgoing)
                .receiveAnswer(any(PairRequestAnswer.class));

        var question = harness.request(
                client -> client.post(PREFIX + "/public/discovery/pair-request/status", body(QUESTION)));
        var answer =
                harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request/answer", body(ANSWER)));

        assertEquals("STATUS_NOT_HERE", json(question).path("reason").asString());
        assertEquals("ANSWER_SIGNATURE_NOT_GOOD", json(answer).path("reason").asString());
    }

    @Test
    void aRefusalThatIsNoReasonOnTheWireIsAnsweredAsUsual() {
        when(incoming.receive(any(PairRequestMessage.class)))
                .thenThrow(FederationRefusal.FEDERATION_STATION_NOT_HERE.raise());

        var answer = harness.request(client -> client.post(PREFIX + "/public/discovery/pair-request", body(REQUEST)));

        var refusal = json(answer);
        assertEquals(
                FederationRefusal.FEDERATION_STATION_NOT_HERE.code(),
                refusal.path("code").asString());
        assertTrue(refusal.path("reason").isMissingNode());
    }
}
