/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record WaitingListField(
        int id,
        int listId,
        String name,
        WaitingListFieldType fieldType,
        WaitingListFieldConfig config,
        int position,
        boolean required,
        boolean isPublic) {

    public static RowMapping<WaitingListField> map() {
        return row -> new WaitingListField(
                row.getInt("id"),
                row.getInt("list_id"),
                row.getString("name"),
                row.getEnum("field_type", WaitingListFieldType.class),
                WaitingListFieldConfig.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("required"),
                row.getBoolean("public"));
    }
}
