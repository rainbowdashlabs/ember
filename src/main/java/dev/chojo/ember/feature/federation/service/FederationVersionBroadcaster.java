/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.lifecycle.DelegatingTask;
import dev.chojo.ember.lifecycle.Schedule;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Pings all active remote federation partners to exchange contract vectors. The signed
 * request carries our core and surface hashes (set by FederationHttpClient), and the ping
 * response contains the partner's full vector which we store.
 * <p>
 * The sweep repeats rather than running once at startup: a partner whose vector is unknown
 * is treated as incompatible, which gates off every outbound feature request to it - so the
 * request-driven refresh triggers can never fire for that partner and a single missed ping
 * would pause the partnership until the next restart. The recurring sweep is the one path
 * that does not depend on traffic, so an unreachable or still-restarting partner heals on
 * its own once it answers.
 * <p>
 * Constructing it, which happens when the scheduled tasks are gathered at boot, also warms
 * {@link FederationContractVersions}, so the
 * reflective contract hash computation runs at boot instead of on whichever user-facing
 * request happens to touch it first.
 */
@Singleton
public class FederationVersionBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(FederationVersionBroadcaster.class);

    private static final Duration INITIAL_DELAY = Duration.ofMinutes(2);
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(15);

    private final FederationRepository repository;
    private final FederationContractRefreshService refreshService;

    @Inject
    public FederationVersionBroadcaster(
            FederationRepository repository, FederationContractRefreshService refreshService) {
        this.repository = repository;
        this.refreshService = refreshService;
        FederationContractVersions.current();
    }

    private void broadcastVersion() {
        try {
            var partners = repository.findAllActiveRemotePartners();
            if (partners.isEmpty()) return;

            int refreshed = 0;
            for (var partner : partners) {
                try {
                    if (refreshService.refresh(partner)) refreshed++;
                } catch (Exception e) {
                    log.debug(
                            "Failed to ping partner {} at {}: {}", partner.id(), partner.remoteHost(), e.getMessage());
                }
            }

            log.info(
                    "Exchanged federation contract vectors with {} of {} remote partner(s)",
                    refreshed,
                    partners.size());
        } catch (Exception e) {
            log.error("Error during federation version broadcast", e);
        }
    }

    /** Exchanges the contract vectors every fifteen minutes, the first time two minutes after the start. */
    @Singleton
    public static final class Task extends DelegatingTask {
        @Inject
        Task(FederationVersionBroadcaster broadcaster) {
            super(
                    "federation-version-broadcast",
                    Schedule.fixedDelay(INITIAL_DELAY, SWEEP_INTERVAL),
                    broadcaster::broadcastVersion);
        }
    }
}
