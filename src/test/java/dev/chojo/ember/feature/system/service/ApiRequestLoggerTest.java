/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.conf.file.elements.Metrics;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A request is recorded under the route it matched, so a token in its path never reaches the log,
 * and the buffer drops rather than grows or blocks once it is full.
 */
class ApiRequestLoggerTest {

    private static final String FEED_TOKEN = "hgEV4EC3kq9ZrT0bXa7LmN2pQ8sUvWy1";

    private Javalin app;

    @AfterEach
    void stop() {
        if (app != null) app.stop();
    }

    @Test
    void aTokenPathIsRecordedAsItsTemplate() throws Exception {
        var logger = new ApiRequestLogger(new Metrics(), 10);
        serveFeed(logger);

        request("/api/v1/public/feed/" + FEED_TOKEN + "/events.ics");

        assertEquals(
                List.of(new ApiRequestLogger.RequestEntry("GET", "/api/v1/public/feed/{token}/events.ics", 200, 0)),
                logger.pending());
    }

    @Test
    void aPathThatMatchedNoRouteIsRecordedUnderTheMarker() throws Exception {
        var logger = new ApiRequestLogger(new Metrics(), 10);
        serveFeed(logger);

        request("/api/v1/public/shared/" + FEED_TOKEN);

        assertEquals(
                List.of(new ApiRequestLogger.RequestEntry("GET", ApiRequestLogger.UNMATCHED, 404, 0)),
                logger.pending());
    }

    @Test
    void aFullBufferDropsWithoutBlocking() {
        var logger = new ApiRequestLogger(new Metrics(), 3);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (int i = 0; i < 10; i++) {
                logger.record("GET", "/api/v1/things/{id}", 200, 1);
            }
        });

        assertEquals(3, logger.pending().size());
        assertEquals(7, logger.droppedSinceFlush());
    }

    @Test
    void aMissingTemplateIsRecordedUnderTheMarker() {
        var logger = new ApiRequestLogger(new Metrics(), 3);

        logger.record("GET", null, 404, 1);

        assertEquals(ApiRequestLogger.UNMATCHED, logger.pending().getFirst().path());
    }

    private void serveFeed(ApiRequestLogger logger) {
        app = Javalin.create(config -> {
            config.routes.get("/api/v1/public/feed/{token}/events.ics", ctx -> ctx.result("feed"));
            config.routes.after(ctx ->
                    logger.record(ctx.method().name(), ApiRequestLogger.routeTemplate(ctx), ctx.statusCode(), 0));
        });
        app.start(0);
    }

    private void request(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }
}
