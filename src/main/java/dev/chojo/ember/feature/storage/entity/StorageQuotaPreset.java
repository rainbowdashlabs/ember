/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A reusable quota profile that can be applied to stations.
 *
 * @param id       the preset identifier
 * @param name     the display name (e.g. "Small", "Standard", "Premium")
 * @param total    total storage quota in bytes
 * @param kb       KB files quota in bytes
 * @param board    board attachments quota in bytes
 * @param images   images quota in bytes
 * @param pages    page images quota in bytes
 * @param perFile  maximum bytes per single file upload
 * @param perImage maximum bytes per single image upload
 */
public record StorageQuotaPreset(
        int id, String name, long total, long kb, long board, long images, long pages, long perFile, long perImage) {
    public static RowMapping<StorageQuotaPreset> map() {
        return row -> new StorageQuotaPreset(
                row.getInt("id"),
                row.getString("name"),
                row.getLong("total"),
                row.getLong("kb"),
                row.getLong("board"),
                row.getLong("images"),
                row.getLong("pages"),
                row.getLong("per_file"),
                row.getLong("per_image"));
    }
}
