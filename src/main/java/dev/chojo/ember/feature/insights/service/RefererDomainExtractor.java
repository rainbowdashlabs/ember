/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

import dev.chojo.ember.conf.file.elements.Api;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Locale;

/**
 * Reduces a raw {@code Referer} header to a single short domain label that is safe to store
 * in the {@code page_hit_hourly} table. The raw header is never persisted.
 *
 * <ul>
 *   <li>Missing or unparseable → {@code direct}.</li>
 *   <li>Same host as this instance (derived from {@link Api#baseUrl()}) → {@code internal}.</li>
 *   <li>Anything else → the lowercased host with a leading {@code www.} stripped. This is a
 *       cheap stand-in for eTLD+1 without pulling in a Public Suffix List dependency.</li>
 * </ul>
 *
 * <p>Long-tail collapse to {@code other} is done at flush time by the recorder, which checks
 * the historical row count for the domain.
 */
@Singleton
public class RefererDomainExtractor {
    private static final Logger log = LoggerFactory.getLogger(RefererDomainExtractor.class);
    private static final String DIRECT = "direct";
    private static final String INTERNAL = "internal";

    private final String ownHost;

    @Inject
    public RefererDomainExtractor(Api api) {
        this.ownHost = parseHost(api.baseUrl());
    }

    private static String parseHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return null;
        try {
            String host = URI.create(baseUrl).getHost();
            if (host == null) return null;
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid api.baseUrl, referer extractor cannot detect internal links: {}", baseUrl);
            return null;
        }
    }

    /**
     * Returns the reduced domain label for the given raw {@code Referer} header value.
     */
    public String extract(String rawReferer) {
        if (rawReferer == null || rawReferer.isBlank()) return DIRECT;
        String host;
        try {
            URI uri = URI.create(rawReferer.trim());
            host = uri.getHost();
        } catch (IllegalArgumentException e) {
            log.debug("Malformed referer header: {}", rawReferer);
            return DIRECT;
        }
        if (host == null || host.isBlank()) return DIRECT;
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        if (ownHost != null && (normalized.equals(ownHost) || normalized.endsWith("." + ownHost))) {
            return INTERNAL;
        }
        return normalized;
    }
}
