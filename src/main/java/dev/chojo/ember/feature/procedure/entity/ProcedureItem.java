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

public record ProcedureItem(
        int id,
        int procedureId,
        String title,
        String description,
        String note,
        boolean isPublic,
        boolean userAssigned,
        int position,
        boolean checked,
        Instant checkedAt,
        Integer checkedBy) {

    @MappingProvider("")
    public static RowMapping<ProcedureItem> map() {
        return row -> new ProcedureItem(
                row.getInt("id"),
                row.getInt("procedure_id"),
                row.getString("title"),
                row.getString("description"),
                row.getString("note"),
                row.getBoolean("public"),
                row.getBoolean("user_assigned"),
                row.getInt("position"),
                row.getBoolean("checked"),
                row.get("checked_at", INSTANT_TIMESTAMP),
                row.getObject("checked_by", Integer.class));
    }
}
