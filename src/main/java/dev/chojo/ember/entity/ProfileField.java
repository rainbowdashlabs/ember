/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record ProfileField(
        int id, int stationId, String name, String fieldType, String config, int position, ProfileFieldScope scope) {
    public static RowMapping<ProfileField> map() {
        return row -> new ProfileField(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("field_type"),
                row.getString("config"),
                row.getInt("position"),
                row.getEnum("scope", ProfileFieldScope.class));
    }
}
