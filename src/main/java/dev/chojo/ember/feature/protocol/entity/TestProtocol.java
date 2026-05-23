/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record TestProtocol(
        int id,
        int stationId,
        String name,
        String description,
        Integer passThreshold,
        Instant createdAt,
        Instant updatedAt) {

    public static RowMapping<TestProtocol> map() {
        return row -> new TestProtocol(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getObject("pass_threshold", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
