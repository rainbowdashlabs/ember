/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import org.jspecify.annotations.Nullable;

/**
 * Where a station's logo and its public page are found: as a path on this instance, or as a full
 * address behind an instance's base URL. The public page is addressed by the station's readable name
 * where it has one, and by its identifier otherwise; the page accepts both.
 */
public final class StationAddresses {
    private static final String LOGO_PATH = "/api/v1/public/stations/%s/logo";
    private static final String PUBLIC_PAGE_PATH = "/public/station/";

    private StationAddresses() {}

    /**
     * The path of the station's logo on this instance.
     *
     * @param station the station
     * @return the path
     */
    public static String logo(Station station) {
        return LOGO_PATH.formatted(station.uid());
    }

    /**
     * The address of the station's logo behind the given base URL.
     *
     * @param baseUrl the instance's base URL, without a trailing slash
     * @param station the station
     * @return the address
     */
    public static String logo(String baseUrl, Station station) {
        return baseUrl + logo(station);
    }

    /**
     * The path of the station's public page on this instance.
     *
     * @param station the station
     * @return the path
     */
    public static String publicPage(Station station) {
        return PUBLIC_PAGE_PATH
                + pageAddress(station.publicSlug(), station.uid().toString());
    }

    /**
     * The address of the station's public page behind the given base URL.
     *
     * @param baseUrl the instance's base URL, without a trailing slash
     * @param station the station
     * @return the address
     */
    public static String publicPage(String baseUrl, Station station) {
        return baseUrl + publicPage(station);
    }

    /**
     * The address of a public page behind the given base URL, for a station known only by the address
     * segment of its page.
     *
     * @param baseUrl the instance's base URL, without a trailing slash
     * @param segment the station's page address, already encoded for a path
     * @return the address
     */
    public static String publicPageAt(String baseUrl, String segment) {
        return baseUrl + PUBLIC_PAGE_PATH + segment;
    }

    /**
     * What a station's public page is addressed by: its readable name where it has one, its identifier
     * otherwise.
     *
     * @param publicSlug the readable name, or {@code null}
     * @param stationUid the identifier
     * @return the address segment
     */
    public static String pageAddress(@Nullable String publicSlug, String stationUid) {
        return publicSlug != null && !publicSlug.isBlank() ? publicSlug : stationUid;
    }
}
