/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.route.FederationRemoteRoutes;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * On startup, pings all active remote federation partners to exchange version information.
 * The signed request carries our version in the X-Federation-Version header (set by FederationHttpClient),
 * and the response contains the partner's version which we store.
 */
@Singleton
public class FederationVersionBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(FederationVersionBroadcaster.class);

    private final FederationRepository repository;
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;

    @Inject
    public FederationVersionBroadcaster(
            FederationRepository repository, FederationHttpClient httpClient, StationRepository stationRepository) {
        this.repository = repository;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;

        // Delay to let the app finish booting (QueryConfiguration, Javalin, etc.)
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "federation-version-broadcast");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(this::broadcastVersion, 2, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    private void broadcastVersion() {
        try {
            var partners = repository.findAllActiveRemotePartners();
            if (partners.isEmpty()) return;

            log.info("Broadcasting federation version to {} remote partner(s)", partners.size());
            int updated = 0;

            for (var partner : partners) {
                try {
                    var station =
                            stationRepository.findById(partner.stationId()).orElse(null);
                    if (station == null || station.federationPrivateKey() == null) continue;

                    var response = httpClient.get(
                            partner.remoteHost(),
                            "/remote/federation/ping",
                            partner.stationId(),
                            station.federationPrivateKey(),
                            FederationRemoteRoutes.VersionPingResponse.class);

                    if (response != null && response.version() != null) {
                        String remoteVersion = response.version();
                        if (!remoteVersion.equals(partner.federationVersion())) {
                            repository.updateFederationVersion(partner.id(), remoteVersion);
                            updated++;
                        }
                    }
                } catch (Exception e) {
                    log.debug(
                            "Failed to ping partner {} at {}: {}", partner.id(), partner.remoteHost(), e.getMessage());
                }
            }

            if (updated > 0) {
                log.info("Updated federation version for {} remote partner(s)", updated);
            }
        } catch (Exception e) {
            log.error("Error during federation version broadcast", e);
        }
    }
}
