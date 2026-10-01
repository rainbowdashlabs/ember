/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.api.refusal.AdminRefusal;
import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryInfoResponse;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * What an administrator does with discovery: the instances this one knows of, how far it trusts
 * them, and the addresses it refuses to deal with.
 */
@Singleton
public class DiscoveryAdminService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryAdminService.class);

    private final DiscoveryPeerRepository peers;
    private final DiscoveryBlocklistRepository blocklist;
    private final DiscoveryReputationService reputation;
    private final DiscoveryPingService pings;
    private final DiscoveryStationFetcher stationFetcher;
    private final FederationPartnerSeeder federationPartnerSeeder;
    private final DiscoveryHttpClient httpClient;
    private final DiscoveryKeyService keys;

    @Inject
    public DiscoveryAdminService(
            DiscoveryPeerRepository peers,
            DiscoveryBlocklistRepository blocklist,
            DiscoveryReputationService reputation,
            DiscoveryPingService pings,
            DiscoveryStationFetcher stationFetcher,
            FederationPartnerSeeder federationPartnerSeeder,
            DiscoveryHttpClient httpClient,
            DiscoveryKeyService keys) {
        this.peers = peers;
        this.blocklist = blocklist;
        this.reputation = reputation;
        this.pings = pings;
        this.stationFetcher = stationFetcher;
        this.federationPartnerSeeder = federationPartnerSeeder;
        this.httpClient = httpClient;
        this.keys = keys;
    }

    private static PeerResponse toResponse(DiscoveryPeer p) {
        return new PeerResponse(
                p.publicKey(),
                p.baseUrl(),
                p.instanceId(),
                p.firstSeenAt(),
                p.lastSeenAt(),
                p.lastPingedAt(),
                p.lastReachedAt(),
                p.reachable(),
                p.source(),
                p.introducedBy(),
                p.reputation(),
                p.blocked());
    }

    /**
     * How this instance introduces itself to others.
     */
    public IdentityResponse identity() {
        return new IdentityResponse(keys.instanceId(), keys.publicKeyBase64(), pings.selfBaseUrl());
    }

    public List<PeerResponse> peers() {
        return peers.findAll().stream().map(DiscoveryAdminService::toResponse).toList();
    }

    /**
     * Knocks on a peer's door and reports what happened.
     *
     * <p>Whatever stopped it is the whole answer here, because an operator diagnosing a peer that
     * will not connect is the one person who needs to know which of five things went wrong.
     */
    public DiscoveryInfoResponse probe(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw DiscoveryRefusal.PROBE_NEEDS_AN_ADDRESS.raise();
        }
        return reachPeer(baseUrl);
    }

    /**
     * Adds a peer by hand, after it has answered as one and named the key it is recognised by.
     *
     * <p>It is pinged at once, so its neighbourhood starts coming in; a ping that fails does not
     * take the peer back out.
     */
    public PeerResponse addPeer(AddPeerRequest request) {
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw DiscoveryRefusal.PEER_NEEDS_AN_ADDRESS.raise();
        }
        var info = reachPeer(request.baseUrl());
        String publicKey = info.publicKey();
        if (publicKey == null) {
            throw AdminRefusal.PEER_NAMED_NO_KEY.raise(
                    "The address answered as a peer would, but named no key to recognise it by");
        }
        String expectedKey = request.expectedPublicKey();
        if (expectedKey != null && !expectedKey.isBlank() && !expectedKey.equals(publicKey)) {
            throw DiscoveryRefusal.PEER_KEY_NOT_THE_EXPECTED_ONE.raise();
        }
        if (!info.discoveryEnabled()) {
            throw DiscoveryRefusal.PEER_DOES_NOT_WANT_DISCOVERY.raise();
        }
        var peer = peers.upsert(publicKey, info.baseUrl(), info.instanceId(), PeerSource.MANUAL, null);
        try {
            pings.sendPing(peer);
        } catch (RuntimeException e) {
            log.debug("The first ping to the new peer {} failed", peer.baseUrl(), e);
        }
        return toResponse(peer);
    }

    /**
     * Fetches a peer's discovery card, refusing with the reason it could not be had.
     */
    private DiscoveryInfoResponse reachPeer(String baseUrl) {
        var probe = httpClient.probe(baseUrl, "/api/v1/public/discovery/info", DiscoveryInfoResponse.class);
        var answer = probe.value();
        if (answer == null) throw AdminRefusal.PEER_DID_NOT_ANSWER.raise(probe.problem());
        return answer;
    }

    /**
     * @return whether there was such a peer to remove
     */
    public boolean deletePeer(String publicKey) {
        return peers.delete(publicKey);
    }

    public PeerResponse upvote(String publicKey) {
        return changeExistingPeer(publicKey, reputation::upvote);
    }

    public PeerResponse downvote(String publicKey) {
        return changeExistingPeer(publicKey, reputation::downvote);
    }

    public PeerResponse block(String publicKey) {
        return changeExistingPeer(publicKey, key -> peers.setBlocked(key, true));
    }

    public PeerResponse unblock(String publicKey) {
        return changeExistingPeer(publicKey, key -> peers.setBlocked(key, false));
    }

    /**
     * Changes a peer that has to exist before and after the change, and answers it as it now stands.
     */
    private PeerResponse changeExistingPeer(String publicKey, Consumer<String> change) {
        peers.findByPublicKey(publicKey).orElseThrow(DiscoveryRefusal.PEER_NOT_HERE::raise);
        change.accept(publicKey);
        return toResponse(
                peers.findByPublicKey(publicKey).orElseThrow(DiscoveryRefusal.PEER_NOT_HERE_AFTER_CHANGE::raise));
    }

    public void pingNow(String publicKey) {
        pings.sendPing(peers.findByPublicKey(publicKey).orElseThrow(DiscoveryRefusal.PEER_NOT_HERE_ON_PING::raise));
    }

    /**
     * Pings every usable peer and fetches every station card again, now rather than on the schedule.
     */
    public DiscoverNowResponse discoverNow() {
        int pinged = 0;
        for (var peer : peers.findUsable()) {
            pings.sendPing(peer);
            pinged++;
        }
        return new DiscoverNowResponse(pinged, stationFetcher.refreshAll());
    }

    /**
     * @return how many federation partners were added as peers
     */
    public int seedFromFederation() {
        return federationPartnerSeeder.seedFromFederationPartners();
    }

    public List<BlocklistResponse> blocklist() {
        return blocklist.findAll().stream()
                .map(e -> new BlocklistResponse(e.value(), e.kind(), e.note(), e.createdAt()))
                .toList();
    }

    public void addToBlocklist(BlocklistRequest request) {
        if (request.value() == null || request.value().isBlank() || request.kind() == null) {
            throw DiscoveryRefusal.BLOCKLIST_ENTRY_INCOMPLETE.raise();
        }
        blocklist.add(request.kind(), request.value(), request.note());
    }

    /**
     * @return whether there was such an entry to remove
     */
    public boolean removeFromBlocklist(String value) {
        return blocklist.remove(value);
    }

    public record IdentityResponse(String instanceId, String publicKey, String baseUrl) {}

    public record AddPeerRequest(String baseUrl, String expectedPublicKey) {}

    public record PeerResponse(
            String publicKey,
            String baseUrl,
            String instanceId,
            Instant firstSeenAt,
            Instant lastSeenAt,
            @Nullable Instant lastPingedAt,
            @Nullable Instant lastReachedAt,
            boolean reachable,
            PeerSource source,
            @Nullable String introducedBy,
            int reputation,
            boolean blocked) {}

    public record DiscoverNowResponse(int pingsDispatched, int stationsFetched) {}

    public record BlocklistRequest(String value, BlocklistKind kind, String note) {}

    public record BlocklistResponse(
            String value, BlocklistKind kind, @Nullable String note, Instant createdAt) {}
}
