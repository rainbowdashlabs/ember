/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

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
 * The remote host is determined per-partner from the {@code remote_host} field.
 */
@Singleton
public class FederationHttpClient {
    private static final Logger log = LoggerFactory.getLogger(FederationHttpClient.class);

    private final HttpClient httpClient;
    private final FederationSigningService signingService;
    private final JsonMapper mapper;

    @Inject
    public FederationHttpClient(FederationSigningService signingService) {
        this.signingService = signingService;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = JsonMapper.builder().build();
    }

    public List<LendingMessage> fetchRemoteMessages(
            String remoteHost, int requestId, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/federation/remote/lending/messages/" + requestId;
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

    public List<RemoteKbFile> fetchSharedKbFiles(String remoteHost, int localStationId, String localPrivateKeyBase64) {
        try {
            String url = apiUrl(remoteHost) + "/federation/remote/kb/browse";
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
            String url = apiUrl(remoteHost) + "/federation/remote/kb/file/" + fileId + "/content";
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
            String url = apiUrl(remoteHost) + "/federation/remote/quiz/catalogs";
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
            String url = apiUrl(remoteHost) + "/federation/remote/protocols";
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

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Federation-Station-Id", String.valueOf(localStationId))
                .header("X-Federation-Signature", signature)
                .header("X-Federation-Timestamp", timestampStr)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public record RemoteKbFile(int id, String name, String description, String fileType) {}

    public record RemoteQuizCatalog(int id, String name, String description) {}

    public record RemoteProtocol(int id, String name, String description) {}

    public record RemoteKbContent(int fileId, String content) {}
}
