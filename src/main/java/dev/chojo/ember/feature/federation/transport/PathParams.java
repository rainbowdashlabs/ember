/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import io.javalin.http.Context;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The parameters of one federation request, read the same way whether the request came over the
 * wire or was handed over on this instance.
 *
 * <p>Path values are URL-decoded once more after the router has read them, which is what the
 * asking side's {@code URLEncoder} expects. Query values arrive decoded.
 *
 * @param path  the path template's parameters by name
 * @param query the query parameters by name, the first value of each
 */
public record PathParams(Map<String, String> path, Map<String, String> query) {

    /** No parameters at all. */
    public static final PathParams NONE = new PathParams(Map.of(), Map.of());

    public PathParams {
        path = Map.copyOf(path);
        query = Map.copyOf(query);
    }

    /**
     * Reads the parameters of a request built on this instance from its endpoint's template.
     *
     * @param request the request as the asking side built it
     * @return its parameters
     */
    public static PathParams of(FederationRequest request) {
        String full = request.path();
        int separator = full.indexOf('?');
        String concrete = separator < 0 ? full : full.substring(0, separator);
        var path = new HashMap<String, String>();
        String[] template = request.endpoint().path().split("/");
        String[] segments = concrete.split("/");
        for (int i = 0; i < template.length && i < segments.length; i++) {
            if (isParameter(template[i])) path.put(name(template[i]), decode(segments[i]));
        }
        var query = new HashMap<String, String>();
        if (separator >= 0) {
            for (String pair : full.substring(separator + 1).split("&")) {
                if (pair.isEmpty()) continue;
                int equals = pair.indexOf('=');
                String key = decode(equals < 0 ? pair : pair.substring(0, equals));
                String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
                query.putIfAbsent(key, value);
            }
        }
        return new PathParams(path, query);
    }

    /**
     * Reads the parameters of a signed request that arrived at a {@code /remote} route.
     *
     * @param ctx      the request
     * @param endpoint the endpoint it was routed to
     * @return its parameters
     */
    public static PathParams of(Context ctx, FederationEndpoint endpoint) {
        var path = new HashMap<String, String>();
        for (String segment : endpoint.path().split("/")) {
            if (isParameter(segment)) path.put(name(segment), decode(ctx.pathParam(name(segment))));
        }
        var query = new HashMap<String, String>();
        for (Map.Entry<String, List<String>> entry : ctx.queryParamMap().entrySet()) {
            if (!entry.getValue().isEmpty())
                query.put(entry.getKey(), entry.getValue().getFirst());
        }
        return new PathParams(path, query);
    }

    /**
     * A path parameter as text.
     *
     * @param name the parameter's name in the template
     * @return its value
     */
    public String text(String name) {
        var value = path.get(name);
        if (value == null) throw new IllegalArgumentException("No path parameter " + name);
        return value;
    }

    /**
     * A path parameter as a number, refused as an address that names nothing when it is none.
     *
     * @param name the parameter's name in the template
     * @return its value
     */
    public int integer(String name) {
        try {
            return Integer.parseInt(text(name));
        } catch (NumberFormatException e) {
            throw GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER.raise();
        }
    }

    /**
     * A path parameter as an identifier, refused as an address that names nothing when it is none.
     *
     * @param name the parameter's name in the template
     * @return its value
     */
    public UUID uuid(String name) {
        try {
            return UUID.fromString(text(name));
        } catch (IllegalArgumentException e) {
            throw GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER.raise();
        }
    }

    /**
     * A query parameter, when the request carried it.
     *
     * @param name the parameter's name
     * @return its value, empty when absent
     */
    public Optional<String> query(String name) {
        return Optional.ofNullable(query.get(name));
    }

    private static boolean isParameter(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private static String name(String segment) {
        return segment.substring(1, segment.length() - 1);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
