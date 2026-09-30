/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.MemberGroupSet;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the sets of groups a member can be in only one of.
 */
@Singleton
public class MemberGroupSetRepository {
    private static final String COLUMNS = MemberGroupSet.COLUMNS;

    /**
     * Finds a set by its identifier.
     */
    public Optional<MemberGroupSet> findById(int id) {
        return SqlSupport.findById("member_group_set", COLUMNS, id, MemberGroupSet.map());
    }

    /**
     * Finds all sets of a station, by name.
     */
    public List<MemberGroupSet> findByStation(int stationId) {
        return query("SELECT %s FROM member_group_set WHERE station_id = :station_id ORDER BY name;", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(MemberGroupSet.map())
                .all();
    }

    /**
     * Whether another set of the station already carries this name.
     *
     * @param stationId the station
     * @param name      the name asked for
     * @param exceptId  a set to leave out, the one being renamed, or {@code null}
     * @return {@code true} where the name is taken
     */
    public boolean nameTaken(int stationId, String name, Integer exceptId) {
        return SqlSupport.exists(
                """
                SELECT 1 FROM member_group_set
                WHERE station_id = :station_id AND name = :name AND id IS DISTINCT FROM :except_id;""", call().bind("station_id", stationId).bind("name", name).bind("except_id", exceptId));
    }

    /**
     * Creates a set.
     */
    public MemberGroupSet create(int stationId, String name) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO member_group_set(station_id, name)
                VALUES(:station_id, :name)
                RETURNING %s;""", call().bind("station_id", stationId).bind("name", name), MemberGroupSet.map(), COLUMNS);
    }

    /**
     * Renames a set of the station.
     *
     * @return {@code true} where the set was there to rename
     */
    public boolean rename(int id, int stationId, String name) {
        return query("UPDATE member_group_set SET name = :name WHERE id = :id AND station_id = :station_id;")
                .single(call().bind("name", name).bind("id", id).bind("station_id", stationId))
                .update()
                .changed();
    }

    /**
     * Deletes a set of the station. Its groups stay, in no set.
     *
     * @return {@code true} where the set was there to delete
     */
    public boolean delete(int id, int stationId) {
        return SqlSupport.deleteByIdInStation("member_group_set", id, stationId);
    }
}
