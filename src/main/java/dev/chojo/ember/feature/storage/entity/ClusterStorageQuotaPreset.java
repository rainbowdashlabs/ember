/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A reusable set of quotas a cluster hands to its stations.
 *
 * <p>The same shape as the instance's own {@link StorageQuotaPreset}, kept in its own table rather than shared
 * with it: an instance administrator editing a tier must not be able to move a cluster's stations, and a
 * cluster must be able to name tiers the instance never thought of.
 *
 * @param id        the preset
 * @param clusterId the cluster it belongs to
 * @param name      what the tier is called, unique within its cluster
 * @param total     total room in bytes
 * @param kb        room for knowledge base files in bytes
 * @param board     room for board attachments in bytes
 * @param images    room for images in bytes
 * @param pages     room for page media in bytes
 * @param perFile   the largest single file in bytes
 * @param perImage  the largest single image in bytes
 */
public record ClusterStorageQuotaPreset(
        int id,
        int clusterId,
        String name,
        long total,
        long kb,
        long board,
        long images,
        long pages,
        long perFile,
        long perImage) {

    public static RowMapping<ClusterStorageQuotaPreset> map() {
        return row -> new ClusterStorageQuotaPreset(
                row.getInt("id"),
                row.getInt("cluster_id"),
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
