/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.content.entity.ContentRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A station page and its content tree.
 *
 * <p>{@code ogImageHash} is the content hash of the file referenced by {@code ogImageId}. It is not
 * a column on the page - the row mapper leaves it null and the service fills it in, the same way
 * {@code rows} is loaded separately. Clients need it because page files are served by hash, not by
 * id, so an id alone cannot be turned into an image URL.
 *
 * <p>{@code containerId} names the container the page's blocks live in. Every page has one; the
 * container is what makes the same editor usable for something that is not a page.
 *
 * <p>The share token of an unlisted page is deliberately not here. This record travels whole to
 * strangers on the public routes, so a token on it would hand every page's link to anybody who
 * opened that page, and a link that cannot be kept cannot be withdrawn either. It is read on its
 * own, by the one screen that shows it.
 */
public record StationPage(
        int id,
        UUID publicUid,
        int stationId,
        Integer parentId,
        String title,
        String slug,
        PageVisibility visibility,
        int sortOrder,
        String metaDescription,
        Integer ogImageId,
        String ogImageHash,
        Integer containerId,
        int createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<ContentRow> rows) {

    public static RowMapping<StationPage> mapFlat() {
        return row -> new StationPage(
                row.getInt("id"),
                row.get("public_uid", StandardValueConverter.UUID_STRING),
                row.getInt("station_id"),
                row.getObject("parent_id") != null ? row.getInt("parent_id") : null,
                row.getString("title"),
                row.getString("slug"),
                row.getEnum("visibility", PageVisibility.class),
                row.getInt("sort_order"),
                row.getString("meta_description"),
                row.getObject("og_image_id") != null ? row.getInt("og_image_id") : null,
                null,
                row.getObject("container_id") != null ? row.getInt("container_id") : null,
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                List.of());
    }

    public StationPage withRows(List<ContentRow> rows) {
        return new StationPage(
                id,
                publicUid,
                stationId,
                parentId,
                title,
                slug,
                visibility,
                sortOrder,
                metaDescription,
                ogImageId,
                ogImageHash,
                containerId,
                createdBy,
                createdAt,
                updatedAt,
                rows);
    }

    public StationPage withOgImageHash(String ogImageHash) {
        return new StationPage(
                id,
                publicUid,
                stationId,
                parentId,
                title,
                slug,
                visibility,
                sortOrder,
                metaDescription,
                ogImageId,
                ogImageHash,
                containerId,
                createdBy,
                createdAt,
                updatedAt,
                rows);
    }
}
