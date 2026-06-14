/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryStationsResponse;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the {@code /public/discovery/stations} endpoint of every reachable peer and
 * refreshes the local cache.
 *
 * <p>Failures bump {@code reachable=false} and decrement reputation. Successful fetches
 * upsert each card and prune entries the peer no longer reports.
 */
@Singleton
public class DiscoveryStationFetcher {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryStationFetcher.class);
    private static final String STATIONS_PATH = "/api/v1/public/discovery/stations";

    private final DiscoveryHttpClient httpClient;
    private final DiscoveryPeerRepository peerRepository;
    private final DiscoveryStationCacheRepository cacheRepository;
    private final DiscoveryReputationService reputationService;
    private final DiscoverySettingsService settingsService;

    @Inject
    public DiscoveryStationFetcher(
            DiscoveryHttpClient httpClient,
            DiscoveryPeerRepository peerRepository,
            DiscoveryStationCacheRepository cacheRepository,
            DiscoveryReputationService reputationService,
            DiscoverySettingsService settingsService) {
        this.httpClient = httpClient;
        this.peerRepository = peerRepository;
        this.cacheRepository = cacheRepository;
        this.reputationService = reputationService;
        this.settingsService = settingsService;
    }

    /**
     * Refreshes the station cache for one peer. Safe to call concurrently for different
     * peers; do not call concurrently for the same peer.
     *
     * @return number of cards fetched, or -1 on failure
     */
    public int refresh(DiscoveryPeer peer) {
        if (!settingsService.isEnabled()) return 0;
        if (peer.blocked()) return 0;

        var response = httpClient.get(peer.baseUrl(), STATIONS_PATH, DiscoveryStationsResponse.class);
        if (response == null) {
            peerRepository.markUnreachable(peer.publicKey());
            reputationService.recordTimeout(peer.publicKey());
            return -1;
        }

        Instant now = Instant.now();
        List<String> presentUids = new ArrayList<>();
        List<DiscoveryStationCard> cards = response.stations() != null ? response.stations() : List.of();
        for (var card : cards) {
            if (card == null || card.stationUid() == null) continue;
            cacheRepository.upsert(peer.publicKey(), card, now);
            presentUids.add(card.stationUid());
        }
        cacheRepository.deleteMissing(peer.publicKey(), presentUids);
        peerRepository.markReached(peer.publicKey(), now);
        reputationService.recordSuccessfulFetch(peer.publicKey());
        return cards.size();
    }

    /**
     * Walks every reachable peer once. Errors per peer are isolated.
     */
    public int refreshAll() {
        if (!settingsService.isEnabled()) return 0;
        int total = 0;
        for (var peer : peerRepository.findReachable()) {
            try {
                int n = refresh(peer);
                if (n > 0) total += n;
            } catch (Exception e) {
                log.warn("Failed to refresh stations for peer {}: {}", peer.baseUrl(), e.getMessage());
            }
        }
        return total;
    }
}
