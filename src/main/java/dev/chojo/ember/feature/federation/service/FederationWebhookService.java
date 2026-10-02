/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.lifecycle.TaskScheduler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Pushes change notifications to federation partners on other instances.
 *
 * <p>A notification is a signed {@code POST} to one of the webhook endpoints the partner declares,
 * sent through {@link FederationHttpClient} under the local station's identity, exactly like every
 * other federated call. The target is the partner's {@code remote_host}; the endpoint path comes
 * from the declared contract. Delivery runs in the background and a failed attempt is retried with
 * backoff (1s, 2s, 4s) before it is given up.
 *
 * <p>Partners on this instance read the same database and have nothing to be told, so they are skipped.
 */
@Singleton
public class FederationWebhookService {
    private static final Logger log = LoggerFactory.getLogger(FederationWebhookService.class);
    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));

    private final FederationRepository repository;
    private final FederationHttpClient httpClient;
    private final Executor executor;
    private final List<Duration> retryDelays;

    @Inject
    public FederationWebhookService(
            FederationRepository repository, FederationHttpClient httpClient, TaskScheduler scheduler) {
        this(repository, httpClient, scheduler.executor(), RETRY_DELAYS);
    }

    /**
     * Builds the service with its own delivery executor and backoff, so a test can deliver on the
     * calling thread without waiting out the production delays.
     */
    public FederationWebhookService(
            FederationRepository repository,
            FederationHttpClient httpClient,
            Executor executor,
            List<Duration> retryDelays) {
        this.repository = repository;
        this.httpClient = httpClient;
        this.executor = executor;
        this.retryDelays = List.copyOf(retryDelays);
    }

    /**
     * Notifies one partner in the background. Nothing is sent when the partner is unknown, not
     * active, or lives on this instance.
     *
     * @param partnerId the local partner row to notify
     * @param request   the partner's webhook endpoint to call
     * @param body      the request body the endpoint declares
     */
    public void notifyPartner(int partnerId, FederationRequest request, Object body) {
        repository
                .findPartnerById(partnerId)
                .filter(partner -> partner.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(FederationPartner::isRemote)
                .ifPresent(partner -> executor.execute(() -> deliver(partner, request, body)));
    }

    private void deliver(FederationPartner partner, FederationRequest request, Object body) {
        if (!httpClient.canSign(partner.stationId())) {
            log.warn("Skipping webhook {} for partner {}: station has no federation key", request.path(), partner.id());
            return;
        }
        int attempts = retryDelays.size() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1 && !pause(retryDelays.get(attempt - 2))) return;
            if (httpClient.post(
                    partner.requireRemoteHost(), request, body, partner.partnerStationId(), partner.stationId())) {
                log.debug("Webhook {} delivered to partner {} (attempt {})", request.path(), partner.id(), attempt);
                return;
            }
            log.warn(
                    "Webhook {} to partner {} failed (attempt {}/{})", request.path(), partner.id(), attempt, attempts);
        }
        log.error("Webhook {} to partner {} exhausted its retries", request.path(), partner.id());
    }

    private static boolean pause(Duration delay) {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
