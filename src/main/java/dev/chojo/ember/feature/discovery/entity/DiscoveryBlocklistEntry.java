/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record DiscoveryBlocklistEntry(String value, BlocklistKind kind, String note, Instant createdAt) {

    public static RowMapping<DiscoveryBlocklistEntry> map() {
        return row -> new DiscoveryBlocklistEntry(
                row.getString("value"),
                BlocklistKind.valueOf(row.getString("kind")),
                row.getString("note"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
