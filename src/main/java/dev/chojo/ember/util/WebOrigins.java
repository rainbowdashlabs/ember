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
 * Whether two web addresses lead to the same origin: the same scheme, host and port, ignoring case,
 * with a port left out read as the scheme's own.
 */
public final class WebOrigins {

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
