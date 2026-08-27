/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

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
     * Writes the order of a station's fields in one statement.
     *
     * <p>Ordering used to be one update per field, which for a screen holding twenty of them meant twenty
     * round trips for a single drag. The positions all change together, so they are written together.
     *
     * @param stationId the station whose fields these are, so one station cannot reorder another's
     * @param fieldIds  the fields in the order they should stand
     * @return how many were moved
     */
    public int applyOrder(int stationId, List<Integer> fieldIds) {
        if (fieldIds.isEmpty()) return 0;
        return query("""
                UPDATE profile_field AS f
                SET position = ordered.position
                FROM unnest(CAST(:ids AS INTEGER[])) WITH ORDINALITY AS ordered(id, position)
                WHERE f.id = ordered.id
                  AND f.station_id = :station_id;""")
                .single(call().bind("ids", fieldIds, PostgreSqlTypes.INTEGER).bind("station_id", stationId))
                .update()
                .rows();
    }

    /**
     * Every field of one station carrying a given type.
     *
     * <p>More than one is legitimate for the date of birth, which may be asked once of each kind of member,
     * so the question of whether two collide is about who meets them rather than how many there are.
     *
     * @param stationId the station to search
     * @param fieldType the type to look for
     * @return the fields, oldest first
     */
    public List<ProfileField> findAllByStationAndType(int stationId, ProfileFieldType fieldType) {
        return query("""
                SELECT %s
                FROM profile_field
                WHERE station_id = :station_id
                  AND field_type = :field_type
                ORDER BY id;""", PROFILE_FIELD_COLUMNS)
                .single(call().bind("station_id", stationId).bind("field_type", fieldType))
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
     *
     * <p>The answer arrives as a JSON document rather than as text, and that is the point: the column
     * is JSONB, and a bare {@code 0170...} or {@code Müller} handed over as a string is not JSON and
     * ends the whole statement in an error. Taking a node instead leaves no caller a way to write one.
     *
     * @param memberId the member the answer belongs to
     * @param fieldId  the field being answered
     * @param value    the answer, or null to record no answer at all
     */
    public void setValue(int memberId, int fieldId, @Nullable JsonNode value) {
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
                        .bind("value", value == null ? null : value.toString()))
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
