/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * What a cluster gives a station it has granted nothing of its own.
 *
 * <p>The same seven dimensions the instance configuration carries, one level down. A null dimension means the
 * cluster does not care about that one and whatever stands behind it applies.
 *
 * @param clusterId        the cluster
 * @param quotaBytes       total room, or {@code null} to leave it to the instance
 * @param quotaKbBytes     room for knowledge base files and the documents filed beside them
 * @param quotaBoardBytes  room for board attachments
 * @param quotaImagesBytes room for images
 * @param quotaPagesBytes  room for page media
 * @param perFileBytes     the largest single file
 * @param perImageBytes    the largest single image
 */
public record ClusterQuotaDefaults(
        int clusterId,
        Long quotaBytes,
        Long quotaKbBytes,
        Long quotaBoardBytes,
        Long quotaImagesBytes,
        Long quotaPagesBytes,
        Long perFileBytes,
        Long perImageBytes) {

    /** A cluster that has set none of them, which is every cluster until somebody does. */
    public static ClusterQuotaDefaults none(int clusterId) {
        return new ClusterQuotaDefaults(clusterId, null, null, null, null, null, null, null);
    }

    public static RowMapping<ClusterQuotaDefaults> map() {
        return row -> new ClusterQuotaDefaults(
                row.getInt("id"),
                row.getObject("default_quota_bytes", Long.class),
                row.getObject("default_quota_kb_bytes", Long.class),
                row.getObject("default_quota_board_bytes", Long.class),
                row.getObject("default_quota_images_bytes", Long.class),
                row.getObject("default_quota_pages_bytes", Long.class),
                row.getObject("default_per_file_bytes", Long.class),
                row.getObject("default_per_image_bytes", Long.class));
    }
}
