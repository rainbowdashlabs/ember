/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record ContentCell(
        int id,
        int rowId,
        int sortOrder,
        double widthPercent,
        CellContentType contentType,
        String content,
        CellConfig config) {

    public static RowMapping<ContentCell> map() {
        return row -> {
            var type = row.getEnum("content_type", CellContentType.class);
            return new ContentCell(
                    row.getInt("id"),
                    row.getInt("row_id"),
                    row.getInt("sort_order"),
                    row.getDouble("width_percent"),
                    type,
                    row.getString("content"),
                    CellConfig.parse(type, row.getString("config")));
        };
    }
}
