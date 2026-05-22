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
        String config,
        int position,
        boolean required) {

    public static RowMapping<WaitingListField> map() {
        return row -> new WaitingListField(
                row.getInt("id"),
                row.getInt("list_id"),
                row.getString("name"),
                WaitingListFieldType.valueOf(row.getString("field_type")),
                row.getString("config"),
                row.getInt("position"),
                row.getBoolean("required"));
    }
}
