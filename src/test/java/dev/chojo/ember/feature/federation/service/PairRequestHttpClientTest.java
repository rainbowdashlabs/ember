/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.sun.net.httpserver.HttpServer;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestStatusQuery;
import dev.chojo.ember.feature.federation.service.PairRequestHttpClient.Delivery;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the other instance answered, as the asking side reads it: a taken request with the asked
 * station's name, a refusal with its code, a route that is not there at all, and no answer.
 */
class PairRequestHttpClientTest {
    private static final UUID ASKING = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ASKED = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final Map<String, Answer> answers = new HashMap<>();
    private HttpServer server;
    private PairRequestHttpClient client;

    private record Answer(int status, String body) {}

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var answer = answers.getOrDefault(exchange.getRequestURI().getPath(), new Answer(404, "Not Found"));
            byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new PairRequestHttpClient(TestRemoteUrlValidator.permissiveOutbound());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static PairRequestMessage message() {
        return new PairRequestMessage(
                ASKING,
                "Wache Nord",
                "key",
                "https://nord.example",
                "instance",
                ASKED,
                new FederationContract("core", Map.of()),
                Instant.now(),
                "nonce",
                "station",
                "instance");
    }

    @Test
    void aTakenRequestNamesTheAskedStation() {
        answers.put("/api/v1/remote/pair-request", new Answer(201, "{\"stationName\": \"Wache Süd\"}"));

        var delivery = client.send(baseUrl(), message());

        assertEquals(
                "Wache Süd", assertInstanceOf(Delivery.Taken.class, delivery).stationName());
    }

    @Test
    void aRefusalCarriesItsCode() {
        answers.put("/api/v1/remote/pair-request", new Answer(409, "{\"error\": \"Conflict\", \"code\": \"X-065\"}"));

        var delivery = assertInstanceOf(Delivery.Answered.class, client.send(baseUrl(), message()));

        assertEquals(409, delivery.status());
        assertEquals("X-065", delivery.code());
    }

    @Test
    void aMissingRouteIsAnAnswerWithoutACode() {
        var delivery = assertInstanceOf(Delivery.Answered.class, client.send(baseUrl(), message()));

        assertEquals(404, delivery.status());
        assertEquals(null, delivery.code());
    }

    @Test
    void anInstanceThatIsNotThereIsUnreachable() {
        String gone = baseUrl();
        server.stop(0);

        var delivery = assertInstanceOf(Delivery.Failed.class, client.send(gone, message()));

        assertEquals(PairRequestHttpClient.Failure.UNREACHABLE, delivery.failure());
    }

    @Test
    void aStatusIsReadAndAnUnansweredOneIsNothing() {
        var query = new PairRequestStatusQuery(ASKING, ASKED, Instant.now(), "nonce", "station", "instance");
        assertTrue(client.askStatus(baseUrl(), query).isEmpty());

        answers.put("/api/v1/remote/pair-request/status", new Answer(200, """
                        {"requesterStationUid": "%s", "targetStationUid": "%s", "status": "DECLINED",
                         "stationName": "Wache Süd", "baseUrl": "https://sued.example",
                         "issuedAt": "2026-10-03T10:00:00Z", "nonce": "n", "instanceSignature": "s"}""".formatted(ASKING, ASKED)));

        var answer = client.askStatus(baseUrl(), query).orElseThrow();
        assertEquals(PairRequestStatus.DECLINED, answer.status());
    }

    @Test
    void aPushedAnswerSaysWhetherItWasTaken() {
        var answer = new PairRequestAnswer(
                ASKING,
                ASKED,
                PairRequestStatus.DECLINED,
                "Wache Süd",
                "https://sued.example",
                null,
                null,
                Instant.now(),
                "nonce",
                null,
                "instance");
        assertFalse(client.deliverAnswer(baseUrl(), answer));

        answers.put("/api/v1/remote/pair-request/answer", new Answer(200, "{\"status\": \"ok\"}"));

        assertTrue(client.deliverAnswer(baseUrl(), answer));
    }
}
