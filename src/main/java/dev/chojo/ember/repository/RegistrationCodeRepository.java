/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.entity.RegistrationCode;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class RegistrationCodeRepository {

    public Optional<RegistrationCode> findByCode(String code) {
        return query("SELECT id, station_id, code, max_uses, uses FROM registration_code WHERE code = :code;")
                .single(Call.of().bind("code", code))
                .map(RegistrationCode.map())
                .first();
    }

    public Optional<RegistrationCode> findByStationAndCode(int stationId, String code) {
        return query("""
                     SELECT id, station_id, code, max_uses, uses
                     FROM registration_code
                     WHERE station_id = :station_id
                       AND code = :code;""")
                .single(Call.of().bind("station_id", stationId).bind("code", code))
                .map(RegistrationCode.map())
                .first();
    }

    public Optional<RegistrationCode> findById(int id) {
        return query("SELECT id, station_id, code, max_uses, uses FROM registration_code WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(RegistrationCode.map())
                .first();
    }

    public List<RegistrationCode> findByStation(int stationId) {
        return query(
                        "SELECT id, station_id, code, max_uses, uses FROM registration_code WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(RegistrationCode.map())
                .all();
    }

    public RegistrationCode create(int stationId, String code, int maxUses) {
        return query(
                        "INSERT INTO registration_code(station_id, code, max_uses) VALUES(:station_id, :code, :max_uses) RETURNING id, station_id, code, max_uses, uses;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("code", code)
                        .bind("max_uses", maxUses))
                .map(RegistrationCode.map())
                .first()
                .orElseThrow();
    }

    public boolean incrementUses(int id) {
        return query("UPDATE registration_code SET uses = uses + 1 WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return query("DELETE FROM registration_code WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Code-Group assignments --

    public List<Integer> findGroupIds(int codeId) {
        return query("SELECT group_id FROM registration_code_group WHERE code_id = :code_id;")
                .single(Call.of().bind("code_id", codeId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    public InsertionResult addGroup(int codeId, int groupId) {
        return query("INSERT INTO registration_code_group(code_id, group_id) VALUES(:code_id, :group_id);")
                .single(Call.of().bind("code_id", codeId).bind("group_id", groupId))
                .insert();
    }

    public boolean removeGroup(int codeId, int groupId) {
        return query("DELETE FROM registration_code_group WHERE code_id = :code_id AND group_id = :group_id;")
                .single(Call.of().bind("code_id", codeId).bind("group_id", groupId))
                .delete()
                .changed();
    }
}
