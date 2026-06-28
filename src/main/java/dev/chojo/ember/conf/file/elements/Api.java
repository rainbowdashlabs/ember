/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

import java.util.List;

/**
 * API server configuration including host, port, base URL, and paths to legal document directories.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("API")
public class Api {
    @Overwrite(env = @Env)
    private String host = "0.0.0.0";

    @Overwrite(env = @Env)
    private int port = 8080;

    @Overwrite(env = @Env)
    private String baseUrl = "http://localhost:3000";

    @Overwrite(env = @Env)
    private String demoUrl = "https://demo.ember-panel.de";

    /**
     * Origins permitted by the CORS policy. When empty, falls back to a single
     * entry containing {@link #baseUrl()} — i.e. the SPA's own origin is the
     * only one allowed to call the API. Set explicitly to grant additional
     * trusted frontends access.
     */
    @Overwrite(env = @Env)
    private List<String> allowedOrigins = List.of();

    @Overwrite(env = @Env)
    private int maxImageSizeBytes = 5 * 1024 * 1024;

    @Overwrite(env = @Env)
    private String privacyPolicyDir = "data/documents/privacy";

    @Overwrite(env = @Env)
    private String consentDir = "data/documents/consent";

    @Overwrite(env = @Env)
    private String tosDir = "data/documents/tos";

    @Overwrite(env = @Env)
    private String imprintDir = "data/documents/imprint";

    /**
     * Whether to gzip text-shaped responses (JSON, HTML, CSS, XML/RSS/Atom, SVG, plain text,
     * ICS feeds, …) before sending them to the client. The largest single egress win after
     * image variants, universally supported, ~70% reduction on JSON. Switch off only for
     * deployments behind a reverse proxy that already handles compression.
     */
    @Overwrite(env = @Env)
    private boolean httpGzipEnabled = true;

    /**
     * Gzip compression level. Range 0–9 (mirrors Javalin's underlying knob); 6 is the
     * standard speed-vs-size trade-off and matches Javalin's default. Bump to 9 if CPU is
     * cheap and bandwidth costs more; drop to 1 on tiny instances if compression CPU shows
     * up in profiles.
     */
    @Overwrite(env = @Env)
    private int httpGzipLevel = 6;

    /**
     * Minimum response body size, in bytes, before compression kicks in. Smaller responses
     * cost more CPU than they save in bytes — the default of ~1 KB sits slightly above the
     * typical MTU. Set to a higher value if many responses fall just over the threshold
     * and the overhead shows up; never set below ~256 B.
     */
    @Overwrite(env = @Env)
    private int httpGzipMinSizeBytes = 1024;

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String demoUrl() {
        return demoUrl;
    }

    /**
     * Returns the effective list of CORS-allowed origins. When the configured
     * list is empty, returns a single-element list containing {@link #baseUrl()}
     * so a fresh deployment is locked to its own SPA origin.
     */
    public List<String> allowedOrigins() {
        return allowedOrigins.isEmpty() ? List.of(baseUrl) : allowedOrigins;
    }

    public int maxImageSizeBytes() {
        return maxImageSizeBytes;
    }

    public String privacyPolicyDir() {
        return privacyPolicyDir;
    }

    public String consentDir() {
        return consentDir;
    }

    public String tosDir() {
        return tosDir;
    }

    public String imprintDir() {
        return imprintDir;
    }

    public boolean httpGzipEnabled() {
        return httpGzipEnabled;
    }

    public int httpGzipLevel() {
        return httpGzipLevel;
    }

    public int httpGzipMinSizeBytes() {
        return httpGzipMinSizeBytes;
    }

    @Override
    public String toString() {
        return "Api{" + "host='" + host + '\'' + ", port=" + port + ", baseUrl='" + baseUrl + '\'' + ", demoUrl='"
                + demoUrl + '\'' + ", allowedOrigins=" + allowedOrigins() + '}';
    }
}
