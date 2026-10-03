/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Web addresses of instances: whether two lead to the same origin (the same scheme, host and port,
 * ignoring case, with a port left out read as the scheme's own), the host and port an address names,
 * and a base address without its trailing slash.
 */
public final class WebOrigins {

    /**
     * The address without a trailing slash, so a path can be put behind it.
     *
     * @param url the address, with or without a trailing slash
     * @return the address without one
     */
    public static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * The host an address names, with the port where it names one: what a pairing code carries.
     *
     * <p>The port comes with it when the address names one. Without it a code from an instance that
     * does not sit on the standard port names something nobody can reach: the side entering the code
     * has only the code to go by, and would call the same host on a port it was never told about.
     *
     * @param baseUrl the base URL of an instance
     * @return its host, with the port where the URL names one; the text itself where it is no address
     */
    public static String hostAndPort(String baseUrl) {
        try {
            var uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null) return baseUrl;
            return uri.getPort() == -1 ? host : host + ":" + uri.getPort();
        } catch (IllegalArgumentException notAnAddress) {
            return baseUrl;
        }
    }

    /**
     * Compares two addresses given as text. Text that is no address shares an origin with nothing.
     *
     * @param first  one address
     * @param second the other address
     * @return whether both lead to the same origin
     */
    public static boolean sameOrigin(@Nullable String first, @Nullable String second) {
        if (first == null || second == null) return false;
        try {
            return sameOrigin(new URI(first), new URI(second));
        } catch (URISyntaxException notAnAddress) {
            return false;
        }
    }

    /**
     * Compares two addresses.
     *
     * @param first  one address
     * @param second the other address
     * @return whether both lead to the same origin
     */
    public static boolean sameOrigin(URI first, URI second) {
        String scheme = first.getScheme();
        String host = first.getHost();
        if (scheme == null || host == null) return false;
        return scheme.equalsIgnoreCase(second.getScheme())
                && host.toLowerCase(Locale.ROOT).equals(lower(second.getHost()))
                && port(first) == port(second);
    }

    private static @Nullable String lower(@Nullable String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private WebOrigins() {}
}
