/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A word an association recommends to the stations under it, shaped like the questions it asks of
 * their members: scoped to the association, aimable at a group of stations, and unique within that.
 *
 * <p>It carries no counterpart to the readonly flag a cluster question has, and that is the whole
 * decision. Imposing a word would mean deleting a station's own row and repointing its things at
 * this one, which leaves nothing sensible to do the day the station leaves the association. It also
 * gains nothing: a station's own row and this one already mean the same word through the canonical
 * name, so searching and recommending treat them as one either way.
 *
 * @param clusterId      the association recommending it
 * @param name           the word as the association spelled it
 * @param canonicalName  the trimmed lowercase name the database maintains
 * @param color          optional hex colour for the badge
 * @param position       where it sits among the association's own words
 * @param stationGroupId the group of stations it is meant for, or {@code null} for all of them
 */
public record ClusterInventoryTag(
        int id, int clusterId, String name, String canonicalName, String color, int position, Integer stationGroupId) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ClusterInventoryTag> map() {
        return row -> new ClusterInventoryTag(
                row.getInt("id"),
                row.getInt("cluster_id"),
                row.getString("name"),
                row.getString("canonical_name"),
                row.getString("color"),
                row.getInt("position"),
                row.getObject("station_group_id", Integer.class));
    }
}
