/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.entity;

import de.chojo.sadu.mapper.annotation.MappingProvider;
import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record ProcedureTemplate(
        int id, int stationId, String name, String description, boolean archived, int createdBy, Instant createdAt) {

    @MappingProvider("")
    public static RowMapping<ProcedureTemplate> map() {
        return row -> new ProcedureTemplate(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getBoolean("archived"),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
