/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import java.lang.reflect.RecordComponent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * One concrete call of a declared {@link FederationEndpoint}: the endpoint plus the path
 * with its template parameters filled in. Built with {@link FederationEndpoint#at}.
 * <p>
 * Carrying the endpoint alongside the path is what lets the HTTP client advertise the right
 * surface hash and check the response type against the declaration, instead of matching the
 * path back against the catalog and hoping it resolves.
 *
 * @param endpoint the declared endpoint being called
 * @param path     the resolved path below the API prefix, query string included
 */
public record FederationRequest(FederationEndpoint endpoint, String path) {

    /**
     * A copy of this request with one query parameter appended, URL-encoded.
     */
    public FederationRequest query(String name, Object value) {
        String separator = path.contains("?") ? "&" : "?";
        String encoded = URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
        return new FederationRequest(endpoint, path + separator + name + "=" + encoded);
    }

    /**
     * Fails when a caller reads fields the endpoint does not declare. The declared response
     * type is what the surface hash is computed from, so a caller expecting more than the
     * declaration provides would drift from the contract without the hash noticing.
     * <p>
     * The check is structural rather than by identity: a caller may deserialize into its own
     * view record as long as every component it reads exists on the declared type. That is
     * what makes a rolled hash meaningful — the fields a caller depends on are a subset of
     * the fields the contract covers.
     */
    public void requireResponseType(Class<?> expected) {
        var declared = endpoint.responseType();
        if (expected == declared || expected == Void.class || !expected.isRecord() || !declared.isRecord()) return;

        var declaredComponents = Arrays.stream(declared.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        var missing = Arrays.stream(expected.getRecordComponents())
                .map(RecordComponent::getName)
                .filter(name -> !declaredComponents.contains(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("%s declares %s, which has no %s for the caller's %s"
                    .formatted(endpoint.path(), declared.getSimpleName(), missing, expected.getSimpleName()));
        }
    }
}
