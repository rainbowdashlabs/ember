/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import dev.chojo.ember.api.FederationHeaders;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteBoardRenamedWebhook;
import dev.chojo.ember.feature.board.route.RemoteBoardWebhookRoutes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FederationWebhookServiceTest {
    private static final int PARTNER_ID = 7;
    private static final int LOCAL_STATION_ID = 3;
    private static final UUID LOCAL_STATION_UID = UUID.randomUUID();
    private static final UUID PARTNER_STATION_UID = UUID.randomUUID();

    private record Received(String method, String path, String body, Headers headers) {}

    private static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static FederationPartner partner(String remoteHost, FederationStatus status, String ownPublicKey) {
        return new FederationPartner(
                PARTNER_ID,
                LOCAL_STATION_ID,
                PARTNER_STATION_UID,
                null,
                ownPublicKey,
                "partner-public-key",
                status,
                null,
                Instant.now(),
                Instant.now(),
                remoteHost,
                "Partner");
    }

    private static StationRepository stations() {
        var station = mock(Station.class);
        when(station.name()).thenReturn("Local");
        var stations = mock(StationRepository.class);
        when(stations.findById(LOCAL_STATION_ID)).thenReturn(Optional.of(station));
        when(stations.resolveUid(LOCAL_STATION_ID)).thenReturn(LOCAL_STATION_UID);
        return stations;
    }

    private static StationSigner signerWith(PrivateKey privateKey, FederationSigningService signing) {
        var keys = mock(StationKeyStore.class);
        when(keys.hasKey(LOCAL_STATION_ID)).thenReturn(true);
        when(keys.privateKey(LOCAL_STATION_ID)).thenReturn(Optional.of(privateKey));
        return new StationSigner(keys, signing);
    }

    private static FederationHttpClient signingClient() {
        var client = mock(FederationHttpClient.class);
        when(client.canSign(LOCAL_STATION_ID)).thenReturn(true);
        return client;
    }

    private static FederationRepository partners(FederationPartner partner) {
        var repository = mock(FederationRepository.class);
        when(repository.findPartnerById(PARTNER_ID)).thenReturn(Optional.of(partner));
        return repository;
    }

    private static FederationWebhookService inline(FederationRepository repository, FederationHttpClient client) {
        return new FederationWebhookService(repository, client, Runnable::run, List.of(Duration.ZERO));
    }

    /**
     * The notification reaches the partner's declared webhook path, names the sending station by its
     * UUID and carries a signature the partner verifies against the public key it holds for us.
     */
    @Test
    void deliversASignedRequestToTheDeclaredWebhookPath() throws Exception {
        var keys = keyPair();
        var ownPublicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        var received = new AtomicReference<Received>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.set(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders()));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            var host = "http://127.0.0.1:" + server.getAddress().getPort();
            var signing = new FederationSigningService();
            var client = new FederationHttpClient(
                    signerWith(keys.getPrivate(), signing),
                    stations(),
                    TestRemoteUrlValidator.permissiveOutbound(),
                    () -> mock(FederationContractRefreshService.class));
            var boardUid = UUID.randomUUID();

            inline(partners(partner(host, FederationStatus.ACTIVE, ownPublicKey)), client)
                    .notifyPartner(
                            PARTNER_ID,
                            RemoteBoardWebhookRoutes.BOARD_RENAMED.at(),
                            new RemoteBoardRenamedWebhook(boardUid, "Renamed", "RN"));

            var request = received.get();
            assertNotNull(request, "the partner was reached");
            assertEquals("POST", request.method());
            assertEquals("/api/v1/remote/boards/webhook/board-renamed", request.path());
            assertEquals(LOCAL_STATION_UID.toString(), request.headers().getFirst(FederationHeaders.HEADER_STATION_ID));
            assertEquals(PARTNER_STATION_UID.toString(), request.headers().getFirst("X-Federation-Target-Station-Id"));
            assertNotNull(request.headers().getFirst(FederationHeaders.HEADER_SURFACE));

            var body = JsonMapper.builder().build().readTree(request.body());
            assertEquals(boardUid.toString(), body.get("boardUid").asString());
            assertEquals("Renamed", body.get("newName").asString());

            assertTrue(
                    signing.verify(
                            "POST",
                            request.path(),
                            PARTNER_STATION_UID,
                            request.headers().getFirst("X-Federation-Nonce"),
                            request.body(),
                            request.headers().getFirst("X-Federation-Signature"),
                            signing.decodePublicKey(ownPublicKey),
                            Instant.parse(request.headers().getFirst("X-Federation-Timestamp"))),
                    "the partner accepts the signature with the key it holds for us");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesUntilThePartnerAccepts() {
        var client = signingClient();
        when(client.post(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(false)
                .thenReturn(true);
        var service = new FederationWebhookService(
                partners(partner("https://partner.example", FederationStatus.ACTIVE, null)),
                client,
                Runnable::run,
                List.of(Duration.ZERO, Duration.ZERO));

        service.notifyPartner(PARTNER_ID, RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(), "body");

        verify(client, times(2))
                .post(
                        eq("https://partner.example"),
                        eq(RemoteBoardWebhookRoutes.BOARD_UNSHARED.at()),
                        eq("body"),
                        eq(PARTNER_STATION_UID),
                        eq(LOCAL_STATION_ID));
    }

    @Test
    void stationsWithoutAKeyDoNotCall() {
        var client = mock(FederationHttpClient.class);

        inline(partners(partner("https://partner.example", FederationStatus.ACTIVE, null)), client)
                .notifyPartner(PARTNER_ID, RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(), "body");

        verify(client, never()).post(anyString(), any(), any(), any(), anyInt());
    }

    @Test
    void partnersOnThisInstanceAreNotCalled() {
        var client = mock(FederationHttpClient.class);

        inline(partners(partner(null, FederationStatus.ACTIVE, null)), client)
                .notifyPartner(PARTNER_ID, RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(), "body");

        verifyNoInteractions(client);
    }

    @Test
    void inactivePartnersAreNotCalled() {
        var client = mock(FederationHttpClient.class);

        inline(partners(partner("https://partner.example", FederationStatus.SUSPENDED, null)), client)
                .notifyPartner(PARTNER_ID, RemoteBoardWebhookRoutes.BOARD_UNSHARED.at(), "body");

        verifyNoInteractions(client);
    }
}
