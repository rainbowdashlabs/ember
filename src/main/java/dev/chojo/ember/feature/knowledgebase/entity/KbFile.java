/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A file in the knowledge base.
 *
 * <p>{@code contentMode} says how a markdown article was written. A rich one is built from blocks
 * and its stored text is a projection of them, which is what lets search, the PDF export and the
 * version history keep reading the same body they always did. The file type stays
 * {@code MARKDOWN} either way: a rich article is still an article, and a new type would ripple
 * through detection, icons, exportability and every switch on the type for nothing.
 */
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
        ConversionStatus conversionStatus,
        ContentMode contentMode,
        Integer containerId) {

    public static RowMapping<KbFile> map() {
        return row -> new KbFile(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("folder_id", Integer.class),
                row.getString("name"),
                row.getString("description"),
                row.getEnum("file_type", KbFileType.class),
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
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getBoolean("restricted"),
                row.getEnum("conversion_status", ConversionStatus.class),
                row.getEnum("content_mode", ContentMode.class),
                row.getObject("container_id", Integer.class));
    }
}
