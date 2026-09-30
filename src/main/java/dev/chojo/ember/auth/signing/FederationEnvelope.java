/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

/**
 * The federation request envelope: HTTP method, path with its sorted query, the recipient station,
 * the nonce, the timestamp and the body, one per line in that order.
 *
 * <p>Binding the method, path and recipient keeps a captured signature from being replayed against
 * another endpoint or another station within the timestamp window. Binding the nonce keeps an
 * attacker from swapping in a fresh one to get past the replay check. The layout is what every
 * federated installation computes, so it never changes.
 *
 * @param method        the HTTP method, upper-cased when laid out
 * @param pathWithQuery the path with its canonical query, see {@link #canonicalPathWithQuery(String, String)}
 * @param recipient     the station the request is addressed to
 * @param nonce         the per-request nonce from the {@code X-Federation-Nonce} header
 * @param timestamp     the ISO-8601 timestamp from the {@code X-Federation-Timestamp} header
 * @param body          the request body, {@code null} read as empty
 */
public record FederationEnvelope(
        String method, String pathWithQuery, UUID recipient, String nonce, String timestamp, String body)
        implements SignedEnvelope {

    /**
     * Builds the canonical path-with-query string by sorting {@code &}-separated pairs
     * lexicographically. The path is used verbatim; the query is omitted entirely when the request
     * has no query string.
     *
     * @param path  the raw path
     * @param query the raw query, may be {@code null}
     * @return the canonical form
     */
    public static String canonicalPathWithQuery(String path, String query) {
        String safePath = path == null ? "" : path;
        if (query == null || query.isEmpty()) {
            return safePath;
        }
        String[] pairs = query.split("&");
        Arrays.sort(pairs);
        return safePath + "?" + String.join("&", pairs);
    }

    /**
     * The canonical path-with-query of a fully-qualified request URI.
     *
     * @param uri the request URI
     * @return the canonical form
     */
    public static String canonicalPathWithQuery(URI uri) {
        return canonicalPathWithQuery(uri.getRawPath(), uri.getRawQuery());
    }

    /**
     * Reports whether the query string repeats any parameter name. Because the canonical form sorts
     * {@code &}-separated pairs, {@code a=1&a=2} and {@code a=2&a=1} would share one signature while
     * a handler reads wire order; the receiver rejects any request with duplicate keys to close
     * that gap.
     *
     * @param query the raw query, may be {@code null}
     * @return true when a parameter name repeats
     */
    public static boolean hasDuplicateQueryKeys(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        var seen = new HashSet<String>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (!seen.add(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public byte[] bytes() {
        String laidOut = method.toUpperCase(Locale.ROOT)
                + "\n"
                + (pathWithQuery == null ? "" : pathWithQuery)
                + "\n"
                + recipient
                + "\n"
                + (nonce == null ? "" : nonce)
                + "\n"
                + timestamp
                + "\n"
                + (body == null ? "" : body);
        return laidOut.getBytes(StandardCharsets.UTF_8);
    }
}
