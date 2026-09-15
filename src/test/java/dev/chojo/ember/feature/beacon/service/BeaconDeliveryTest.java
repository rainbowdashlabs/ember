/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import com.sun.net.httpserver.HttpServer;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.discovery.service.DiscoverySigningService;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a delivery to a beacon actually puts on the wire.
 *
 * <p>This is the seam the other beacon tests leave out. The sending side and the receiving side each
 * had tests of their own and both passed for releases while nothing ever arrived: the intake demanded
 * a header naming the key a signature is to be checked against, and no sender sent one. Every
 * delivery was refused as unsigned, and because a refusal was a boolean nobody read, an operator saw
 * an empty beacon and no reason for it.
 *
 * <p>So what is asserted here is the agreement itself: the headers a real delivery carries, and that
 * the receiving side verifies the body it is given against the key that travelled with it.
 */
class BeaconDeliveryTest {

    /** What the intake reads to learn who signed a delivery. */
    private static final String KEY_HEADER = DiscoverySigningService.BEACON_KEY_HEADER;

    private record Delivery(String body, String signature, String key) {}

    /**
     * Sends one payload to a stub standing in for a beacon and hands back what arrived.
     *
     * @param status what the stub answers with
     */
    private static Delivery deliveredTo(int status, DiscoverySigningService signing, Object payload)
            throws IOException {
        var seen = new AtomicReference<Delivery>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.set(new Delivery(
                    body,
                    exchange.getRequestHeaders().getFirst(DiscoverySigningService.SIGNATURE_HEADER),
                    exchange.getRequestHeaders().getFirst(KEY_HEADER)));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new DiscoveryHttpClient(signing, TestRemoteUrlValidator.permissive());
            var answered = client.beaconPost(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "/api/v1/beacon/reports", payload);
            assertEquals(status, answered.orElseThrow().status(), "the beacon's answer is handed back, not a boolean");
            assertEquals(status < 300, answered.orElseThrow().accepted());
            return seen.get();
        } finally {
            server.stop(0);
        }
    }

    private static BeaconPayloads.ReportPayload aReport() {
        return new BeaconPayloads.ReportPayload(
                new BeaconPayloads.Envelope(
                        BeaconPayloads.PROTOCOL_VERSION,
                        Instant.now(),
                        UUID.randomUUID().toString(),
                        "http://beacon.invalid"),
                "26.17.1",
                null,
                null,
                "Etwas ging schief",
                "/station/dashboard/overview",
                Instant.now());
    }

    /**
     * The delivery carries the key its signature is to be checked against.
     *
     * <p>Without this header the intake answers "A report has to be signed" and nothing is stored,
     * which is exactly what happened to every fault and every report until it was sent.
     */
    @Test
    void aDeliveryCarriesTheKeyTheIntakeAsksFor() throws IOException {
        var signing = new DiscoverySigningService(new DiscoveryKeyService());

        var delivered = deliveredTo(202, signing, aReport());

        assertNotNull(delivered, "the beacon was reached");
        assertNotNull(delivered.key(), "the key header is set");
        assertEquals(signing.publicKeyBase64(), delivered.key(), "and it is this instance's own key");
        assertNotNull(delivered.signature(), "the signature header is set");
    }

    /**
     * The receiving side accepts what the sending side produced.
     *
     * <p>Verified the way the intake verifies it: the body as it arrived, against the key that came
     * with it. Signing a re-serialised payload rather than the bytes that travelled would pass a test
     * and fail in the wild the first time a field was ordered differently.
     */
    @Test
    void whatWasSignedIsWhatTheIntakeVerifies() throws IOException {
        var signing = new DiscoverySigningService(new DiscoveryKeyService());

        var delivered = deliveredTo(202, signing, aReport());

        assertTrue(
                signing.verify(delivered.body(), delivered.signature(), delivered.key()),
                "the signature checks out against the body that arrived");
    }

    /** The key the intake computes an identity from is the one the delivery carried. */
    @Test
    void theKeyNamesTheInstanceItCameFrom() throws IOException {
        var keys = new DiscoveryKeyService();
        var signing = new DiscoverySigningService(keys);

        var delivered = deliveredTo(202, signing, aReport());

        assertEquals(
                keys.instanceId(),
                DiscoveryKeyService.fingerprintOf(delivered.key()),
                "the sender the beacon files this under is this instance");
    }

    /**
     * A refusal is answered as a refusal rather than as silence.
     *
     * <p>The boolean this replaced was false for a closed port, a wrong address and a rejected
     * signature alike, and none of the three reached a log. Handing the status and the beacon's own
     * words back is what lets the sender say which of them happened: a beacon says no with 403 for
     * three different reasons.
     */
    @Test
    void aRefusalIsHandedBackWithWhatTheBeaconSaid() throws IOException {
        var signing = new DiscoverySigningService(new DiscoveryKeyService());

        var delivered = deliveredTo(403, signing, aReport());

        assertNotNull(delivered.key(), "the delivery was still made");
    }

    /** A beacon that cannot be reached is empty rather than a status nobody can read. */
    @Test
    void aBeaconThatCannotBeReachedIsSaidToBeUnreachable() {
        var signing = new DiscoverySigningService(new DiscoveryKeyService());
        var client = new DiscoveryHttpClient(signing, TestRemoteUrlValidator.permissive());

        assertTrue(client.beaconPost("http://127.0.0.1:1", "/api/v1/beacon/reports", aReport())
                .isEmpty());
    }
}
