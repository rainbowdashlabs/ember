/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record StationPage(
        int id,
        UUID publicUid,
        int stationId,
        Integer parentId,
        String title,
        String slug,
        boolean published,
        int sortOrder,
        String metaDescription,
        Integer ogImageId,
        int createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<PageRow> rows) {

    public static RowMapping<StationPage> mapFlat() {
        return row -> new StationPage(
                row.getInt("id"),
                row.get("public_uid", StandardValueConverter.UUID_STRING),
                row.getInt("station_id"),
                row.getObject("parent_id") != null ? row.getInt("parent_id") : null,
                row.getString("title"),
                row.getString("slug"),
                row.getBoolean("published"),
                row.getInt("sort_order"),
                row.getString("meta_description"),
                row.getObject("og_image_id") != null ? row.getInt("og_image_id") : null,
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                List.of());
    }

    public StationPage withRows(List<PageRow> rows) {
        return new StationPage(
                id,
                publicUid,
                stationId,
                parentId,
                title,
                slug,
                published,
                sortOrder,
                metaDescription,
                ogImageId,
                createdBy,
                createdAt,
                updatedAt,
                rows);
    }
}
