/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.auth.signing.repository.SignedRequestNonceRepository;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryIdentity;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryPingMessage;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPingRepository;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An inbound discovery ping is taken once: a captured ping sent again inside its window is dropped
 * by the shared nonce store, and a ping that comes back to the instance that sent it is dropped too.
 */
class DiscoveryPingReplayTest extends RepositoryTestBase {
    private static final String OWN_KEY = "own-key";

    private DiscoveryPeerRepository peers;
    private DiscoveryPingService service;

    @BeforeEach
    void setUp() {
        var keys = mock(DiscoveryKeyService.class);
        when(keys.publicKeyBase64()).thenReturn(OWN_KEY);
        var signing = mock(DiscoverySigningService.class);
        when(signing.verify(anyString(), anyString(), anyString())).thenReturn(true);
        var settings = mock(DiscoverySettingsService.class);
        when(settings.isEnabled()).thenReturn(true);
        var validator = mock(RemoteUrlValidator.class);
        when(validator.isAllowed(anyString())).thenReturn(true);
        peers = mock(DiscoveryPeerRepository.class);
        service = new DiscoveryPingService(
                keys,
                signing,
                mock(DiscoveryHttpClient.class),
                peers,
                mock(DiscoveryPingRepository.class),
                new DatabaseReplayStore(new SignedRequestNonceRepository()),
                mock(DiscoveryBlocklistRepository.class),
                mock(DiscoveryReputationService.class),
                settings,
                validator,
                mock(Conf.class),
                mock(TaskScheduler.class));
    }

    private static DiscoveryPingMessage ping(String fromKey, String nonce) {
        return new DiscoveryPingMessage(
                new DiscoveryIdentity("https://peer.example", fromKey, "abc"),
                nonce,
                Instant.now(),
                "https://peer.example/api/v1/discovery/peers",
                1);
    }

    @Test
    void theSamePingSentTwiceIsTakenOnce() {
        var message = ping("peer-key", UUID.randomUUID().toString());

        service.handleInboundPing("{}", message, "signature");
        service.handleInboundPing("{}", message, "signature");

        verify(peers, times(1)).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void aPingFromThisInstanceItselfIsDropped() {
        service.handleInboundPing("{}", ping(OWN_KEY, UUID.randomUUID().toString()), "signature");

        verify(peers, never()).upsert(any(), any(), any(), any(), any());
    }
}
