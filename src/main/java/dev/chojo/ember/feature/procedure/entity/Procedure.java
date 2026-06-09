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

public record Procedure(
        int id,
        int stationId,
        Integer templateId,
        String name,
        String description,
        boolean isPublic,
        ProcedureStatus status,
        int assignedBy,
        Instant dueAt,
        Instant createdAt,
        Instant resolvedAt) {

    @MappingProvider("")
    public static RowMapping<Procedure> map() {
        return row -> new Procedure(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("template_id", Integer.class),
                row.getString("name"),
                row.getString("description"),
                row.getBoolean("public"),
                row.getEnum("status", ProcedureStatus.class),
                row.getInt("assigned_by"),
                row.get("due_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("resolved_at", INSTANT_TIMESTAMP));
    }
}
