/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.RefusedDestinationException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Posts pushes through the one gateway for addresses somebody else supplied: the push address comes
 * from a browser, so it is resolved, checked to be public and pinned, and redirects are not followed.
 *
 * <p>A name that does not resolve right now is a passing fault of the resolver, answered like a busy
 * push service so the push is tried again; only an address that may never be reached counts as gone.
 */
@Singleton
public class OutboundPushTransport implements PushTransport {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OutboundHttp http;

    @Inject
    public OutboundPushTransport(OutboundHttp http) {
        this.http = http;
    }

    @Override
    public Answer post(URI endpoint, Map<String, String> headers, byte[] body)
            throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(endpoint).timeout(TIMEOUT).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        try {
            var response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return new Answer(response.statusCode(), retryAfter(response));
        } catch (RefusedDestinationException e) {
            if (e.reason() == RefusedDestinationException.Reason.UNRESOLVABLE) {
                return new Answer(503, Duration.ZERO);
            }
            return new Answer(0, Duration.ZERO);
        }
    }

    private static Duration retryAfter(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(value.trim()));
                    } catch (NumberFormatException notSeconds) {
                        return Duration.ZERO;
                    }
                })
                .orElse(Duration.ZERO);
    }
}
