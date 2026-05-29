/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.StationUidResolver;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
import java.time.Instant;
import java.util.List;

/**
 * HTTP client for cross-instance federation communication.
 * Calls remote federation endpoints and signs requests using {@link FederationSigningService}.
 * The remote host is determined per-partner from the {@code remote_host} field.
 * <p>
 * All public methods accept and return typed objects. JSON serialization/deserialization
 * is handled internally — callers never deal with raw JSON strings.
 */
@Singleton
public class FederationHttpClient {
    private static final Logger log = LoggerFactory.getLogger(FederationHttpClient.class);

    private final HttpClient httpClient;
    private final FederationSigningService signingService;
    private final StationRepository stationRepository;
    private final JsonMapper mapper;

    @Inject
    public FederationHttpClient(FederationSigningService signingService, StationRepository stationRepository) {
        this.signingService = signingService;
        this.stationRepository = stationRepository;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    private String resolveStationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("");
    }

    // -- Generic typed methods --

    /**
     * Performs a signed GET and deserializes the response as a single typed object.
     * Returns null on error or non-2xx status.
     */
    public <T> T get(
            String remoteHost, String path, int localStationId, String localPrivateKeyBase64, Class<T> responseType) {
        try {
            var response = signedGet(apiUrl(remoteHost) + path, localStationId, localPrivateKeyBase64);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.warn("Signed GET {} failed: HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed GET {} on {}", path, remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed GET and deserializes the response as a list of typed objects.
     * Returns an empty list on error or non-200 status.
     */
    public <T> List<T> getList(
            String remoteHost, String path, int localStationId, String localPrivateKeyBase64, Class<T> elementType) {
        try {
            var response = signedGet(apiUrl(remoteHost) + path, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn("Signed GET list {} failed: HTTP {}", path, response.statusCode());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch list from {} {}", remoteHost, path, e);
            return List.of();
        }
    }

    /**
     * Performs a signed POST with a request body and deserializes the response as a typed object.
     * The request body is serialized to JSON internally.
     * Returns null on error or non-2xx status.
     */
    public <T> T post(
            String remoteHost,
            String path,
            Object requestBody,
            int localStationId,
            String localPrivateKeyBase64,
            Class<T> responseType) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = signedPost(apiUrl(remoteHost) + path, jsonBody, localStationId, localPrivateKeyBase64);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.warn("Signed POST {} failed: HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed POST {} on {}", path, remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed POST with a request body and deserializes the response as a list of typed objects.
     * The request body is serialized to JSON internally.
     * Returns an empty list on error or non-2xx status.
     */
    public <T> List<T> postList(
            String remoteHost,
            String path,
            Object requestBody,
            int localStationId,
            String localPrivateKeyBase64,
            Class<T> elementType) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = signedPost(apiUrl(remoteHost) + path, jsonBody, localStationId, localPrivateKeyBase64);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
                return mapper.readValue(response.body(), type);
            }
            log.warn("Signed POST list {} failed: HTTP {}", path, response.statusCode());
            return List.of();
        } catch (Exception e) {
            log.error("Failed signed POST list {} on {}", path, remoteHost, e);
            return List.of();
        }
    }

    /**
     * Performs a signed POST with a request body, returning true on 2xx success.
     * The request body is serialized to JSON internally.
     */
    public boolean post(
            String remoteHost, String path, Object requestBody, int localStationId, String localPrivateKeyBase64) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = signedPost(apiUrl(remoteHost) + path, jsonBody, localStationId, localPrivateKeyBase64);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed POST {} on {}", path, remoteHost, e);
            return false;
        }
    }

