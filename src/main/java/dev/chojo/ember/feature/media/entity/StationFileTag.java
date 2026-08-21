/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A label a station puts on its media files. Unique per station by name.
 */
public record StationFileTag(int id, int stationId, String name, String color) {

    public static RowMapping<StationFileTag> map() {
        return row -> new StationFileTag(
                row.getInt("id"), row.getInt("station_id"), row.getString("name"), row.getString("color"));
    }
}
