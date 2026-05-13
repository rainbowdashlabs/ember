/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.entity.ProfileField;
import dev.chojo.ember.entity.ProfileFieldScope;
import dev.chojo.ember.entity.ProfileFieldValue;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class ProfileFieldRepository {

    private static final String COLUMNS = "id, station_id, name, field_type, config, position, scope, keep_on_archive";

    // -- Field Definitions --

    public Optional<ProfileField> findById(int id) {
        return Query.query("SELECT " + COLUMNS + " FROM profile_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(ProfileField.map())
                .first();
    }

    public List<ProfileField> findByStation(int stationId) {
        return Query.query("SELECT " + COLUMNS
                        + " FROM profile_field WHERE station_id = :station_id ORDER BY scope, position;")
                .single(Call.of().bind("station_id", stationId))
                .map(ProfileField.map())
                .all();
    }

    public List<ProfileField> findByStationAndScope(int stationId, ProfileFieldScope scope) {
        return Query.query("SELECT " + COLUMNS
                        + " FROM profile_field WHERE station_id = :station_id AND scope = :scope ORDER BY position;")
                .single(Call.of().bind("station_id", stationId).bind("scope", scope))
                .map(ProfileField.map())
                .all();
    }

    public ProfileField create(
            int stationId, String name, String fieldType, String config, int position, ProfileFieldScope scope) {
        return Query.query("""
                            INSERT INTO profile_field(station_id, name, field_type, config, position, scope)
                            VALUES (:station_id, :name, :field_type, :config::JSONB, :position, :scope)
                            RETURNING\s""" + COLUMNS + ";")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config)
                        .bind("position", position)
                        .bind("scope", scope))
                .map(ProfileField.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name, String fieldType, String config, int position, boolean keepOnArchive) {
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
                        .bind("config", config)
                        .bind("position", position)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM profile_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Field Values --

    public List<ProfileFieldValue> findValues(int memberId) {
        return Query.query("SELECT member_id, field_id, value FROM profile_field_value WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(ProfileFieldValue.map())
                .all();
    }

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
                              SELECT id FROM profile_field WHERE keep_on_archive = true
                          );""").single(Call.of().bind("member_id", memberId)).delete();
    }
}