    /**
     * Performs a signed PUT with a request body and deserializes the response as a typed object.
     * The request body is serialized to JSON internally.
     * Returns null on error or non-2xx status.
     */
    public <T> T put(
            String remoteHost,
            String path,
            Object requestBody,
            int localStationId,
            String localPrivateKeyBase64,
            Class<T> responseType) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = signedPut(apiUrl(remoteHost) + path, jsonBody, localStationId, localPrivateKeyBase64);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return mapper.readValue(response.body(), responseType);
            }
            log.warn("Signed PUT {} failed: HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed PUT {} on {}", path, remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed PUT with a request body, returning true on 2xx success.
     * The request body is serialized to JSON internally.
     */
    public boolean put(
            String remoteHost, String path, Object requestBody, int localStationId, String localPrivateKeyBase64) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = signedPut(apiUrl(remoteHost) + path, jsonBody, localStationId, localPrivateKeyBase64);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed PUT {} on {}", path, remoteHost, e);
            return false;
        }
    }

    /**
     * Performs a signed DELETE without a request body, returning true on 2xx success.
     */
    public boolean delete(String remoteHost, String path, int localStationId, String localPrivateKeyBase64) {
        try {
            var response = signedDelete(apiUrl(remoteHost) + path, "", localStationId, localPrivateKeyBase64);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed DELETE {} on {}", path, remoteHost, e);
            return false;
        }
    }

    /**
     * Performs a signed DELETE with a request body, returning true on 2xx success.
     * The request body is serialized to JSON internally.
     */
    public boolean delete(
            String remoteHost, String path, Object requestBody, int localStationId, String localPrivateKeyBase64) {
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            var response = signedDelete(apiUrl(remoteHost) + path, jsonBody, localStationId, localPrivateKeyBase64);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed DELETE {} on {}", path, remoteHost, e);
            return false;
        }
    }

    // -- Internal HTTP primitives --

    /**
     * Converts a base URL like "https://ember.example.com" to the API prefix.
     */
    private String apiUrl(String remoteHost) {
        // Strip trailing slash if present
        String host = remoteHost.endsWith("/") ? remoteHost.substring(0, remoteHost.length() - 1) : remoteHost;
        return host + "/api/v1";
    }

    private HttpResponse<String> signedGet(String url, int localStationId, String localPrivateKeyBase64)
            throws Exception {
        String timestampStr = Instant.now().toString();
        var privateKey = signingService.decodePrivateKey(localPrivateKeyBase64);
        String signature = signingService.sign("", timestampStr, privateKey);
        String stationUid = StationUidResolver.instance().resolveToString(localStationId);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Federation-Station-Id", stationUid)
                .header("X-Federation-Station-Name", resolveStationName(localStationId))
                .header("X-Federation-Signature", signature)
                .header("X-Federation-Timestamp", timestampStr)
                .header("X-Federation-Version", FederationService.FEDERATION_VERSION)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> signedPost(String url, String body, int localStationId, String localPrivateKeyBase64)
            throws Exception {
        String timestampStr = Instant.now().toString();
        var privateKey = signingService.decodePrivateKey(localPrivateKeyBase64);
        String signature = signingService.sign(body, timestampStr, privateKey);
        String stationUid = StationUidResolver.instance().resolveToString(localStationId);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Federation-Station-Id", stationUid)
                .header("X-Federation-Station-Name", resolveStationName(localStationId))
                .header("X-Federation-Signature", signature)
                .header("X-Federation-Timestamp", timestampStr)
                .header("X-Federation-Version", FederationService.FEDERATION_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> signedPut(String url, String body, int localStationId, String localPrivateKeyBase64)
            throws Exception {
        String timestampStr = Instant.now().toString();
        var privateKey = signingService.decodePrivateKey(localPrivateKeyBase64);
        String signature = signingService.sign(body, timestampStr, privateKey);
        String stationUid = StationUidResolver.instance().resolveToString(localStationId);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Federation-Station-Id", stationUid)
                .header("X-Federation-Station-Name", resolveStationName(localStationId))
                .header("X-Federation-Signature", signature)
                .header("X-Federation-Timestamp", timestampStr)
                .header("X-Federation-Version", FederationService.FEDERATION_VERSION)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> signedDelete(String url, String body, int localStationId, String localPrivateKeyBase64)
            throws Exception {
        String timestampStr = Instant.now().toString();
        var privateKey = signingService.decodePrivateKey(localPrivateKeyBase64);
        String signature = signingService.sign(body, timestampStr, privateKey);
        String stationUid = StationUidResolver.instance().resolveToString(localStationId);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Federation-Station-Id", stationUid)
                .header("X-Federation-Station-Name", resolveStationName(localStationId))
                .header("X-Federation-Signature", signature)
                .header("X-Federation-Timestamp", timestampStr)
                .header("X-Federation-Version", FederationService.FEDERATION_VERSION)
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
