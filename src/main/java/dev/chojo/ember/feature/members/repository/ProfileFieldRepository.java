/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for profile field definitions and their values per member.
 */
@Singleton
public class ProfileFieldRepository {

    private static final String PROFILE_FIELD_COLUMNS =
            "id, station_id, name, field_type, config, position, scope, keep_on_archive";

    /**
     * Finds a profile field definition by its identifier.
     */
    public Optional<ProfileField> findById(int id) {
        return SqlSupport.findById("profile_field", PROFILE_FIELD_COLUMNS, id, ProfileField.map());
    }

    /**
     * Finds all profile field definitions for a station, ordered by scope and position.
     */
    public List<ProfileField> findByStation(int stationId) {
        return query("""
                SELECT %s
                FROM profile_field
                WHERE station_id = :station_id
                ORDER BY scope, position;""", PROFILE_FIELD_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ProfileField.map())
                .all();
    }

    /**
     * Finds profile field definitions for a station filtered by scope, ordered by position.
     */
    public List<ProfileField> findByStationAndScope(int stationId, ProfileFieldScope scope) {
        return query("""
                SELECT %s
                FROM profile_field
                WHERE station_id = :station_id
                  AND scope = :scope
                ORDER BY position;""", PROFILE_FIELD_COLUMNS)
                .single(call().bind("station_id", stationId).bind("scope", scope))
                .map(ProfileField.map())
                .all();
    }

    /**
     * Creates a new profile field definition for a station.
     */
    public ProfileField create(
            int stationId,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            ProfileFieldScope scope) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO profile_field(station_id, name, field_type, config, position, scope)
                VALUES (:station_id, :name, :field_type, :config::JSONB, :position, :scope)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("scope", scope),
                ProfileField.map(),
                PROFILE_FIELD_COLUMNS);
    }

    /**
     * Updates an existing profile field definition.
     */
    public boolean update(
            int id,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            boolean keepOnArchive) {
        return query("""
                UPDATE profile_field
                SET
                    name             = :name,
                    field_type       = :field_type,
                    config           = :config::JSONB,
                    position         = :position,
                    keep_on_archive  = :keep_on_archive
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a profile field definition and all associated values.
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("profile_field", id);
    }

    /**
     * Finds all profile field values for a member.
     */
    public List<ProfileFieldValue> findValues(int memberId) {
        return query("SELECT member_id, field_id, value FROM profile_field_value WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(ProfileFieldValue.map())
                .all();
    }

    /**
     * Finds a specific profile field value for a member.
     */
    public Optional<ProfileFieldValue> findValue(int memberId, int fieldId) {
        return query("""
                SELECT
                    member_id,
                    field_id,
                    value
                FROM
                    profile_field_value
                WHERE member_id = :member_id
                  AND field_id = :field_id;""")
                .single(call().bind("member_id", memberId).bind("field_id", fieldId))
                .map(ProfileFieldValue.map())
                .first();
    }

    /**
     * Sets a profile field value for a member, inserting or updating as needed.
     */
    public void setValue(int memberId, int fieldId, String value) {
        query("""
                INSERT
                INTO
                    profile_field_value(member_id, field_id, value)
                VALUES
                    (:member_id, :field_id, :value::JSONB)
                ON CONFLICT (member_id, field_id) DO UPDATE SET
                    value = excluded.value;""")
                .single(call().bind("member_id", memberId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .insert();
    }

    /**
     * Deletes a specific profile field value for a member.
     */
    public boolean deleteValue(int memberId, int fieldId) {
        return query("DELETE FROM profile_field_value WHERE member_id = :member_id AND field_id = :field_id;")
                .single(call().bind("member_id", memberId).bind("field_id", fieldId))
                .delete()
                .changed();
    }

    /**
     * Delete all field values for a member where the field is NOT marked as keep_on_archive.
     */
    public void deleteNonArchivedValues(int memberId) {
        query("""
                DELETE FROM profile_field_value
                WHERE member_id = :member_id
                  AND field_id NOT IN (
                      SELECT id FROM profile_field WHERE keep_on_archive
                  );""").single(call().bind("member_id", memberId)).delete();
    }
}
