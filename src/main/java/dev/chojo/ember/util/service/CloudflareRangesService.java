/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.service;

import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.util.ClientIp;
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
 * Replaces the Cloudflare edge ranges in {@link ClientIp} with the ones Cloudflare publishes, once at startup
 * and only with Cloudflare enabled. The committed snapshot answers until then and stays on any failure.
 */
@Singleton
public class CloudflareRangesService {
    private static final Logger log = LoggerFactory.getLogger(CloudflareRangesService.class);
    private static final URI IPS_V4 = URI.create("https://www.cloudflare.com/ips-v4");
    private static final URI IPS_V6 = URI.create("https://www.cloudflare.com/ips-v6");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = OutboundHttp.trustedClient(Duration.ofSeconds(5), HttpClient.Redirect.NORMAL);

    private final Network network;
    private final TaskScheduler scheduler;

    @Inject
    public CloudflareRangesService(Network network, TaskScheduler scheduler) {
        this.network = network;
        this.scheduler = scheduler;
    }

    /** Refreshes in the background, so startup never waits on {@code cloudflare.com}. */
    public void refreshAsync() {
        if (!network.cloudflare()) {
            log.debug("Cloudflare integration disabled; skipping edge range refresh");
            return;
        }
        scheduler.background("cloudflare-ranges-refresh", this::refresh);
    }

    /** Fetches both range lists and applies them; a failure is logged and leaves the current list. */
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
