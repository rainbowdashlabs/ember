/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.util.WebOrigins;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Which other instances may exchange requests to federate with this one.
 *
 * <p>Only instances this one knows from discovery: a request comes from a stranger until an instance
 * key vouches for it, and the only keys this instance has any reason to trust are those of its
 * discovery peers. A peer that is blocked, on the blocklist by key or by address, or distrusted by
 * its reputation does not count.
 */
@Singleton
public class PairRequestPeers {
    private final DiscoveryPeerRepository peers;
    private final DiscoveryBlocklistRepository blocklist;

    @Inject
    public PairRequestPeers(DiscoveryPeerRepository peers, DiscoveryBlocklistRepository blocklist) {
        this.peers = peers;
        this.blocklist = blocklist;
    }

    /**
     * How this instance stands towards the instance with the given discovery key.
     *
     * @param instanceKey the other instance's discovery key
     * @return whether it is known, and whether it may send requests
     */
    public Standing standingOf(String instanceKey) {
        if (blocklist.contains(BlocklistKind.PUBLIC_KEY, instanceKey)) return new Standing.Blocked();
        var peer = peers.findByPublicKey(instanceKey);
        if (peer.isEmpty()) return new Standing.Unknown();
        return usable(peer.get()) ? new Standing.Known(peer.get()) : new Standing.Blocked();
    }

    /**
     * The usable peer reached at the given address, as a pairing code names it.
     *
     * @param address the host, with the port where the instance does not sit on the standard one
     * @return the peer, or empty when no usable peer is reached there
     */
    public Optional<DiscoveryPeer> atAddress(String address) {
        return peers.findUsable().stream()
                .filter(peer -> WebOrigins.hostAndPort(peer.baseUrl()).equalsIgnoreCase(address))
                .filter(this::usable)
                .findFirst();
    }

    private boolean usable(DiscoveryPeer peer) {
        return !peer.blocked()
                && peer.reputation() > DiscoveryPeer.DISTRUSTED_REPUTATION
                && !blocklist.contains(BlocklistKind.PUBLIC_KEY, peer.publicKey())
                && !blocklist.contains(BlocklistKind.BASE_URL, peer.baseUrl());
    }

    /** How this instance stands towards another one. */
    public sealed interface Standing {
        /** A discovery peer that may send requests. */
        record Known(DiscoveryPeer peer) implements Standing {}

        /** An instance this one has never met through discovery. */
        record Unknown() implements Standing {}

        /** A peer that is blocked or distrusted here. */
        record Blocked() implements Standing {}
    }
}
