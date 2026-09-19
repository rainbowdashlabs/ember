/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePreset;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

/** Reads and writes the column selections a station has saved for its tables of people. */
@Singleton
public class MemberTablePresetRepository {

    /**
     * Every selection this station has saved, by name.
     *
     * @param stationId the station
     * @return the saved selections
     */
    public List<MemberTablePreset> findByStation(int stationId) {
        return query("SELECT %s FROM member_table_preset WHERE station_id = :station_id ORDER BY name;"
                        .formatted(MemberTablePreset.COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(MemberTablePreset.map())
                .all();
    }

    /**
     * One saved selection, whoever it belongs to.
     *
     * @param id the selection
     * @return the selection, where there is one
     */
    public Optional<MemberTablePreset> findById(int id) {
        return query("SELECT %s FROM member_table_preset WHERE id = :id;".formatted(MemberTablePreset.COLUMNS))
                .single(call().bind("id", id))
                .map(MemberTablePreset.map())
                .first();
    }

    /**
     * Saves a selection under a name, writing over one the station had saved under that name.
     *
     * <p>Named selections are how a station picks its columns once rather than on every export, so
     * saving the same name twice is somebody changing their mind about that selection rather than
     * asking for a second one.
     *
     * @param stationId the station
     * @param name      what the station calls this selection
     * @param columns   the chosen columns, in the order they are to be printed
     * @return the selection as it now stands
     */
    public MemberTablePreset save(int stationId, String name, List<MemberTableColumn> columns) {
        return insertReturning(
                """
                INSERT INTO member_table_preset(station_id, name, columns)
                VALUES(:station_id, :name, :columns::JSONB)
                ON CONFLICT (station_id, name) DO UPDATE SET columns = EXCLUDED.columns
                RETURNING %s;""".formatted(MemberTablePreset.COLUMNS),
                call().bind("station_id", stationId).bind("name", name).bind("columns", toJson(columns)),
                MemberTablePreset.map(),
                MemberTablePreset.COLUMNS);
    }

    /**
     * Throws one of this station's saved selections away.
     *
     * <p>The station is in the statement rather than checked before it. A handler can forget whose
     * row it is asking about and this one cannot, which is the difference between a guard and a
     * habit.
     *
     * @param id        the selection
     * @param stationId the station it must belong to
     * @return {@code true} where one was thrown away
     */
    public boolean delete(int id, int stationId) {
        return SqlSupport.deleteByIdInStation("member_table_preset", id, stationId);
    }

    private static String toJson(List<MemberTableColumn> columns) {
        return Json.MAPPER.writeValueAsString(columns);
    }
}
