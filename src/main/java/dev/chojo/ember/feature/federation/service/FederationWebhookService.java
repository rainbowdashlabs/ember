/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fires webhook notifications to federation partners when shared content changes.
 * Retries failed deliveries with exponential backoff (1s, 2s, 4s, max 3 retries).
 */
@Singleton
public class FederationWebhookService {
    private static final Logger log = LoggerFactory.getLogger(FederationWebhookService.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration[] RETRY_DELAYS = {Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)
    };

    private final FederationRepository repository;
    private final FederationSigningService signingService;
    private final FederationService federationService;
    private final RemoteUrlValidator urlValidator;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Inject
    public FederationWebhookService(
            FederationRepository repository,
            FederationSigningService signingService,
            FederationService federationService,
            RemoteUrlValidator urlValidator) {
        this.repository = repository;
        this.signingService = signingService;
        this.federationService = federationService;
        this.urlValidator = urlValidator;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.objectMapper = JsonMapper.builder().build();
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Fires a webhook event to all active partners of the given station that have a webhook URL.
     */
    // Available for federation event delivery — not yet triggered from change listeners
    public void fireEvent(int stationId, WebhookEvent event, Object payload) {
        var partners = federationService.findPartners(stationId);
        for (var partner : partners) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            String webhookUrl = repository.getWebhookUrl(partner.id());
            if (webhookUrl == null || webhookUrl.isBlank()) continue;

            executor.submit(() -> deliverWebhook(partner, webhookUrl, event, payload));
        }
    }

    /**
     * Fires a webhook event to a specific partner.
     */
    public void fireEventToPartner(int partnerId, WebhookEvent event, Object payload) {
        var partner = repository.findPartnerById(partnerId);
        if (partner.isEmpty()) return;
        var p = partner.get();
        if (p.status() != FederationPartner.FederationStatus.ACTIVE) return;
        String webhookUrl = repository.getWebhookUrl(p.id());
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        executor.submit(() -> deliverWebhook(p, webhookUrl, event, payload));
    }

    /**
     * Delivers a webhook with retry logic.
     */
    private void deliverWebhook(FederationPartner partner, String webhookUrl, WebhookEvent event, Object payload) {
        if (!urlValidator.isAllowed(webhookUrl)) {
            log.warn("Skipping webhook delivery for partner {} — URL rejected by RemoteUrlValidator", partner.id());
            return;
        }
        try {
            var body = objectMapper.writeValueAsString(new WebhookBody(
                    event.name(), partner.stationId(), Instant.now().toString(), payload));

            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                if (attempt > 0) {
                    Thread.sleep(RETRY_DELAYS[attempt - 1].toMillis());
                }

                try {
                    var timestamp = Instant.now().toString();
                    var uri = URI.create(webhookUrl);
                    String pathWithQuery = FederationSigningService.canonicalPathWithQuery(uri);
                    String nonce = UUID.randomUUID().toString();
                    String signature = "";
                    if (partner.publicKey() != null) {
                        var privateKey = signingService.decodePrivateKey(partner.publicKey());
                        signature = signingService.sign(
                                "POST", pathWithQuery, partner.partnerStationId(), nonce, body, timestamp, privateKey);
                    }

                    var request = HttpRequest.newBuilder()
                            .uri(uri)
                            .header("Content-Type", "application/json")
                            .header("X-Federation-Station-Id", String.valueOf(partner.stationId()))
                            .header("X-Federation-Timestamp", timestamp)
                            .header("X-Federation-Signature", signature)
                            .header("X-Federation-Nonce", nonce)
                            .header("X-Federation-Event", event.name())
                            .header("X-Federation-Version", FederationService.FEDERATION_VERSION)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(15))
                            .build();

                    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        log.debug(
                                "Webhook delivered to partner {} at {}: {} (attempt {})",
                                partner.id(),
                                webhookUrl,
                                event,
                                attempt + 1);
                        return;
                    }
                    log.warn(
                            "Webhook delivery failed for partner {} at {} with status {} (attempt {}/{})",
                            partner.id(),
                            webhookUrl,
                            response.statusCode(),
                            attempt + 1,
                            MAX_RETRIES + 1);
                } catch (Exception e) {
                    log.warn(
                            "Webhook delivery error for partner {} at {} (attempt {}/{}): {}",
                            partner.id(),
                            webhookUrl,
                            attempt + 1,
                            MAX_RETRIES + 1,
                            e.getMessage());
                }
            }
            log.error("Webhook delivery exhausted retries for partner {} at {}: {}", partner.id(), webhookUrl, event);
        } catch (Exception e) {
            log.error("Failed to prepare webhook payload for partner {}", partner.id(), e);
        }
    }

    public enum WebhookEvent {
        CONTENT_UPDATED,
        CONTENT_DELETED,
        SHARE_CHANGED,
        FEDERATION_SUSPENDED,
        EVENT_REGISTRATION_ACCEPTED,
        EVENT_REGISTRATION_DENIED,
        MEMBER_NAME_CHANGED,
        BOARD_TICKET_CHANGED,
        BOARD_MENTION,
        BOARD_ASSIGNMENT,
        BOARD_UNASSIGNMENT,
        BOARD_RENAMED,
        BOARD_UNSHARED,
        BOARD_SHARE_MODE_CHANGED
    }

    record WebhookBody(String event, int stationId, String timestamp, Object data) {}
}
