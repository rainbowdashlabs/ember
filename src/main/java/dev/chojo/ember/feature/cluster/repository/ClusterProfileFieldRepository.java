/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The questions a cluster asks, and what its stations' members answered.
 */
@Singleton
public class ClusterProfileFieldRepository {

    private static final String FIELD_COLUMNS =
            "id, cluster_id, name, field_type, config, position, scope, station_readonly, keep_on_archive, "
                    + "station_group_id";

    public List<ClusterProfileField> findByCluster(int clusterId) {
        return query("""
                SELECT %s FROM cluster_profile_field
                WHERE cluster_id = :cluster_id
                ORDER BY scope, position, name;""", FIELD_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterProfileField.map())
                .all();
    }

    public Optional<ClusterProfileField> findById(int id) {
        return SqlSupport.findById("cluster_profile_field", FIELD_COLUMNS, id, ClusterProfileField.map());
    }

    /**
     * The questions that reach one station, which are its cluster's, in the scope asked for.
     *
     * <p>Resolved from the station rather than from the cluster, because the station's own screens do not
     * know or care which cluster they answer to.
     *
     * <p>A question naming a group reaches only the stations filed under it. This is the one place a question
     * reaches a station at all, so the targeting is written here and nowhere else.
     *
     * @param stationId the station
     * @param scope     which kind of member the fields apply to
     * @return the fields, empty when the station answers to no cluster
     */
    public List<ClusterProfileField> findForStation(int stationId, ProfileFieldScope scope) {
        return query("""
                SELECT %s FROM cluster_profile_field cpf
                JOIN station s ON s.cluster_id = cpf.cluster_id
                WHERE s.id = :station_id
                  AND cpf.scope = :scope
                  AND (cpf.station_group_id IS NULL
                       OR EXISTS (SELECT 1
                                  FROM cluster_station_group_membership m
                                  WHERE m.group_id = cpf.station_group_id
                                    AND m.station_id = s.id))
                ORDER BY cpf.position, cpf.name;""", SqlSupport.alias("cpf", FIELD_COLUMNS))
                .single(call().bind("station_id", stationId).bind("scope", scope))
                .map(ClusterProfileField.map())
                .all();
    }

    public ClusterProfileField create(
            int clusterId,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            ProfileFieldScope scope,
            boolean stationReadonly,
            boolean keepOnArchive,
            Integer stationGroupId) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    cluster_profile_field(cluster_id, name, field_type, config, position, scope,
                                          station_readonly, keep_on_archive, station_group_id)
                VALUES
                    (:cluster_id, :name, :field_type, :config::JSONB, :position, :scope,
                     :station_readonly, :keep_on_archive, :station_group_id)
                RETURNING %s;""",
                call().bind("cluster_id", clusterId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("scope", scope)
                        .bind("station_readonly", stationReadonly)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("station_group_id", stationGroupId),
                ClusterProfileField.map(),
                FIELD_COLUMNS);
    }

    public boolean update(
            int id,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            ProfileFieldScope scope,
            boolean stationReadonly,
            boolean keepOnArchive,
            Integer stationGroupId) {
        return query("""
                UPDATE cluster_profile_field
                SET name             = :name,
                    field_type       = :field_type,
                    config           = :config::JSONB,
                    position         = :position,
                    scope            = :scope,
                    station_readonly = :station_readonly,
                    keep_on_archive  = :keep_on_archive,
                    station_group_id = :station_group_id
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("scope", scope)
                        .bind("station_readonly", stationReadonly)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("station_group_id", stationGroupId))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return SqlSupport.deleteById("cluster_profile_field", id);
    }

    // -- Values --

    /**
     * What one member answered to the cluster's questions.
     *
     * @param memberId the station member
     * @return one entry per answered field
     */
    public List<Value> findValues(int memberId) {
        return query("""
                SELECT field_id, value FROM cluster_profile_field_value
                WHERE member_id = :member_id;""")
                .single(call().bind("member_id", memberId))
                .map(row -> new Value(row.getInt("field_id"), row.getString("value")))
                .all();
    }

    public void setValue(int memberId, int fieldId, String value) {
        query("""
                INSERT INTO cluster_profile_field_value(member_id, field_id, value)
                VALUES (:member_id, :field_id, :value::JSONB)
                ON CONFLICT (member_id, field_id) DO UPDATE SET value = EXCLUDED.value;""")
                .single(call().bind("member_id", memberId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .update();
    }

    public boolean deleteValue(int memberId, int fieldId) {
        return query("""
                DELETE FROM cluster_profile_field_value
                WHERE member_id = :member_id AND field_id = :field_id;""")
                .single(call().bind("member_id", memberId).bind("field_id", fieldId))
                .delete()
                .changed();
    }

    /**
     * Clears the cluster's answers for everybody at one station, which is what a release does.
     *
     * <p>Only the answers. The history in {@code profile_field_change} stays, because a record of what was
     * changed and by whom is not the cluster's to take away when it lets a station go.
     *
     * @param stationId the station being released
     * @return how many answers were cleared
     */
    public int deleteValuesOfStation(int stationId) {
        return query("""
                DELETE FROM cluster_profile_field_value cpfv
                USING station_member sm
                WHERE sm.id = cpfv.member_id AND sm.station_id = :station_id;""").single(call().bind("station_id", stationId)).delete().rows();
    }

    /**
     * @param value the answer as stored, which is JSON like a station field's
     */
    public record Value(int fieldId, String value) {}
}
