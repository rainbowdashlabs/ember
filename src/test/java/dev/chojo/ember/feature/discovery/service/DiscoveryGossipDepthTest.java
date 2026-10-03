/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.auth.signing.Ed25519Keys;
import dev.chojo.ember.auth.signing.repository.SignedRequestNonceRepository;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryCallbackMessage;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryIdentity;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryPingMessage;
import dev.chojo.ember.feature.discovery.protocol.PeerAnnouncement;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Discovery explores the network one hop per ping cycle. This instance starts out knowing only peer B.
 * The first cycle pings B, and B's callback announces C. The next cycle pings C directly, and C's
 * callback announces D, which the cycle after that pings. The reach grows by one hop per cycle no
 * matter how small the configured depth is, because every announced peer is stored and pinged on its
 * own in the next round. A callback that answers no ping this instance sent stores nothing.
 */
class DiscoveryGossipDepthTest extends RepositoryTestBase {
    private static final String SELF_URL = "https://self.example";
    private static final String PING_PATH = "/api/v1/discovery/ping";

    private final DiscoveryHttpClient http = mock(DiscoveryHttpClient.class);
    private DiscoveryPingService pingService;
    private Runnable pingCycle;

    @BeforeEach
    void setUp() {
        discoveryPeerRepo.findAll().forEach(peer -> discoveryPeerRepo.delete(peer.publicKey()));

        var keys = mock(DiscoveryKeyService.class);
        when(keys.publicKeyBase64()).thenReturn(newPublicKey());
        when(keys.instanceId()).thenReturn("self");
        var signing = mock(DiscoverySigningService.class);
        when(signing.verify(anyString(), anyString(), anyString())).thenReturn(true);
        var settings = mock(DiscoverySettingsService.class);
        when(settings.isEnabled()).thenReturn(true);
        when(settings.maxDepth()).thenReturn(0);
        var validator = mock(RemoteUrlValidator.class);
        when(validator.isAllowed(anyString())).thenReturn(true);
        when(http.signedPost(anyString(), anyString(), any())).thenReturn(true);

        pingService = new DiscoveryPingService(
                keys,
                signing,
                http,
                discoveryPeerRepo,
                discoveryPingRepo,
                new DatabaseReplayStore(new SignedRequestNonceRepository()),
                discoveryBlocklistRepo,
                new DiscoveryReputationService(discoveryPeerRepo),
                settings,
                validator,
                selfConf(),
                mock(TaskScheduler.class));
        pingCycle = new DiscoveryPingScheduler(discoveryPeerRepo, pingService, settings)
                .scheduledTasks()
                .getFirst()
                .work();
    }

    @Test
    void eachCyclePingsThePeersTheLastOneLearnedAbout() {
        var b = new DiscoveryIdentity("https://b.example", newPublicKey(), "b");
        var c = new DiscoveryIdentity("https://c.example", newPublicKey(), "c");
        var d = new DiscoveryIdentity("https://d.example", newPublicKey(), "d");
        discoveryPeerRepo.upsert(b.publicKey(), b.baseUrl(), b.instanceId(), PeerSource.MANUAL, null);

        var first = runPingCycle();
        assertEquals(Set.of(b.baseUrl()), first.keySet());

        assertTrue(pingService.handleCallback("{}", callback(b, first.get(b.baseUrl()), c), "signature"));
        var learnedC = discoveryPeerRepo.findByPublicKey(c.publicKey()).orElseThrow();
        assertEquals(PeerSource.GOSSIP, learnedC.source());
        assertEquals(b.publicKey(), learnedC.introducedBy());

        var second = runPingCycle();
        assertEquals(Set.of(b.baseUrl(), c.baseUrl()), second.keySet());

        assertTrue(pingService.handleCallback("{}", callback(c, second.get(c.baseUrl()), d), "signature"));
        assertEquals(
                c.publicKey(),
                discoveryPeerRepo.findByPublicKey(d.publicKey()).orElseThrow().introducedBy());

        var third = runPingCycle();
        assertEquals(Set.of(b.baseUrl(), c.baseUrl(), d.baseUrl()), third.keySet());
    }

    @Test
    void aCallbackAnsweringNoPingSentStoresNothing() {
        var stranger = new DiscoveryIdentity("https://stranger.example", newPublicKey(), "stranger");
        var announced = new DiscoveryIdentity("https://announced.example", newPublicKey(), "announced");

        assertFalse(pingService.handleCallback("{}", callback(stranger, "never-sent", announced), "signature"));

        assertTrue(discoveryPeerRepo.findByPublicKey(announced.publicKey()).isEmpty());
        assertTrue(discoveryPeerRepo.findByPublicKey(stranger.publicKey()).isEmpty());
    }

    /**
     * Runs one ping cycle and returns the nonce each pinged base URL was sent.
     */
    private Map<String, String> runPingCycle() {
        clearInvocations(http);
        pingCycle.run();
        var baseUrls = ArgumentCaptor.forClass(String.class);
        var messages = ArgumentCaptor.forClass(Object.class);
        verify(http, atLeast(0)).signedPost(baseUrls.capture(), eq(PING_PATH), messages.capture());
        Map<String, String> nonces = new HashMap<>();
        for (int i = 0; i < baseUrls.getAllValues().size(); i++) {
            var ping = (DiscoveryPingMessage) messages.getAllValues().get(i);
            nonces.put(baseUrls.getAllValues().get(i), ping.nonce());
        }
        return nonces;
    }

    private static DiscoveryCallbackMessage callback(
            DiscoveryIdentity from, String inReplyTo, DiscoveryIdentity announced) {
        var announcement = new PeerAnnouncement(
                announced.baseUrl(), announced.publicKey(), announced.instanceId(), from.instanceId(), Instant.now());
        return new DiscoveryCallbackMessage(from, inReplyTo, Instant.now(), List.of(announcement));
    }

    private static Conf selfConf() {
        var api = mock(Api.class);
        when(api.baseUrl()).thenReturn(SELF_URL);
        var file = mock(File.class);
        when(file.api()).thenReturn(api);
        var conf = mock(Conf.class);
        when(conf.main()).thenReturn(file);
        return conf;
    }

    private static String newPublicKey() {
        try {
            var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
            return Base64.getEncoder().encodeToString(Ed25519Keys.raw(key));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
