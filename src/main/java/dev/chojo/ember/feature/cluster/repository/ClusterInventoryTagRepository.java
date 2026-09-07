/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.feature.cluster.entity.ClusterInventoryTag;
import dev.chojo.ember.feature.inventory.entity.InventoryTag;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The words an association recommends to its stations.
 *
 * <p>Which stations a word reaches is decided here and nowhere else, the way the association's
 * questions decide it: a word with no group reaches every station under the association, and a word
 * with one reaches the stations in that group.
 */
@Singleton
public class ClusterInventoryTagRepository {
    private static final String CLUSTER_TAG_COLUMNS =
            "id, cluster_id, name, canonical_name, color, position, station_group_id";

    /**
     * Every word an association recommends.
     */
    public List<ClusterInventoryTag> findByCluster(int clusterId) {
        return query("""
                SELECT %s
                FROM cluster_inventory_tag
                WHERE cluster_id = :cluster_id
                ORDER BY position, name;""", CLUSTER_TAG_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterInventoryTag.map())
                .all();
    }

    /**
     * Finds one recommendation by its identifier.
     */
    public Optional<ClusterInventoryTag> findById(int id) {
        return SqlSupport.findById("cluster_inventory_tag", CLUSTER_TAG_COLUMNS, id, ClusterInventoryTag.map());
    }

    /**
     * The words recommended to one station: the association's, narrowed to the groups the station
     * is in.
     *
     * @param stationId the station
     * @return the words, empty when the station answers to no association
     */
    public List<ClusterInventoryTag> findForStation(int stationId) {
        return query("""
                SELECT %s
                FROM cluster_inventory_tag cit
                         JOIN station s ON s.cluster_id = cit.cluster_id
                WHERE s.id = :station_id
                  AND (cit.station_group_id IS NULL
                       OR EXISTS (SELECT 1
                                  FROM cluster_station_group_membership m
                                  WHERE m.group_id = cit.station_group_id
                                    AND m.station_id = s.id))
                ORDER BY cit.position, cit.name;""", SqlSupport.alias("cit", CLUSTER_TAG_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(ClusterInventoryTag.map())
                .all();
    }

    /**
     * Finds the association's recommendation matching a word, compared in its merged form and
     * within the same group of stations, which is what the uniqueness of the row rests on.
     */
    public Optional<ClusterInventoryTag> findByName(int clusterId, Integer stationGroupId, String name) {
        return query("""
                SELECT %s
                FROM cluster_inventory_tag
                WHERE cluster_id = :cluster_id
                  AND canonical_name = :canonical
                  AND station_group_id IS NOT DISTINCT FROM :station_group_id;""", CLUSTER_TAG_COLUMNS)
                .single(call().bind("cluster_id", clusterId)
                        .bind("canonical", InventoryTag.canonical(name))
                        .bind("station_group_id", stationGroupId))
                .map(ClusterInventoryTag.map())
                .first();
    }

    /**
     * Writes a recommendation down, placing it after the association's existing ones.
     */
    public ClusterInventoryTag create(int clusterId, String name, String color, Integer stationGroupId) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO cluster_inventory_tag(cluster_id, name, color, position, station_group_id)
                VALUES (:cluster_id, :name, :color,
                        coalesce((SELECT max(position) + 1 FROM cluster_inventory_tag WHERE cluster_id = :cluster_id), 0),
                        :station_group_id)
                RETURNING %s;""",
                call().bind("cluster_id", clusterId)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("station_group_id", stationGroupId),
                ClusterInventoryTag.map(),
                CLUSTER_TAG_COLUMNS);
    }

    /**
     * Changes a recommendation's word, colour, place and the group of stations it is meant for.
     */
    public boolean update(int id, String name, String color, int position, Integer stationGroupId) {
        return query("""
                UPDATE cluster_inventory_tag
                SET name             = :name,
                    color            = :color,
                    position         = :position,
                    station_group_id = :station_group_id
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("position", position)
                        .bind("station_group_id", stationGroupId))
                .update()
                .changed();
    }

    /**
     * Withdraws a recommendation. The stations that took the word up keep their own row.
     */
    public boolean delete(int id, int clusterId) {
        return query("DELETE FROM cluster_inventory_tag WHERE id = :id AND cluster_id = :cluster_id;")
                .single(call().bind("id", id).bind("cluster_id", clusterId))
                .delete()
                .changed();
    }
}
