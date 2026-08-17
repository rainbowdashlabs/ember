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
 * Network / reverse-proxy configuration. Controls which forwarded-for headers
 * {@link dev.chojo.ember.util.ClientIp} honours and from which hops, so that
 * the real visitor IP is identified correctly regardless of deployment shape
 * (direct, Traefik, Cloudflare, or Cloudflare → Traefik).
 *
 * <p>The defaults assume the safest deployment - direct on a public socket -
 * where no forwarded-for headers are trusted. Production deployments override
 * these values to match the actual layout.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("NETWORK")
public class Network {

    /**
     * Trusted reverse-proxy CIDRs. Forwarded-for headers
     * ({@code X-Forwarded-For}, {@code X-Real-IP}) are only honoured when the
     * immediate hop matches one of these ranges. Empty list = no proxies
     * trusted (direct deployment).
     */
    @Overwrite(env = @Env)
    private List<String> trustedProxies = List.of();

    /**
     * Whether the deployment sits behind Cloudflare. When {@code true}, the
     * {@code CF-Connecting-IP} header is honoured but only if the immediate
     * hop is in Cloudflare's published edge ranges.
     */
    @Overwrite(env = @Env)
    private boolean cloudflare = false;

    public List<String> trustedProxies() {
        return trustedProxies;
    }

    public boolean cloudflare() {
        return cloudflare;
    }

    @Override
    public String toString() {
        return "Network{trustedProxies=" + trustedProxies + ", cloudflare=" + cloudflare + '}';
    }
}
