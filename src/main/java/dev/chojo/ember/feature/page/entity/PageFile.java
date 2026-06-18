/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record PageFile(
        int id,
        int pageId,
        int stationId,
        String contentHash,
        String fileName,
        String mimeType,
        long fileSize,
        Instant uploadedAt,
        String defaultAltText,
        String defaultDescription,
        Integer folderId) {

    public static RowMapping<PageFile> map() {
        return row -> new PageFile(
                row.getInt("id"),
                row.getInt("page_id"),
                row.getInt("station_id"),
                row.getString("content_hash"),
                row.getString("file_name"),
                row.getString("mime_type"),
                row.getLong("file_size"),
                row.get("uploaded_at", INSTANT_TIMESTAMP),
                row.getString("default_alt_text"),
                row.getString("default_description"),
                row.getObject("folder_id") != null ? row.getInt("folder_id") : null);
    }
}
