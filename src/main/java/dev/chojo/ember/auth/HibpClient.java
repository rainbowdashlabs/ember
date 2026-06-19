/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.HibpSettings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Client for the Have I Been Pwned "k-anonymity" pwned-passwords range API.
 * <p>
 * The plaintext is hashed locally with SHA-1; only the first five hex characters
 * of the digest are sent to the API, which returns a newline-delimited list of
 * suffix + count pairs. The full hash never leaves the process, so the lookup
 * does not leak the password being checked.
 * <p>
 * The client is fail-open: any network error, non-200 response, or unparseable
 * body returns {@code false} (not pwned) so an outage of the third-party
 * service cannot block legitimate password changes or logins.
 */
@Singleton
public class HibpClient {
    private static final Logger log = LoggerFactory.getLogger(HibpClient.class);

    private final HibpSettings config;
    private final HttpClient httpClient;

    @Inject
    public HibpClient(Auth authConfig) {
        this(authConfig.hibp());
    }

    /** Visible-for-testing constructor that lets tests inject a stub config. */
    public HibpClient(HibpSettings config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, config.timeoutSeconds())))
                .build();
    }

    /**
     * Returns {@code true} when {@code plaintext} appears in HIBP's pwned-password
     * corpus. Returns {@code false} when HIBP is disabled by configuration, the
     * lookup fails, or the password is not present in any breach known to HIBP.
     */
    public boolean isPwned(String plaintext) {
        if (!config.enabled() || plaintext == null || plaintext.isEmpty()) {
            return false;
        }
        try {
            String sha1Hex = sha1Hex(plaintext);
            String prefix = sha1Hex.substring(0, 5);
            String suffix = sha1Hex.substring(5);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint() + prefix))
                    .timeout(Duration.ofSeconds(Math.max(1, config.timeoutSeconds())))
                    .header("User-Agent", "Ember-Security/1.0")
                    .header("Add-Padding", "true")
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("HIBP returned HTTP {} for prefix {} — failing open", response.statusCode(), prefix);
                return false;
            }
            return matches(response.body(), suffix);
        } catch (Exception e) {
            log.warn("HIBP lookup failed — failing open: {}", e.getMessage());
            return false;
        }
    }

    private static boolean matches(String body, String suffix) {
        for (String line : body.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String candidate = line.substring(0, colon).trim();
            if (candidate.equalsIgnoreCase(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String sha1Hex(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
