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
import java.util.Map;

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
            String url = baseUrl() + "/federation/remote/lending/messages/" + requestId;
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
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

    /**
     * Fetches shared KB file summaries from a remote station.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchSharedKbFiles(int localStationId, String localPrivateKeyBase64) {
        try {
            String url = baseUrl() + "/federation/remote/kb/browse";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn("Failed to fetch shared KB files: HTTP {} - {}", response.statusCode(), response.body());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, Map.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch shared KB files", e);
            return List.of();
        }
    }

    /**
     * Fetches a KB file's content from a remote station.
     */
    public String fetchKbFileContent(int fileId, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = baseUrl() + "/federation/remote/kb/file/" + fileId + "/content";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn("Failed to fetch KB file content: HTTP {} - {}", response.statusCode(), response.body());
                return "";
            }
            @SuppressWarnings("unchecked")
            var map = mapper.readValue(response.body(), Map.class);
            return map.getOrDefault("content", "").toString();
        } catch (Exception e) {
            log.error("Failed to fetch KB file content for file {}", fileId, e);
            return "";
        }
    }

    /**
     * Fetches shared quiz catalog summaries from a remote station.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchSharedQuizCatalogs(int localStationId, String localPrivateKeyBase64) {
        try {
            String url = baseUrl() + "/federation/remote/quiz/catalogs";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn("Failed to fetch shared quiz catalogs: HTTP {} - {}", response.statusCode(), response.body());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, Map.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch shared quiz catalogs", e);
            return List.of();
        }
    }

    /**
     * Fetches shared protocol summaries from a remote station.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchSharedProtocols(int localStationId, String localPrivateKeyBase64) {
        try {
            String url = baseUrl() + "/federation/remote/protocols";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn("Failed to fetch shared protocols: HTTP {} - {}", response.statusCode(), response.body());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, Map.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch shared protocols", e);
            return List.of();
        }
    }

    private String baseUrl() {
        return "http://localhost:" + apiConfig.port() + "/api/v1";
    }

    private HttpResponse<String> signedGet(String url, int localStationId, String localPrivateKeyBase64)
            throws Exception {
        var timestamp = Instant.now();
        String timestampStr = timestamp.toString();
        var privateKey = signingService.decodePrivateKey(localPrivateKeyBase64);
        String signature = signingService.sign("", timestampStr, privateKey);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Federation-Station-Id", String.valueOf(localStationId))
                .header("X-Federation-Signature", signature)
                .header("X-Federation-Timestamp", timestampStr)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
