/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.feature.cluster.entity.ClusterStationGroup;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * How an association files its stations, and which stations are in which filing.
 */
@Singleton
public class ClusterStationGroupRepository {
    private static final String COLUMNS = "id, cluster_id, name";

    public List<ClusterStationGroup> findByCluster(int clusterId) {
        return query("SELECT %s FROM cluster_station_group WHERE cluster_id = :cluster_id ORDER BY name;", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStationGroup.map())
                .all();
    }

    public Optional<ClusterStationGroup> findById(int id) {
        return query("SELECT %s FROM cluster_station_group WHERE id = :id;", COLUMNS)
                .single(call().bind("id", id))
                .map(ClusterStationGroup.map())
                .first();
    }

    public ClusterStationGroup create(int clusterId, String name) {
        return query(
                        "INSERT INTO cluster_station_group (cluster_id, name) VALUES (:cluster_id, :name) "
                                + "RETURNING %s;",
                        COLUMNS)
                .single(call().bind("cluster_id", clusterId).bind("name", name))
                .map(ClusterStationGroup.map())
                .first()
                .orElseThrow();
    }

    public void rename(int id, String name) {
        query("UPDATE cluster_station_group SET name = :name WHERE id = :id;")
                .single(call().bind("id", id).bind("name", name))
                .update();
    }

    public void delete(int id) {
        query("DELETE FROM cluster_station_group WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    /**
     * @param groupId the group
     * @return the stations filed under it
     */
    public List<Integer> findStationIds(int groupId) {
        return query("""
                SELECT station_id FROM cluster_station_group_membership
                WHERE group_id = :group_id ORDER BY station_id;""")
                .single(call().bind("group_id", groupId))
                .map(row -> row.getInt("station_id"))
                .all();
    }

    /**
     * Replaces what is in one group, which is how the panel saves: it hands back the whole set it drew.
     *
     * @param groupId    the group
     * @param stationIds the stations that are in it afterwards
     */
    public void setStations(int groupId, List<Integer> stationIds) {
        query("DELETE FROM cluster_station_group_membership WHERE group_id = :group_id;")
                .single(call().bind("group_id", groupId))
                .delete();
        for (int stationId : stationIds) {
            query("""
                    INSERT INTO cluster_station_group_membership (group_id, station_id)
                    VALUES (:group_id, :station_id) ON CONFLICT DO NOTHING;""")
                    .single(call().bind("group_id", groupId).bind("station_id", stationId))
                    .insert();
        }
    }

    /**
     * @param stationId the station
     * @return the groups it is filed under
     */
    public List<Integer> findGroupIdsOfStation(int stationId) {
        return query("""
                SELECT group_id FROM cluster_station_group_membership
                WHERE station_id = :station_id ORDER BY group_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    /**
     * Takes a station out of every filing, which is what leaving the association means.
     *
     * @param stationId the station
     */
    public void deleteMembershipsOfStation(int stationId) {
        query("DELETE FROM cluster_station_group_membership WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }

    /**
     * How many questions are pointed at one group, so a refused delete can say what is in the way.
     *
     * @param groupId the group
     * @return the number of questions keyed to it
     */
    public int countFieldsUsing(int groupId) {
        return query("SELECT count(*) AS used FROM cluster_profile_field WHERE station_group_id = :group_id;")
                .single(call().bind("group_id", groupId))
                .map(row -> row.getInt("used"))
                .first()
                .orElse(0);
    }

    /**
     * How many recommended tags are aimed at one group, so a refused delete can say what is in the
     * way.
     *
     * @param groupId the group
     * @return the number of tags keyed to it
     */
    public int countTagsUsing(int groupId) {
        return query("SELECT count(*) AS used FROM cluster_inventory_tag WHERE station_group_id = :group_id;")
                .single(call().bind("group_id", groupId))
                .map(row -> row.getInt("used"))
                .first()
                .orElse(0);
    }

    /**
     * How many stock requirements count at one group, so a refused delete can say what is in the way.
     *
     * @param groupId the group
     * @return the number of requirements keyed to it
     */
    public int countRequirementsUsing(int groupId) {
        return query("SELECT count(*) AS used FROM inventory_requirement WHERE station_group_id = :group_id;")
                .single(call().bind("group_id", groupId))
                .map(row -> row.getInt("used"))
                .first()
                .orElse(0);
    }

    /**
     * Every station a question would reach.
     *
     * <p>The association's whole list when the question names no group, and the group's stations otherwise.
     * This is what the collision rule compares: two questions of one name may never land on one profile.
     *
     * @param clusterId the association
     * @param groupId   the group the question names, or {@code null} for every station
     * @return the stations reached
     */
    public List<Integer> findStationIdsReachedBy(int clusterId, Integer groupId) {
        if (groupId == null) {
            return query("SELECT id FROM station WHERE cluster_id = :cluster_id ORDER BY id;")
                    .single(call().bind("cluster_id", clusterId))
                    .map(row -> row.getInt("id"))
                    .all();
        }
        return query("""
                SELECT m.station_id AS id
                FROM cluster_station_group_membership m
                JOIN station s ON s.id = m.station_id AND s.cluster_id = :cluster_id
                WHERE m.group_id = :group_id
                ORDER BY m.station_id;""")
                .single(call().bind("cluster_id", clusterId).bind("group_id", groupId))
                .map(row -> row.getInt("id"))
                .all();
    }
}
