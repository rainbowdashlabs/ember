/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record Station(int id, String name, String timezone, String locale) {
    public static RowMapping<Station> map() {
        return row -> new Station(
                row.getInt("id"), row.getString("name"), row.getString("timezone"), row.getString("locale"));
    }
}
