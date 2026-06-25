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
 * Represents another Ember instance known to the local discovery layer.
 * Identified canonically by {@code publicKey}; {@code baseUrl} is observed and may drift over
 * time (we still pin to the key).
 */
public record DiscoveryPeer(
        String publicKey,
        String baseUrl,
        String instanceId,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant lastPingedAt,
        Instant lastReachedAt,
        boolean reachable,
        PeerSource source,
        String introducedBy,
        int reputation,
        boolean blocked) {

    public static RowMapping<DiscoveryPeer> map() {
        return row -> new DiscoveryPeer(
                row.getString("public_key"),
                row.getString("base_url"),
                row.getString("instance_id"),
                row.get("first_seen_at", INSTANT_TIMESTAMP),
                row.get("last_seen_at", INSTANT_TIMESTAMP),
                row.get("last_pinged_at", INSTANT_TIMESTAMP),
                row.get("last_reached_at", INSTANT_TIMESTAMP),
                row.getBoolean("reachable"),
                row.getEnum("source", PeerSource.class),
                row.getString("introduced_by"),
                row.getInt("reputation"),
                row.getBoolean("blocked"));
    }
}
