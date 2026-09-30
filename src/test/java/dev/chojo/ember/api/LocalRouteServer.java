/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Json;
import io.javalin.Javalin;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Serves route groups over real HTTP on a free local port, reading and writing JSON with the API's own
 * mapper, so a request shape the API refuses is refused here too.
 *
 * <p>Every request is made as the session given, and the access gate is not in front of the routes:
 * what is tested is what a route does with a request it lets in, not who it lets in.
 */
public final class LocalRouteServer implements AutoCloseable {
    private static final String PREFIX = "/api/v1";

    private final Javalin app;
    private final HttpClient client = HttpClient.newHttpClient();

    private LocalRouteServer(Javalin app) {
        this.app = app;
    }

    /**
     * Starts a server for the given route groups.
     *
     * @param stations the station store the mapper resolves station ids with
     * @param clusters the cluster store the mapper resolves cluster ids with
     * @param session  who every request is made as
     * @param routes   the route groups under test
     * @return the running server
     */
    public static LocalRouteServer serving(
            StationRepository stations, ClusterRepository clusters, UserSession session, Routes... routes) {
        var app = Javalin.create(config -> {
                    config.jsonMapper(ApiServer.jacksonMapper(stations, clusters));
                    config.routes.before(ctx -> ctx.attribute(ApiServer.ATTR_SESSION, session));
                    for (var group : routes) {
                        group.register(config.routes, PREFIX);
                    }
                })
                .start(0);
        return new LocalRouteServer(app);
    }

    /**
     * @param path the address below {@code /api/v1}, starting with a slash
     * @return the answer
     */
    public HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * @param path the address below {@code /api/v1}, starting with a slash
     * @param body the request body as JSON text
     * @return the answer
     */
    public HttpResponse<String> put(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * @param answer an answer of this server
     * @return its body read as JSON
     */
    public static JsonNode json(HttpResponse<String> answer) {
        return Json.MAPPER.readTree(answer.body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:%d%s%s".formatted(app.port(), PREFIX, path));
    }

    @Override
    public void close() {
        app.stop();
        client.close();
    }
}
