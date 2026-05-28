/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.StationUidResolver;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for cross-instance federation communication.
 * Calls remote federation endpoints and signs requests using {@link FederationSigningService}.
 * The remote host is determined per-partner from the {@code remote_host} field.
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
        this.mapper = JsonMapper.builder().build();
    }

    private String resolveStationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("");
    }

    public List<LendingMessage> fetchRemoteMessages(
            String remoteHost, int requestId, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/lending/messages/" + requestId;
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn(
                        "Failed to fetch remote lending messages from {}: HTTP {} - {}",
                        remoteHost,
                        response.statusCode(),
                        response.body());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, LendingMessage.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch remote lending messages from {} for request {}", remoteHost, requestId, e);
            return List.of();
        }
    }

    public List<RemoteKbSearchResult> searchKb(
            String remoteHost, int localStationId, String localPrivateKeyBase64, String query) {
        try {
            String url = apiUrl(remoteHost) + "/remote/kb/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) return List.of();
            var type = mapper.getTypeFactory().constructCollectionType(List.class, RemoteKbSearchResult.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to search KB on {}", remoteHost, e);
            return List.of();
        }
    }

    public record RemoteKbSearchResult(int id, String name, String description, String snippet) {}

    public List<RemoteKbFile> fetchSharedKbFiles(String remoteHost, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/kb/browse";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn(
                        "Failed to fetch shared KB files from {}: HTTP {} - {}",
                        remoteHost,
                        response.statusCode(),
                        response.body());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, RemoteKbFile.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch shared KB files from {}", remoteHost, e);
            return List.of();
        }
    }

    public String fetchKbFileContent(String remoteHost, int fileId, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/kb/file/" + fileId + "/content";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn(
                        "Failed to fetch KB file content from {}: HTTP {} - {}",
                        remoteHost,
                        response.statusCode(),
                        response.body());
                return "";
            }
            var remoteContent = mapper.readValue(response.body(), RemoteKbContent.class);
            return remoteContent.content() != null ? remoteContent.content() : "";
        } catch (Exception e) {
            log.error("Failed to fetch KB file content from {} for file {}", remoteHost, fileId, e);
            return "";
        }
    }

    public List<RemoteQuizCatalog> fetchSharedQuizCatalogs(
            String remoteHost, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/quiz/catalogs";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn(
                        "Failed to fetch shared quiz catalogs from {}: HTTP {} - {}",
                        remoteHost,
                        response.statusCode(),
                        response.body());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, RemoteQuizCatalog.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch shared quiz catalogs from {}", remoteHost, e);
            return List.of();
        }
    }

    public List<RemoteProtocol> fetchSharedProtocols(
            String remoteHost, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/protocols";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn(
                        "Failed to fetch shared protocols from {}: HTTP {} - {}",
                        remoteHost,
                        response.statusCode(),
                        response.body());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, RemoteProtocol.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch shared protocols from {}", remoteHost, e);
            return List.of();
        }
    }

    /**
     * Converts a base URL like "<a href="https://ember.example.com">...</a>" to the API prefix.
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

    /**
     * Performs a signed GET request and parses the response as a JSON list of maps.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> signedGetList(
            String remoteHost, String path, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + path;
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn("Signed GET list failed for {}: HTTP {}", url, response.statusCode());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, Map.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch list from {} {}", remoteHost, path, e);
            return List.of();
        }
    }

    /**
     * Performs a signed GET and returns the raw JSON response body. Returns null on error.
     */
    public String signedGetJson(String remoteHost, String path, int localStationId, String localPrivateKeyBase64) {
        try {
            var response = signedGet(apiUrl(remoteHost) + path, localStationId, localPrivateKeyBase64);
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
            log.warn("Signed GET {} failed: HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed GET {} on {}", path, remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed POST and returns the raw JSON response body. Returns null on error.
     */
    public String signedPostJson(
            String remoteHost, String path, String jsonBody, int localStationId, String localPrivateKeyBase64) {
        try {
            var response = signedPost(apiUrl(remoteHost) + path, jsonBody, localStationId, localPrivateKeyBase64);
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
            log.warn("Signed POST {} failed: HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed POST {} on {}", path, remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed PUT and returns the raw JSON response body. Returns null on error.
     */
    public String signedPutJson(
            String remoteHost, String path, String jsonBody, int localStationId, String localPrivateKeyBase64) {
        try {
            String timestampStr = Instant.now().toString();
            var privateKey = signingService.decodePrivateKey(localPrivateKeyBase64);
            String signature = signingService.sign(jsonBody, timestampStr, privateKey);
            String stationUid = StationUidResolver.instance().resolveToString(localStationId);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(remoteHost) + path))
                    .header("X-Federation-Station-Id", stationUid)
                    .header("X-Federation-Station-Name", resolveStationName(localStationId))
                    .header("X-Federation-Signature", signature)
                    .header("X-Federation-Timestamp", timestampStr)
                    .header("X-Federation-Version", FederationService.FEDERATION_VERSION)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
            log.warn("Signed PUT {} failed: HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed signed PUT {} on {}", path, remoteHost, e);
            return null;
        }
    }

    /**
     * Performs a signed DELETE. Returns true on success.
     */
    public boolean signedDeleteRequest(
            String remoteHost, String path, int localStationId, String localPrivateKeyBase64) {
        try {
            var response = signedDelete(apiUrl(remoteHost) + path, "", localStationId, localPrivateKeyBase64);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed signed DELETE {} on {}", path, remoteHost, e);
            return false;
        }
    }

    public JsonMapper getMapper() {
        return mapper;
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

    // -- Federated Events --

    public List<RemoteFederatedEvent> fetchFederatedEvents(
            String remoteHost, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/events";
            var response = signedGet(url, localStationId, localPrivateKeyBase64);
            if (response.statusCode() != 200) {
                log.warn("Failed to fetch federated events from {}: HTTP {}", remoteHost, response.statusCode());
                return List.of();
            }
            var type = mapper.getTypeFactory().constructCollectionType(List.class, RemoteFederatedEvent.class);
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            log.error("Failed to fetch federated events from {}", remoteHost, e);
            return List.of();
        }
    }

    public boolean registerForFederatedEvent(
            String remoteHost,
            int eventId,
            String remoteMemberId,
            String eventDate,
            int localStationId,
            String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/events/" + eventId + "/register";
            String body = mapper.writeValueAsString(new FederatedRegBody(remoteMemberId, eventDate));
            var response = signedPost(url, body, localStationId, localPrivateKeyBase64);
            return response.statusCode() == 201;
        } catch (Exception e) {
            log.error("Failed to register for federated event {} on {}", eventId, remoteHost, e);
            return false;
        }
    }

    public boolean withdrawFederatedRegistration(
            String remoteHost,
            int eventId,
            String remoteMemberId,
            String eventDate,
            int localStationId,
            String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/remote/events/" + eventId + "/register";
            String body = mapper.writeValueAsString(new FederatedRegBody(remoteMemberId, eventDate));
            var response = signedDelete(url, body, localStationId, localPrivateKeyBase64);
            return response.statusCode() == 204;
        } catch (Exception e) {
            log.error("Failed to withdraw federated registration for event {} on {}", eventId, remoteHost, e);
            return false;
        }
    }

    public record RemoteKbFile(int id, String name, String description, String fileType) {}

    public record RemoteQuizCatalog(int id, String name, String description) {}

    public record RemoteProtocol(int id, String name, String description) {}

    public record RemoteKbContent(int fileId, String content) {}

    public record RemoteFederatedEvent(
            int id,
            String name,
            String description,
            String eventType,
            int dayOfWeek,
            String startTime,
            String endTime,
            boolean requiresRegistration,
            boolean requiresConfirmation) {}

    private record FederatedRegBody(String remoteMemberId, String eventDate) {}
}
