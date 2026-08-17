/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import io.javalin.http.HandlerType;

/**
 * One declared endpoint of the federation contract. Route classes serving {@code /remote}
 * paths declare their endpoints as constants, list them in a static {@code CONTRACT} and
 * register their Javalin handlers from that list via {@link FederationContractBinder} -
 * an endpoint cannot exist without being part of the versioned contract.
 *
 * @param surface      the contract surface the endpoint belongs to
 * @param method       the HTTP method
 * @param path         the path template below the API prefix, e.g. {@code /remote/kb/browse}
 * @param requestType  the request body type, {@link Void} when the endpoint takes none
 * @param responseType the response body type (the element type for list responses),
 *                     {@link Void} when the endpoint returns none
 * @param listResponse whether the response body is a JSON array of {@code responseType}
 * @param versionExempt whether the endpoint stays callable across any contract mismatch;
 *                      reserved for the handshake and the version ping, which must keep
 *                      working so diverged instances can learn each other's versions
 */
public record FederationEndpoint(
        FederationSurface surface,
        HandlerType method,
        String path,
        Class<?> requestType,
        Class<?> responseType,
        boolean listResponse,
        boolean versionExempt) {

    public static FederationEndpoint get(FederationSurface surface, String path, Class<?> responseType) {
        return new FederationEndpoint(surface, HandlerType.GET, path, Void.class, responseType, false, false);
    }

    public static FederationEndpoint getList(FederationSurface surface, String path, Class<?> elementType) {
        return new FederationEndpoint(surface, HandlerType.GET, path, Void.class, elementType, true, false);
    }

    public static FederationEndpoint post(
            FederationSurface surface, String path, Class<?> requestType, Class<?> responseType) {
        return new FederationEndpoint(surface, HandlerType.POST, path, requestType, responseType, false, false);
    }

    public static FederationEndpoint postList(
            FederationSurface surface, String path, Class<?> requestType, Class<?> elementType) {
        return new FederationEndpoint(surface, HandlerType.POST, path, requestType, elementType, true, false);
    }

    public static FederationEndpoint put(
            FederationSurface surface, String path, Class<?> requestType, Class<?> responseType) {
        return new FederationEndpoint(surface, HandlerType.PUT, path, requestType, responseType, false, false);
    }

    public static FederationEndpoint delete(
            FederationSurface surface, String path, Class<?> requestType, Class<?> responseType) {
        return new FederationEndpoint(surface, HandlerType.DELETE, path, requestType, responseType, false, false);
    }

    /**
     * A copy of this endpoint that stays callable across any contract mismatch.
     */
    public FederationEndpoint exempt() {
        return new FederationEndpoint(surface, method, path, requestType, responseType, listResponse, true);
    }

    /**
     * Binds this endpoint to a concrete call, substituting the path template's parameters in
     * declaration order. Callers address federation endpoints through the constant rather
     * than a hand-built string, so the surface a request advertises and the type it expects
     * both come from the same declaration the version hash is computed from.
     */
    public FederationRequest at(Object... pathParams) {
        var segments = path.split("/", -1);
        var resolved = new StringBuilder();
        int next = 0;
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) resolved.append('/');
            String segment = segments[i];
            if (segment.startsWith("{") && segment.endsWith("}")) {
                if (next >= pathParams.length) {
                    throw new IllegalArgumentException("Too few path parameters for " + path);
                }
                resolved.append(pathParams[next++]);
            } else {
                resolved.append(segment);
            }
        }
        if (next != pathParams.length) {
            throw new IllegalArgumentException("Too many path parameters for " + path);
        }
        return new FederationRequest(this, resolved.toString());
    }

    /**
     * The deterministic line this endpoint contributes to its surface hash.
     */
    public String signatureLine() {
        String response = responseType.getCanonicalName();
        if (listResponse) response = "List<" + response + ">";
        return "endpoint:%s %s -> %s -> %s%s"
                .formatted(
                        method.name(),
                        path,
                        requestType.getCanonicalName(),
                        response,
                        versionExempt ? " (exempt)" : "");
    }

    /**
     * Whether a concrete request path matches this endpoint's path template. Template
     * segments in curly braces match any single concrete segment.
     */
    public boolean matches(HandlerType requestMethod, String concretePath) {
        if (method != requestMethod) return false;
        String[] template = path.split("/");
        String[] concrete = concretePath.split("/");
        if (template.length != concrete.length) return false;
        for (int i = 0; i < template.length; i++) {
            if (template[i].startsWith("{") && template[i].endsWith("}")) continue;
            if (!template[i].equals(concrete[i])) return false;
        }
        return true;
    }
}
