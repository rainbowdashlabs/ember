/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for discovery-protocol traffic.
 *
 * <p>Two flavours: <em>unauthenticated GETs</em> for {@code /public/discovery/info} and
 * {@code /public/discovery/stations}, and <em>signed POSTs</em> for {@code /discovery/ping}
 * and {@code /discovery/peers}. Signatures use the per-instance Ed25519 key via
 * {@link DiscoverySigningService}.
 *
 * <p>Distinct from {@code FederationHttpClient} — that one ties every request to a specific
 * federation partner's RSA key pair; discovery uses a single instance-wide identity.
 *
 * <p>The embedded {@link JsonMapper} intentionally disables
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} so a peer running a newer discovery protocol
 * version can add fields to a response without breaking older peers.
 */
@Singleton
public class DiscoveryHttpClient {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryHttpClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final DiscoverySigningService signingService;
    private final JsonMapper mapper;

    @Inject
    public DiscoveryHttpClient(DiscoverySigningService signingService) {
        this.signingService = signingService;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    private static String joinUrl(String baseUrl, String path) {
        String host = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return host + path;
    }

    /**
     * Performs an unauthenticated GET and deserializes the response. Returns {@code null} on
     * non-2xx or transport failure.
     */
    public <T> T get(String baseUrl, String path, Class<T> responseType) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(joinUrl(baseUrl, path)))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.debug("Discovery GET {} on {} returned HTTP {}", path, baseUrl, response.statusCode());
            return null;
        } catch (Exception e) {
            log.debug("Discovery GET {} on {} failed: {}", path, baseUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Sends a signed POST. Returns true on 2xx, false otherwise.
     *
     * @param baseUrl peer base URL (without {@code /api/v1})
     * @param path    API path including the {@code /api/v1} prefix
     * @param body    Java object to serialize as the request body
     */
    public boolean signedPost(String baseUrl, String path, Object body) {
        try {
            String json = mapper.writeValueAsString(body);
            String signature = signingService.sign(json);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(joinUrl(baseUrl, path)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(DiscoverySigningService.SIGNATURE_HEADER, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.debug("Discovery POST {} on {} failed: {}", path, baseUrl, e.getMessage(), e);
            return false;
        }
    }
}
