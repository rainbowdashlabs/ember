/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.conf.file.elements.Network;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches Cloudflare's published edge IP ranges from {@code cloudflare.com} and
 * hot-swaps them into {@link ClientIp}. Runs once on startup so the in-memory
 * list reflects the latest upstream data without requiring a rebuild; the build
 * still bakes a snapshot into the classpath so the first request after boot is
 * never gated on the live fetch finishing.
 *
 * <p>Skipped when {@link Network#cloudflare()} is {@code false} — a non-CF
 * deployment never reads {@code CF-Connecting-IP} anyway, so the list is unused.
 *
 * <p>Failures are logged at {@code warn} and leave the build-time snapshot in
 * place. Never throws to the caller.
 */
@Singleton
public class CloudflareRangesService {
    private static final Logger log = LoggerFactory.getLogger(CloudflareRangesService.class);
    private static final URI IPS_V4 = URI.create("https://www.cloudflare.com/ips-v4");
    private static final URI IPS_V6 = URI.create("https://www.cloudflare.com/ips-v6");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Network network;

    @Inject
    public CloudflareRangesService(Network network) {
        this.network = network;
    }

    /**
     * Kicks off the refresh on a virtual thread so the startup sequence is
     * never blocked on outbound HTTP to {@code cloudflare.com}.
     */
    public void refreshAsync() {
        if (!network.cloudflare()) {
            log.debug("Cloudflare integration disabled; skipping edge range refresh");
            return;
        }
        Thread.ofVirtual().name("cloudflare-ranges-refresh").start(this::refresh);
    }

    /**
     * Performs the synchronous refresh — fetches both {@code ips-v4} and
     * {@code ips-v6}, parses, and replaces the in-memory list. Returns silently
     * on any failure.
     */
    public void refresh() {
        try {
            String body = fetch(IPS_V4) + "\n" + fetch(IPS_V6);
            int applied = ClientIp.updateCloudflareRanges(body);
            if (applied == 0) {
                log.warn("Cloudflare ranges fetch returned no usable entries; keeping build-time snapshot");
                return;
            }
            log.info("Refreshed Cloudflare edge IP ranges from upstream ({} entries)", applied);
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to refresh Cloudflare edge IP ranges from upstream: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private String fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Ember")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected status " + response.statusCode() + " from " + uri);
        }
        return response.body();
    }
}
