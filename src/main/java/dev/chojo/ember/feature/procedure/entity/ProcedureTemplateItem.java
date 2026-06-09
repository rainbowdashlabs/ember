/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.entity;

import de.chojo.sadu.mapper.annotation.MappingProvider;
import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record ProcedureTemplateItem(
        int id,
        int templateId,
        String title,
        String description,
        boolean isPublic,
        boolean userAssigned,
        int position) {

    @MappingProvider("")
    public static RowMapping<ProcedureTemplateItem> map() {
        return row -> new ProcedureTemplateItem(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("title"),
                row.getString("description"),
                row.getBoolean("public"),
                row.getBoolean("user_assigned"),
                row.getInt("position"));
    }
}
