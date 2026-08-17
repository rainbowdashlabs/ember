/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.transfer.TransferBackendDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.feature.station.transfer.WireValues.asInteger;

/**
 * Talks to the token-authenticated transfer endpoints of the source instance for the duration of
 * one import run. Every outgoing request is throttled to at most one per {@value
 * #MIN_REQUEST_INTERVAL_MILLIS} milliseconds so a high-fanout import cannot overload the source,
 * and the secret token is stripped from every logged URI.
 */
public final class TransferSourceClient {
    private static final Logger log = LoggerFactory.getLogger(TransferSourceClient.class);
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 200L;

    private final String baseUrl;
    private final String token;
    private final String callerBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Object throttleLock = new Object();
    private long lastRequestMillis;

    /**
     * @param baseUrl       the source instance's base URL, without a trailing slash
     * @param token         the transfer token authorizing this run
     * @param callerBaseUrl this instance's public base URL, announced to the source, or {@code null}
     */
    public TransferSourceClient(String baseUrl, String token, String callerBaseUrl) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.callerBaseUrl = callerBaseUrl;
        this.httpClient = buildHttpClient(baseUrl);
    }

    /**
     * Builds the HTTP client for talking to the source instance. Uses HTTP/2 over HTTPS so
     * production runs benefit from ALPN-negotiated multiplexing, but falls back to HTTP/1.1
     * over plain HTTP because the JDK client's HTTP/2 default sends an {@code Upgrade: h2c}
     * header that Node-based servers (e.g. a Nuxt dev server in front of the source) hold
     * open without responding - see the dev compose transfer profile.
     */
    private static HttpClient buildHttpClient(String baseUrl) {
        HttpClient.Version version = baseUrl != null && baseUrl.startsWith("https://")
                ? HttpClient.Version.HTTP_2
                : HttpClient.Version.HTTP_1_1;
        return HttpClient.newBuilder()
                .version(version)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Strips the secret transfer token from a logged URI so logs are safe to keep. The token
     * is the path segment after {@code /transfer/}; everything between the slashes is replaced
     * with {@code ***}.
     */
    private static String redactToken(URI uri) {
        return uri.toString().replaceFirst("/transfer/[^/?]+", "/transfer/***");
    }

    /**
     * URL-encodes each path segment of a relative key, keeping the {@code /} separators intact.
     */
    private static String encodeKeyPath(String key) {
        String[] parts = key.split("/", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    /**
     * @return the schema hash the source reports, or {@code null} when it does not report one
     */
    @SuppressWarnings("unchecked")
    public String fetchSchemaHash() {
        var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/tables");
        log.info("verify schema hash: GET {}", redactToken(uri));
        try {
            var response = send(uri, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new TransferSourceException("Failed to fetch /tables from remote: HTTP " + response.statusCode());
            }
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            return (String) body.get("schemaHash");
        } catch (TransferSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to verify schema hash with remote at " + baseUrl + ": "
                            + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage()),
                    e);
        }
    }

    /**
     * Fetches one page of a table's rows.
     *
     * @param table  the tracked table name
     * @param offset the row offset
     * @param limit  the page size
     * @return the page envelope keyed by table name
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchPage(String table, int offset, int limit) {
        var uri = URI.create(
                baseUrl + "/api/v1/public/transfer/" + token + "/" + table + "?offset=" + offset + "&limit=" + limit);
        log.info("fetch page: GET {}", redactToken(uri));
        try {
            var response = send(uri, HttpResponse.BodyHandlers.ofString());
            log.info("fetch page: table '{}' offset={} HTTP {}", table, offset, response.statusCode());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to fetch table '" + table + "' from remote: HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(), Map.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch table '" + table + "' from remote", e);
        }
    }

    /**
     * Lists one page of storage keys in a category.
     *
     * @param category the storage category
     * @param after    the pagination cursor, or {@code null} for the first page
     * @return the listed keys with the next cursor and, when known, the total
     */
    @SuppressWarnings("unchecked")
    public ListKeysPage listKeys(StorageCategory category, String after) {
        var sb = new StringBuilder(baseUrl)
                .append("/api/v1/public/transfer/")
                .append(token)
                .append("/files/")
                .append(category.name());
        if (after != null && !after.isBlank()) {
            sb.append("?after=").append(URLEncoder.encode(after, StandardCharsets.UTF_8));
        }
        var uri = URI.create(sb.toString());
        log.info("list keys: GET {}", redactToken(uri));
        try {
            var response = send(uri, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to list keys for category " + category + ": HTTP " + response.statusCode());
            }
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            List<String> keys = (List<String>) body.getOrDefault("keys", List.of());
            String next = (String) body.get("next");
            Integer total = asInteger(body.get("total"));
            log.info(
                    "list keys: category {} returned {} key(s), next-cursor={}, total={}",
                    category,
                    keys.size(),
                    next == null ? "none" : "present",
                    total == null ? "absent" : total);
            return new ListKeysPage(keys, next, total);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list keys for category " + category, e);
        }
    }

    /**
     * Streams one stored file from the source.
     *
     * @param category the storage category
     * @param key      the relative key inside the category
     * @return the file, or empty when the source answered 404 because the row was deleted concurrently
     */
    public Optional<RemoteFile> fetchFile(StorageCategory category, String key) {
        var uri = URI.create(
                baseUrl + "/api/v1/public/transfer/" + token + "/files/" + category.name() + "/" + encodeKeyPath(key));
        log.info("stream file: GET {}", redactToken(uri));
        try {
            var response = send(uri, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                log.info("stream file: category {} key '{}' - source 404, skipping", category, key);
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to stream key '" + key + "' for category " + category + ": HTTP "
                        + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return Optional.of(new RemoteFile(response.body(), contentType));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream key '" + key + "' from remote", e);
        }
    }

    /**
     * Fetches an account avatar from the source.
     *
     * @param sourceUid the account UUID on the source instance
     * @return the avatar, or empty when the account has none or the source refused
     */
    public Optional<RemoteFile> fetchAvatar(UUID sourceUid) {
        var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/avatars/" + sourceUid);
        log.info("fetch avatar: GET {}", redactToken(uri));
        try {
            var response = send(uri, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() != 200) {
                log.warn(
                        "Source returned HTTP {} when fetching avatar for source account {}",
                        response.statusCode(),
                        sourceUid);
                return Optional.empty();
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return Optional.of(new RemoteFile(response.body(), contentType));
        } catch (Exception e) {
            log.warn("Failed to import avatar for source account {}", sourceUid, e);
            return Optional.empty();
        }
    }

    /**
     * @return the source station's storage backend descriptor
     */
    public TransferBackendDescriptor fetchBackendDescriptor() {
        var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/backend");
        log.info("fetch backend descriptor: GET {}", redactToken(uri));
        try {
            var response = send(uri, HttpResponse.BodyHandlers.ofString());
            log.info("fetch backend descriptor: HTTP {}", response.statusCode());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch /backend from remote: HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(), TransferBackendDescriptor.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch backend descriptor from remote", e);
        }
    }

    /**
     * Best-effort POST to the source's completion endpoint so the source can flip the remote host
     * on every partnership that pointed at the departed station. Any failure is logged and
     * swallowed - partnerships on the source fall back to manual reconfiguration.
     */
    public void notifyComplete() {
        postSignal("complete", "completion");
    }

    /**
     * Best-effort POST to the source's abort endpoint so the source can clear its read-only
     * transfer flag immediately instead of waiting for the idle-timeout watchdog.
     */
    public void notifyAbort() {
        postSignal("abort", "abort");
    }

    private void postSignal(String pathSegment, String label) {
        try {
            var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/" + pathSegment);
            var builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(30));
            if (callerBaseUrl != null && !callerBaseUrl.isBlank()) {
                builder.header("X-Ember-Importing-From", callerBaseUrl);
            }
            throttle();
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            log.info("notified source of {}: HTTP {}", label, response.statusCode());
        } catch (Exception e) {
            log.warn("could not notify source of {}: {}", label, e.getMessage());
        }
    }

    private <T> HttpResponse<T> send(URI uri, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder().uri(uri).GET().timeout(Duration.ofSeconds(30));
        if (callerBaseUrl != null && !callerBaseUrl.isBlank()) {
            builder.header("X-Ember-Importing-From", callerBaseUrl);
        }
        throttle();
        return httpClient.send(builder.build(), handler);
    }

    /**
     * Caps outgoing transfer requests at five per second. Synchronized because the synchronous
     * import-start path runs on the request thread while the async run loop runs on the import
     * executor, and both call into the throttle. The sleep is hard-capped at the interval itself
     * so a regression elsewhere cannot ever stall a request beyond one slot.
     */
    private void throttle() throws InterruptedException {
        long sleepMillis;
        synchronized (throttleLock) {
            long now = System.currentTimeMillis();
            long wait = lastRequestMillis + MIN_REQUEST_INTERVAL_MILLIS - now;
            if (wait > 0) {
                sleepMillis = Math.min(wait, MIN_REQUEST_INTERVAL_MILLIS);
                lastRequestMillis = now + sleepMillis;
            } else {
                sleepMillis = 0;
                lastRequestMillis = now;
            }
        }
        if (sleepMillis > 0) Thread.sleep(sleepMillis);
    }

    /**
     * One page of a category's key listing.
     *
     * @param keys  the keys on this page
     * @param next  the cursor for the following page, or {@code null} when exhausted
     * @param total the overall key count when the source reports one, otherwise {@code null}
     */
    public record ListKeysPage(List<String> keys, String next, Integer total) {}

    /**
     * A payload streamed from the source together with its content type.
     */
    public record RemoteFile(byte[] data, String contentType) {}

    /**
     * Raised when the source answers a request in a way the caller must surface as a bad request
     * rather than an internal failure.
     */
    public static class TransferSourceException extends RuntimeException {
        public TransferSourceException(String message) {
            super(message);
        }
    }
}
