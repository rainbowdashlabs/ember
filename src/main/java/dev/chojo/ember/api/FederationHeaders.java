/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.station.entity.Station;
import io.javalin.http.Context;

/**
 * Utility for setting federation station identity headers on responses.
 */
public final class FederationHeaders {

    public static final String HEADER_STATION_NAME = "X-Federation-Station-Name";
    public static final String HEADER_STATION_ID = "X-Federation-Station-Id";

    private FederationHeaders() {}

    /**
     * Sets the federation station headers from a station directly.
     * Used on {@code /remote/...} responses to identify the serving station.
     */
    public static void setStationHeaders(Context ctx, Station station) {
        ctx.header(HEADER_STATION_NAME, station.name());
        ctx.header(HEADER_STATION_ID, station.uid().toString());
    }
}
