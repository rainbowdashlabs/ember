/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing registration codes and their group assignments.
 */
@Singleton
public class RegistrationCodeRepository {
    private static final String REGISTRATION_CODE_COLUMNS = "id, station_id, code, max_uses, uses";

    /**
     * Finds a registration code by its code string.
     */
    public Optional<RegistrationCode> findByCode(String code) {
        return query("""
                SELECT %s FROM registration_code WHERE code = :code;""", REGISTRATION_CODE_COLUMNS)
                .single(call().bind("code", code))
                .map(RegistrationCode.map())
                .first();
    }

    /**
     * Finds a registration code by station and code string.
     */
    public Optional<RegistrationCode> findByStationAndCode(int stationId, String code) {
        return query("""
                SELECT %s
                FROM registration_code
                WHERE station_id = :station_id
                  AND code = :code;""", REGISTRATION_CODE_COLUMNS)
                .single(call().bind("station_id", stationId).bind("code", code))
                .map(RegistrationCode.map())
                .first();
    }

    /**
     * Finds a registration code by its identifier.
     */
    public Optional<RegistrationCode> findById(int id) {
        return SqlSupport.findById("registration_code", REGISTRATION_CODE_COLUMNS, id, RegistrationCode.map());
    }

    /**
     * Finds all registration codes for a station.
     */
    public List<RegistrationCode> findByStation(int stationId) {
        return query("""
                SELECT %s FROM registration_code WHERE station_id = :station_id;""", REGISTRATION_CODE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(RegistrationCode.map())
                .all();
    }

    /**
     * Creates a new registration code for a station.
     */
    public RegistrationCode create(int stationId, String code, int maxUses) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO registration_code(station_id, code, max_uses) VALUES(:station_id, :code, :max_uses)
                RETURNING %s;""",
                call().bind("station_id", stationId).bind("code", code).bind("max_uses", maxUses),
                RegistrationCode.map(),
                REGISTRATION_CODE_COLUMNS);
    }

    /**
     * Increments the usage count of a registration code by one.
     */
    public boolean incrementUses(int id) {
        return query("UPDATE registration_code SET uses = uses + 1 WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a registration code.
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("registration_code", id);
    }

    /**
     * Finds all group IDs assigned to a registration code.
     */
    public List<Integer> findGroupIds(int codeId) {
        return query("SELECT group_id FROM registration_code_group WHERE code_id = :code_id;")
                .single(call().bind("code_id", codeId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    /**
     * Assigns a group to a registration code.
     */
    public void addGroup(int codeId, int groupId) {
        query("INSERT INTO registration_code_group(code_id, group_id) VALUES(:code_id, :group_id);")
                .single(call().bind("code_id", codeId).bind("group_id", groupId))
                .insert();
    }

    /**
     * Removes a group assignment from a registration code.
     */
    public boolean removeGroup(int codeId, int groupId) {
        return query("DELETE FROM registration_code_group WHERE code_id = :code_id AND group_id = :group_id;")
                .single(call().bind("code_id", codeId).bind("group_id", groupId))
                .delete()
                .changed();
    }
}
