/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Per-station quota overrides. NULL values mean "use instance default from config".
 *
 * @param stationId        the station
 * @param quotaBytes       total storage quota override (null = use default)
 * @param quotaKbBytes     KB files quota override (null = use default)
 * @param quotaBoardBytes  board attachments quota override (null = use default)
 * @param quotaImagesBytes images quota override (null = use default)
 * @param quotaPagesBytes  page images quota override (null = use default)
 * @param perFileBytes     max file size override (null = use default)
 * @param perImageBytes    max image size override (null = use default)
 */
public record StationStorageQuota(
        int stationId,
        Long quotaBytes,
        Long quotaKbBytes,
        Long quotaBoardBytes,
        Long quotaImagesBytes,
        Long quotaPagesBytes,
        Long perFileBytes,
        Long perImageBytes) {
    public static RowMapping<StationStorageQuota> map() {
        return row -> new StationStorageQuota(
                row.getInt("id"),
                row.getObject("storage_quota_bytes") != null ? row.getLong("storage_quota_bytes") : null,
                row.getObject("storage_quota_kb_bytes") != null ? row.getLong("storage_quota_kb_bytes") : null,
                row.getObject("storage_quota_board_bytes") != null ? row.getLong("storage_quota_board_bytes") : null,
                row.getObject("storage_quota_images_bytes") != null ? row.getLong("storage_quota_images_bytes") : null,
                row.getObject("storage_quota_pages_bytes") != null ? row.getLong("storage_quota_pages_bytes") : null,
                row.getObject("storage_per_file_bytes") != null ? row.getLong("storage_per_file_bytes") : null,
                row.getObject("storage_per_image_bytes") != null ? row.getLong("storage_per_image_bytes") : null);
    }
}
