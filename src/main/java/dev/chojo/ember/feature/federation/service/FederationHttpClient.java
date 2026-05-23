/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

/**
 * HTTP client for cross-instance federation communication.
 * Calls remote federation endpoints and signs requests using {@link FederationSigningService}.
 * Used for cross-instance federation or when dev mode forces HTTP for same-instance calls.
 */
@Singleton
public class FederationHttpClient {
    private static final Logger log = LoggerFactory.getLogger(FederationHttpClient.class);

    private final HttpClient httpClient;
    private final FederationSigningService signingService;
    private final JsonMapper mapper;
    private final Api apiConfig;

    @Inject
    public FederationHttpClient(FederationSigningService signingService, Api apiConfig) {
        this.signingService = signingService;
        this.apiConfig = apiConfig;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = JsonMapper.builder().build();
    }

    /**
     * Fetches lending messages from a remote station via the federation HTTP endpoint.
     * For same-instance dev mode, calls localhost on the configured API port.
     *
     * @param requestId             the lending request ID
     * @param localStationId        the station ID of the calling station (sent in signature header)
     * @param localPrivateKeyBase64 the local station's Base64-encoded private key for request signing
     * @return list of messages from the remote station
     */
    public List<LendingMessage> fetchRemoteMessages(int requestId, int localStationId, String localPrivateKeyBase64) {
        try {
            String baseUrl = "http://localhost:" + apiConfig.port();
            String url = baseUrl + "/api/v1/federation/remote/lending/messages/" + requestId;

            var timestamp = Instant.now();
            String timestampStr = timestamp.toString();
            String body = "";

            var privateKey = signingService.decodePrivateKey(localPrivateKeyBase64);
            String signature = signingService.sign(body, timestampStr, privateKey);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Federation-Station-Id", String.valueOf(localStationId))
                    .header("X-Federation-Signature", signature)
                    .header("X-Federation-Timestamp", timestampStr)
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn(
                        "Failed to fetch remote lending messages: HTTP {} - {}",
                        response.statusCode(),
                        response.body());
                return List.of();
            }

            var type = mapper.getTypeFactory().constructCollectionType(List.class, LendingMessage.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch remote lending messages for request {}", requestId, e);
            return List.of();
        }
    }
}
