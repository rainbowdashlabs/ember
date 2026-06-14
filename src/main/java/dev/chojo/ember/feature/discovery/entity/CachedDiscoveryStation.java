/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One station card cached locally, attributed to the peer instance it came from.
 */
public record CachedDiscoveryStation(
        String instancePublicKey, String stationUid, DiscoveryStationCard card, Instant fetchedAt) {

    public static RowMapping<CachedDiscoveryStation> map() {
        return row -> new CachedDiscoveryStation(
                row.getString("instance_public_key"),
                row.getString("station_uid"),
                DiscoveryStationCard.parse(row.getString("payload")),
                row.get("fetched_at", INSTANT_TIMESTAMP));
    }
}
