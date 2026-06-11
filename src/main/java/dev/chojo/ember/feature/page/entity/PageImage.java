/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record PageImage(int id, int pageId, String fileName, String mimeType, long fileSize, Instant uploadedAt) {

    public static RowMapping<PageImage> map() {
        return row -> new PageImage(
                row.getInt("id"),
                row.getInt("page_id"),
                row.getString("file_name"),
                row.getString("mime_type"),
                row.getLong("file_size"),
                row.get("uploaded_at", INSTANT_TIMESTAMP));
    }
}
