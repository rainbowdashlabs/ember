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
 * A recorded discovery ping nonce. Used both for outbound callback correlation and inbound
 * replay protection.
 */
public record DiscoveryPing(
        String nonce, PingDirection direction, String peerKey, Instant issuedAt, Instant expiresAt) {

    public static RowMapping<DiscoveryPing> map() {
        return row -> new DiscoveryPing(
                row.getString("nonce"),
                row.getEnum("direction", PingDirection.class),
                row.getString("peer_key"),
                row.get("issued_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP));
    }
}
