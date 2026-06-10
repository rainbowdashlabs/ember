/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record KbFile(
        int id,
        int stationId,
        Integer folderId,
        String name,
        String description,
        KbFileType fileType,
        String mimeType,
        long fileSize,
        String iconUrl,
        String youtubeUrl,
        String linkUrl,
        int position,
        int createdBy,
        Instant createdAt,
        Instant updatedAt,
        Integer sourceFileId,
        Integer sourceStationId,
        RestrictionMode restrictionMode,
        boolean restricted,
        ConversionStatus conversionStatus) {

    public static RowMapping<KbFile> map() {
        return row -> {
            String statusStr = row.getString("conversion_status");
            return new KbFile(
                    row.getInt("id"),
                    row.getInt("station_id"),
                    row.getObject("folder_id", Integer.class),
                    row.getString("name"),
                    row.getString("description"),
                    KbFileType.valueOf(row.getString("file_type")),
                    row.getString("mime_type"),
                    row.getLong("file_size"),
                    row.getString("icon_url"),
                    row.getString("youtube_url"),
                    row.getString("link_url"),
                    row.getInt("position"),
                    row.getInt("created_by"),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("updated_at", INSTANT_TIMESTAMP),
                    row.getObject("source_file_id", Integer.class),
                    row.getObject("source_station_id", Integer.class),
                    RestrictionMode.valueOf(row.getString("restriction_mode")),
                    row.getBoolean("restricted"),
                    statusStr != null ? ConversionStatus.valueOf(statusStr) : null);
        };
    }
}
