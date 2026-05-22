/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record KbFolder(
        int id,
        int stationId,
        Integer parentId,
        String name,
        String description,
        String iconUrl,
        int position,
        int createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static RowMapping<KbFolder> map() {
        return row -> new KbFolder(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("parent_id", Integer.class),
                row.getString("name"),
                row.getString("description"),
                row.getString("icon_url"),
                row.getInt("position"),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
