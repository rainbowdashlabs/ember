/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.sun.net.httpserver.HttpServer;
import dev.chojo.ember.feature.board.route.RemoteBoardWebhookRoutes;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederationHttpClientTest {

    /**
     * A partner that takes the request and never answers turns into a failed call once the request
     * timeout has passed, instead of holding the caller for as long as the connection stays open.
     */
    @Test
    void aStalledPartnerFailsTheCallInsteadOfHangingIt() throws Exception {
        var release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keys = mock(StationKeyStore.class);
            when(keys.privateKey(1))
                    .thenReturn(Optional.of(generator.generateKeyPair().getPrivate()));
            var stations = mock(StationRepository.class);
            when(stations.resolveUid(1)).thenReturn(UUID.randomUUID());
            when(stations.findById(1)).thenReturn(Optional.empty());
            var client = new FederationHttpClient(
                    new StationSigner(keys, new FederationSigningService()),
                    stations,
                    TestRemoteUrlValidator.permissiveOutbound(),
                    () -> mock(FederationContractRefreshService.class),
                    Duration.ofMillis(300));

            boolean delivered = assertTimeoutPreemptively(
                    Duration.ofSeconds(5),
                    () -> client.post(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            RemoteBoardWebhookRoutes.MENTION.at(),
                            "body",
                            UUID.randomUUID(),
                            1));

            assertFalse(delivered);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
