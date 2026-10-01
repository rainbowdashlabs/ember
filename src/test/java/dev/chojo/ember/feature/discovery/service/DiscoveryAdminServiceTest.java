/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.api.refusal.AdminRefusal;
import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.DiscoveryBlocklistEntry;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryInfoResponse;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.AddPeerRequest;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.BlocklistRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryAdminServiceTest {
    private static final String URL = "https://peer.test";
    private static final Instant AT = Instant.parse("2026-09-01T00:00:00Z");

    private DiscoveryPeerRepository peers;
    private DiscoveryBlocklistRepository blocklist;
    private DiscoveryReputationService reputation;
    private DiscoveryPingService pings;
    private DiscoveryStationFetcher fetcher;
    private FederationPartnerSeeder seeder;
    private DiscoveryHttpClient http;
    private DiscoveryKeyService keys;
    private DiscoveryAdminService service;

    private static DiscoveryPeer peer(String key, boolean blocked) {
        return new DiscoveryPeer(key, URL, "i1", AT, AT, AT, AT, true, PeerSource.MANUAL, null, 0, blocked);
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @BeforeEach
    void setup() {
        peers = mock(DiscoveryPeerRepository.class);
        blocklist = mock(DiscoveryBlocklistRepository.class);
        reputation = mock(DiscoveryReputationService.class);
        pings = mock(DiscoveryPingService.class);
        fetcher = mock(DiscoveryStationFetcher.class);
        seeder = mock(FederationPartnerSeeder.class);
        http = mock(DiscoveryHttpClient.class);
        keys = mock(DiscoveryKeyService.class);
        service = new DiscoveryAdminService(peers, blocklist, reputation, pings, fetcher, seeder, http, keys);
    }

    private void peerAnswers(DiscoveryInfoResponse info) {
        when(http.probe(eq(URL), any(), eq(DiscoveryInfoResponse.class)))
                .thenReturn(new DiscoveryHttpClient.Probe<>(info, info == null ? "connection refused" : null));
    }

    @Test
    void theIdentityIsWhatThisInstanceIntroducesItselfWith() {
        when(keys.instanceId()).thenReturn("i0");
        when(keys.publicKeyBase64()).thenReturn("k0");
        when(pings.selfBaseUrl()).thenReturn("https://self.test");

        assertEquals(new DiscoveryAdminService.IdentityResponse("i0", "k0", "https://self.test"), service.identity());
    }

    @Test
    void aProbeNeedsAnAddressAndSaysWhyAPeerDidNotAnswer() {
        assertEquals(DiscoveryRefusal.PROBE_NEEDS_AN_ADDRESS, refusalOf(() -> service.probe(" ")));
        assertEquals(DiscoveryRefusal.PROBE_NEEDS_AN_ADDRESS, refusalOf(() -> service.probe(null)));
        peerAnswers(null);
        assertEquals(AdminRefusal.PEER_DID_NOT_ANSWER, refusalOf(() -> service.probe(URL)));
    }

    @Test
    void aPeerIsAddedOnlyWhenItNamesTheExpectedKeyAndWantsDiscovery() {
        assertEquals(
                DiscoveryRefusal.PEER_NEEDS_AN_ADDRESS,
                refusalOf(() -> service.addPeer(new AddPeerRequest(null, null))));
        peerAnswers(new DiscoveryInfoResponse(URL, "i1", null, "1", true));
        assertEquals(AdminRefusal.PEER_NAMED_NO_KEY, refusalOf(() -> service.addPeer(new AddPeerRequest(URL, null))));
        peerAnswers(new DiscoveryInfoResponse(URL, "i1", "k1", "1", true));
        assertEquals(
                DiscoveryRefusal.PEER_KEY_NOT_THE_EXPECTED_ONE,
                refusalOf(() -> service.addPeer(new AddPeerRequest(URL, "other"))));
        peerAnswers(new DiscoveryInfoResponse(URL, "i1", "k1", "1", false));
        assertEquals(
                DiscoveryRefusal.PEER_DOES_NOT_WANT_DISCOVERY,
                refusalOf(() -> service.addPeer(new AddPeerRequest(URL, ""))));
        verify(peers, never()).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void anAddedPeerIsPingedAndAFailedPingDoesNotTakeItOut() {
        peerAnswers(new DiscoveryInfoResponse(URL, "i1", "k1", "1", true));
        when(peers.upsert("k1", URL, "i1", PeerSource.MANUAL, null)).thenReturn(peer("k1", false));
        doThrow(new IllegalStateException("unreachable")).when(pings).sendPing(any());

        var added = service.addPeer(new AddPeerRequest(URL, "k1"));

        assertEquals("k1", added.publicKey());
        verify(pings).sendPing(any());
    }

    @Test
    void aPeerThatIsNotHereCannotBeChangedOrPinged() {
        when(peers.findByPublicKey("gone")).thenReturn(Optional.empty());

        assertEquals(DiscoveryRefusal.PEER_NOT_HERE, refusalOf(() -> service.upvote("gone")));
        assertEquals(DiscoveryRefusal.PEER_NOT_HERE_ON_PING, refusalOf(() -> service.pingNow("gone")));
        verify(reputation, never()).upvote(any());
    }

    @Test
    void aPeerIsVotedOnBlockedAndUnblockedAndAnsweredAsItNowStands() {
        when(peers.findByPublicKey("k1")).thenReturn(Optional.of(peer("k1", false)));

        service.upvote("k1");
        service.downvote("k1");
        service.block("k1");
        service.unblock("k1");
        service.pingNow("k1");

        verify(reputation).upvote("k1");
        verify(reputation).downvote("k1");
        verify(peers).setBlocked("k1", true);
        verify(peers).setBlocked("k1", false);
        verify(pings).sendPing(peer("k1", false));
    }

    @Test
    void aPeerThatVanishesDuringAChangeIsSaidSo() {
        when(peers.findByPublicKey("k1")).thenReturn(Optional.of(peer("k1", false)), Optional.empty());

        assertEquals(DiscoveryRefusal.PEER_NOT_HERE_AFTER_CHANGE, refusalOf(() -> service.block("k1")));
    }

    @Test
    void discoveringNowPingsEveryUsablePeerAndFetchesTheCardsAgain() {
        when(peers.findUsable()).thenReturn(List.of(peer("k1", false), peer("k2", false)));
        when(fetcher.refreshAll()).thenReturn(5);
        when(seeder.seedFromFederationPartners()).thenReturn(3);
        when(peers.findAll()).thenReturn(List.of(peer("k1", true)));
        when(peers.delete("k1")).thenReturn(true);

        assertEquals(new DiscoveryAdminService.DiscoverNowResponse(2, 5), service.discoverNow());
        assertEquals(3, service.seedFromFederation());
        assertTrue(service.peers().getFirst().blocked());
        assertTrue(service.deletePeer("k1"));
    }

    @Test
    void theBlocklistTakesOnlyCompleteEntries() {
        when(blocklist.findAll())
                .thenReturn(List.of(new DiscoveryBlocklistEntry("x", BlocklistKind.BASE_URL, null, AT)));
        when(blocklist.remove("x")).thenReturn(true);

        assertEquals(
                DiscoveryRefusal.BLOCKLIST_ENTRY_INCOMPLETE,
                refusalOf(() -> service.addToBlocklist(new BlocklistRequest(" ", BlocklistKind.BASE_URL, null))));
        assertEquals(
                DiscoveryRefusal.BLOCKLIST_ENTRY_INCOMPLETE,
                refusalOf(() -> service.addToBlocklist(new BlocklistRequest("x", null, null))));
        assertEquals(
                DiscoveryRefusal.BLOCKLIST_ENTRY_INCOMPLETE,
                refusalOf(() -> service.addToBlocklist(new BlocklistRequest(null, BlocklistKind.BASE_URL, null))));
        service.addToBlocklist(new BlocklistRequest("x", BlocklistKind.PUBLIC_KEY, "spam"));

        verify(blocklist).add(BlocklistKind.PUBLIC_KEY, "x", "spam");
        assertEquals("x", service.blocklist().getFirst().value());
        assertTrue(service.removeFromBlocklist("x"));
    }
}
