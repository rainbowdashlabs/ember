/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * A named set of columns a station wants whenever it lists people.
 *
 * <p>Saved per station rather than per screen, because who is coming on Friday and who is on the
 * register are the same table asked two questions. A selection may name a field the reader looking at
 * it may not see; that is not a broken selection, and the table trims it when it is drawn.
 *
 * @param columns the chosen columns, in the order they are to be printed
 */
public record MemberTablePreset(int id, int stationId, String name, List<MemberTableColumn> columns) {
    private static final Logger log = LoggerFactory.getLogger(MemberTablePreset.class);
    private static final TypeReference<List<MemberTableColumn>> COLUMN_LIST = new TypeReference<>() {};

    public static final String COLUMNS = "id, station_id, name, columns";

    public static RowMapping<MemberTablePreset> map() {
        return row -> new MemberTablePreset(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                parseColumns(row.getString("columns")));
    }

    private static List<MemberTableColumn> parseColumns(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return Json.MAPPER.readValue(json, COLUMN_LIST).stream()
                    .filter(MemberTableColumn::isWellFormed)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to read the columns of a member table preset: {}", json, e);
            return List.of();
        }
    }
}
