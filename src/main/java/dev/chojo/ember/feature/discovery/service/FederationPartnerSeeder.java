/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryInfoResponse;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * On startup (and on demand), walks active federation partners and inserts them into the
 * peer registry as {@link PeerSource#BOOTSTRAP} seeds. Each partner's discovery public key
 * is learned by probing its {@code /public/discovery/info} endpoint - discovery and
 * federation use independent keys.
 *
 * <p>Triggered as an eager singleton with a startup delay so QueryConfiguration and Javalin
 * have time to come up first.
 */
@Singleton
public class FederationPartnerSeeder {
    private static final Logger log = LoggerFactory.getLogger(FederationPartnerSeeder.class);
    private static final String INFO_PATH = "/api/v1/public/discovery/info";

    private final FederationRepository federationRepository;
    private final DiscoveryPeerRepository peerRepository;
    private final DiscoveryHttpClient httpClient;

    @Inject
    public FederationPartnerSeeder(
            FederationRepository federationRepository,
            DiscoveryPeerRepository peerRepository,
            DiscoveryHttpClient httpClient) {
        this.federationRepository = federationRepository;
        this.peerRepository = peerRepository;
        this.httpClient = httpClient;

        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "discovery-fed-seeder");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(this::seedFromFederationPartners, 2, TimeUnit.MINUTES);
    }

    /**
     * One-shot seeding pass. Idempotent - already-known peers stay where they are.
     *
     * @return number of new peers inserted
     */
    public int seedFromFederationPartners() {
        int added = 0;
        try {
            var partners = federationRepository.findAllActiveRemotePartners();
            Set<String> uniqueHosts = new HashSet<>();
            for (var partner : partners) {
                if (partner.remoteHost() == null || !uniqueHosts.add(partner.remoteHost())) continue;
                if (probeAndInsert(partner.remoteHost())) added++;
            }
            if (added > 0) {
                log.info("Seeded {} discovery peer(s) from federation partners", added);
            }
        } catch (Exception e) {
            log.warn("Federation partner seeding failed: {}", e.getMessage());
        }
        return added;
    }

    private boolean probeAndInsert(String baseUrl) {
        try {
            var info = httpClient.get(baseUrl, INFO_PATH, DiscoveryInfoResponse.class);
            if (info == null || info.publicKey() == null) {
                log.debug("Federation partner {} has no discovery info endpoint", baseUrl);
                return false;
            }
            if (!info.discoveryEnabled()) {
                log.debug("Federation partner {} reports discoveryEnabled=false; skipping", baseUrl);
                return false;
            }
            // upsert is a no-op if we already know this public key.
            boolean existed = peerRepository.findByPublicKey(info.publicKey()).isPresent();
            peerRepository.upsert(info.publicKey(), info.baseUrl(), info.instanceId(), PeerSource.BOOTSTRAP, null);
            return !existed;
        } catch (Exception e) {
            log.debug("Probe of {} failed: {}", baseUrl, e.getMessage());
            return false;
        }
    }
}
