/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.webhook.service;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.webhook.repository.WebhookKeyRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * The key an outside tool presents when it reports something to Ember.
 *
 * <p>Ember generates these itself. An operator is never asked to invent a secret and never has to
 * keep one safe on paper: they copy a finished address out of the settings and paste it wherever it
 * is wanted. One key answers for the whole instance; a station has its own, so a station running its
 * own mail provider - or any other tool of its own - points it at an address that can only ever
 * touch that station.
 *
 * <p>Not being provider-specific is deliberate. Brevo reports bounces today; the next provider, or
 * something else entirely, reports through the same door with the same key.
 */
@Singleton
public class WebhookKeyService {
    private static final Logger log = LoggerFactory.getLogger(WebhookKeyService.class);

    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Conf conf;
    private final WebhookKeyRepository repository;

    @Inject
    public WebhookKeyService(Conf conf, WebhookKeyRepository repository) {
        this.conf = conf;
        this.repository = repository;
    }

    /**
     * Who a presented key speaks for.
     *
     * @param stationId the station, or null when the key is the instance's own
     */
    public record WebhookScope(Integer stationId) {

        /**
         * Whether this scope covers the whole instance rather than a single station.
         */
        public boolean isInstance() {
            return stationId == null;
        }
    }

    /**
     * The instance key, generating it the first time it is wanted.
     */
    public String instanceKey() {
        String current = conf.main().mailing().webhookSecret();
        if (current != null && !current.isBlank()) return current;
        String generated = generate();
        conf.main().mailing().webhookSecret(generated);
        conf.save();
        log.info("Generated the instance webhook key");
        return generated;
    }

    /**
     * The key of a station, generating it the first time it is wanted.
     */
    public String stationKey(int stationId) {
        return repository.findByStation(stationId).orElseGet(() -> {
            String generated = generate();
            repository.store(stationId, generated);
            log.info("Generated the webhook key of station {}", stationId);
            return generated;
        });
    }

    /**
     * Replaces a key with a fresh one. The old address stops being accepted at once, so whatever
     * was pointed at it has to be pointed at the new one.
     *
     * @param stationId the station whose key to replace, or null for the instance key
     * @return the new key
     */
    public String regenerate(Integer stationId) {
        String generated = generate();
        if (stationId == null) {
            conf.main().mailing().webhookSecret(generated);
            conf.save();
            log.info("Replaced the instance webhook key");
        } else {
            repository.store(stationId, generated);
            log.info("Replaced the webhook key of station {}", stationId);
        }
        return generated;
    }

    /**
     * Works out who a presented key speaks for.
     *
     * <p>The instance key is compared in constant time so a caller cannot feel its way to the
     * value; a station key is looked up, which is safe because there is nothing to feel for in a
     * random 256-bit token.
     *
     * @param presented the key taken from the address
     * @return the scope it authorises, or empty when it authorises nothing
     */
    public Optional<WebhookScope> resolve(String presented) {
        if (presented == null || presented.isBlank()) return Optional.empty();
        String instance = conf.main().mailing().webhookSecret();
        if (instance != null
                && !instance.isBlank()
                && MessageDigest.isEqual(
                        presented.getBytes(StandardCharsets.UTF_8), instance.getBytes(StandardCharsets.UTF_8))) {
            return Optional.of(new WebhookScope(null));
        }
        return repository.findStation(presented).map(WebhookScope::new);
    }

    /**
     * The address a provider or tool reports to, ready to be pasted into its settings.
     *
     * @param baseUrl   the public address of this instance, without a trailing slash
     * @param stationId the station the address should speak for, or null for the instance
     * @param path      what is being reported, e.g. {@code mail/brevo}
     */
    public String webhookUrl(String baseUrl, Integer stationId, String path) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String key = stationId == null ? instanceKey() : stationKey(stationId);
        return base + "/api/v1/public/webhooks/" + key + "/" + path;
    }

    private static String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
