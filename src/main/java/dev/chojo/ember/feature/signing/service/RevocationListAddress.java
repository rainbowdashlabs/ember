/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import java.net.URI;

/**
 * Where an authority's revocation list is published: below the installation's public base address,
 * named by the authority certificate's serial number, which never changes and tells the authorities
 * apart after a renewal. Station certificates carry this address as their CRL distribution point, so
 * it has to stay stable for as long as any of them is checked; the route that serves the list takes
 * {@link #ROUTE} from here.
 */
public final class RevocationListAddress {
    /** The path parameter in {@link #ROUTE} that holds the authority's serial number. */
    public static final String SERIAL_PARAMETER = "serial";

    /** The route below the API prefix, with the authority's serial number as path parameter. */
    public static final String ROUTE = "/public/signing/ca/{" + SERIAL_PARAMETER + "}.crl";

    private static final String API_PREFIX = "/api/v1";

    private RevocationListAddress() {}

    /**
     * @param baseUrl         the installation's public base address
     * @param authoritySerial the authority certificate's serial number, lower-case hexadecimal
     * @return the address the authority's revocation list is published at
     */
    public static URI of(String baseUrl, String authoritySerial) {
        var base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + API_PREFIX + ROUTE.replace("{" + SERIAL_PARAMETER + "}", authoritySerial));
    }
}
