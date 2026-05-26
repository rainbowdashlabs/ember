/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Repository for profile field definitions and their values per member.
 */
@Singleton
public class ProfileFieldRepository {

    private static final String COLUMNS = "id, station_id, name, field_type, config, position, scope, keep_on_archive";

    // -- Field Definitions --

    /**
     * Finds a profile field definition by its identifier.
     */
    public Optional<ProfileField> findById(int id) {
        return Query.query("SELECT " + COLUMNS + " FROM profile_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(ProfileField.map())
                .first();
    }

    /**
     * Finds all profile field definitions for a station, ordered by scope and position.
     */
    public List<ProfileField> findByStation(int stationId) {
        return Query.query("SELECT " + COLUMNS
                        + " FROM profile_field WHERE station_id = :station_id ORDER BY scope, position;")
                .single(Call.of().bind("station_id", stationId))
                .map(ProfileField.map())
                .all();
    }

    /**
     * Finds profile field definitions for a station filtered by scope, ordered by position.
     */
    public List<ProfileField> findByStationAndScope(int stationId, ProfileFieldScope scope) {
        return Query.query("SELECT " + COLUMNS
                        + " FROM profile_field WHERE station_id = :station_id AND scope = :scope ORDER BY position;")
                .single(Call.of().bind("station_id", stationId).bind("scope", scope))
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
        return Query.query("""
                            INSERT INTO profile_field(station_id, name, field_type, config, position, scope)
                            VALUES (:station_id, :name, :field_type, :config::JSONB, :position, :scope)
                            RETURNING\s""" + COLUMNS + ";")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("scope", scope))
                .map(ProfileField.map())
                .first()
                .orElseThrow();
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
        return Query.query("""
                            UPDATE profile_field
                            SET
                                name             = :name,
                                field_type       = :field_type,
                                config           = :config::JSONB,
                                position         = :position,
                                keep_on_archive  = :keep_on_archive
                            WHERE id = :id;""")
                .single(Call.of()
                        .bind("name", name)
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
        return Query.query("DELETE FROM profile_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Field Values --

    /**
     * Finds all profile field values for a member.
     */
    public List<ProfileFieldValue> findValues(int memberId) {
        return Query.query("SELECT member_id, field_id, value FROM profile_field_value WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(ProfileFieldValue.map())
                .all();
    }

    /**
     * Finds a specific profile field value for a member.
     */
    public Optional<ProfileFieldValue> findValue(int memberId, int fieldId) {
        return Query.query("""
                            SELECT
                                member_id,
                                field_id,
                                value
                            FROM
                                profile_field_value
                            WHERE member_id = :member_id
                              AND field_id = :field_id;""")
                .single(Call.of().bind("member_id", memberId).bind("field_id", fieldId))
                .map(ProfileFieldValue.map())
                .first();
    }

    /**
     * Sets a profile field value for a member, inserting or updating as needed.
     */
    public InsertionResult setValue(int memberId, int fieldId, String value) {
        return Query.query("""
                            INSERT
                            INTO
                                profile_field_value(member_id, field_id, value)
                            VALUES
                                (:member_id, :field_id, :value::JSONB)
                            ON CONFLICT (member_id, field_id) DO UPDATE SET
                                value = excluded.value;""")
                .single(Call.of()
                        .bind("member_id", memberId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .insert();
    }

    /**
     * Deletes a specific profile field value for a member.
     */
    public boolean deleteValue(int memberId, int fieldId) {
        return Query.query("DELETE FROM profile_field_value WHERE member_id = :member_id AND field_id = :field_id;")
                .single(Call.of().bind("member_id", memberId).bind("field_id", fieldId))
                .delete()
                .changed();
    }

    /**
     * Delete all field values for a member where the field is NOT marked as keep_on_archive.
     */
    public void deleteNonArchivedValues(int memberId) {
        Query.query("""
                DELETE FROM profile_field_value
                WHERE member_id = :member_id
                  AND field_id NOT IN (
                      SELECT id FROM profile_field WHERE keep_on_archive = TRUE
                  );""").single(Call.of().bind("member_id", memberId)).delete();
    }
}
