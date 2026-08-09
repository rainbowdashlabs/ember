/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches a remote partner's contract vector via the version ping and stores it on the
 * partner record. Called from the startup broadcast, and on demand whenever either side of
 * a partnership notices a vector that no longer matches — an incoming request with an
 * unexpected core hash, or an outgoing request rejected with a contract mismatch — so
 * stored vectors self-heal on first contact after a partner redeploys.
 */
@Singleton
public class FederationContractRefreshService {
    private static final Logger log = LoggerFactory.getLogger(FederationContractRefreshService.class);

    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);

    private final FederationRepository repository;
    private final StationRepository stationRepository;
    private final FederationHttpClient httpClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Instant> lastAttempt = new ConcurrentHashMap<>();

    @Inject
    public FederationContractRefreshService(
            FederationRepository repository, StationRepository stationRepository, FederationHttpClient httpClient) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.httpClient = httpClient;
    }

    /**
     * Pings the partner and stores the vector it answers with. Returns whether a vector was
     * received.
     */
    public boolean refresh(FederationPartner partner) {
        if (!partner.isRemote()) return false;
        var station = stationRepository.findById(partner.stationId()).orElse(null);
        if (station == null || station.federationPrivateKey() == null) return false;

        var response = httpClient.get(
                partner.remoteHost(),
                RemoteFederationRoutes.VERSION_PING.at(),
                partner.partnerStationId(),
                partner.stationId(),
                station.federationPrivateKey(),
                RemoteFederationRoutes.VersionPingResponse.class);
        if (response == null || response.contract() == null) return false;

        if (!response.contract().equals(partner.federationContract())) {
            repository.updateFederationContract(partner.id(), response.contract());
            log.info("Updated federation contract vector of partner {}", partner.id());
        }
        return true;
    }

    /**
     * Refreshes in the background, with at most one refresh in flight per partner and at
     * most one attempt per partner per minute. Mismatch detection re-arms on every request
     * for as long as a partner stays incompatible or unreachable, so without the interval a
     * degraded partner would draw one outbound ping — with a 10 second connect timeout —
     * per inbound request.
     */
    public void refreshAsync(FederationPartner partner) {
        if (!partner.isRemote() || recentlyAttempted(partner.id()) || !inFlight.add(partner.id())) return;
        lastAttempt.put(partner.id(), Instant.now());
        executor.submit(() -> {
            try {
                refresh(partner);
            } catch (Exception e) {
                log.debug("Federation contract refresh for partner {} failed: {}", partner.id(), e.getMessage());
            } finally {
                inFlight.remove(partner.id());
            }
        });
    }

    private boolean recentlyAttempted(int partnerId) {
        var attempt = lastAttempt.get(partnerId);
        return attempt != null && attempt.isAfter(Instant.now().minus(RETRY_INTERVAL));
    }

    /**
     * Background refresh addressed by the station pair an outgoing request was made for,
     * which is all the HTTP client knows about the partner it just called.
     */
    public void refreshAsync(int localStationId, UUID partnerStationUid) {
        repository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .ifPresent(this::refreshAsync);
    }
}
