/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A station card another instance publishes, together with the address that instance is known by here.
 *
 * <p>The address comes from the peer record and not from the card, so a link to the station always
 * leads to the instance the card was fetched from, whatever the card itself claims.
 */
public record PublishedRemoteStation(String instancePublicKey, String instanceBaseUrl, DiscoveryStationCard card) {

    public static RowMapping<PublishedRemoteStation> map() {
        return row -> new PublishedRemoteStation(
                row.getString("instance_public_key"),
                row.getString("base_url"),
                DiscoveryStationCard.parse(row.getString("payload")));
    }
}
