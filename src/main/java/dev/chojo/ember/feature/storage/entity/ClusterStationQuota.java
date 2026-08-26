/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * What a cluster granted one of its stations.
 *
 * <p>Kept apart from the instance's own numbers on the station row on purpose. Two parties writing one column
 * means neither can tell what the other did, and the pool ends up adding up a number nobody handed out. A
 * station with no row of this kind has been granted nothing, which is not the same as having been granted
 * nothing in particular: a null dimension inside a row falls back to the cluster's defaults.
 *
 * @param stationId        the station being granted room
 * @param clusterId        the cluster granting it
 * @param quotaBytes       total room, or {@code null} to fall back to the cluster's default
 * @param quotaKbBytes     room for knowledge base files and the documents filed beside them
 * @param quotaBoardBytes  room for board attachments
 * @param quotaImagesBytes room for images
 * @param quotaPagesBytes  room for page media
 * @param perFileBytes     the largest single file
 * @param perImageBytes    the largest single image
 * @param presetId         the cluster tier the station was put on, kept so a screen can name it
 */
public record ClusterStationQuota(
        int stationId,
        int clusterId,
        Long quotaBytes,
        Long quotaKbBytes,
        Long quotaBoardBytes,
        Long quotaImagesBytes,
        Long quotaPagesBytes,
        Long perFileBytes,
        Long perImageBytes,
        Integer presetId) {

    public static RowMapping<ClusterStationQuota> map() {
        return row -> new ClusterStationQuota(
                row.getInt("station_id"),
                row.getInt("cluster_id"),
                row.getObject("quota_bytes", Long.class),
                row.getObject("quota_kb_bytes", Long.class),
                row.getObject("quota_board_bytes", Long.class),
                row.getObject("quota_images_bytes", Long.class),
                row.getObject("quota_pages_bytes", Long.class),
                row.getObject("per_file_bytes", Long.class),
                row.getObject("per_image_bytes", Long.class),
                row.getObject("preset_id", Integer.class));
    }
}
