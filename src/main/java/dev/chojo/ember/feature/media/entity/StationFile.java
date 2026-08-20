/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One file in a station's media library. Files are deduplicated per station by
 * {@code contentHash}, so the same bytes uploaded twice are one row that several members and
 * several pieces of content point at.
 *
 * @param pageId the page a file was first uploaded for, or {@code 0} when it was brought in
 *               through the station-wide browser rather than from a page
 */
public record StationFile(
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

    public static RowMapping<StationFile> map() {
        return row -> new StationFile(
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
