/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record PageFileFolder(int id, int stationId, Integer parentId, String name, int sortOrder, Instant createdAt) {

    public static RowMapping<PageFileFolder> map() {
        return row -> new PageFileFolder(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("parent_id") != null ? row.getInt("parent_id") : null,
                row.getString("name"),
                row.getInt("sort_order"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
